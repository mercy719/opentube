package com.opentube.tv.ui.player

import android.view.KeyEvent as AKeyEvent
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment
import com.opentube.tv.player.OtPlayer
import com.opentube.tv.player.PlayerState
import com.opentube.tv.player.PlayerState.Phase
import com.opentube.tv.player.Quality
import com.opentube.tv.ui.components.LoadingLine
import com.opentube.tv.ui.components.PillButton
import com.opentube.tv.ui.components.formatTime
import com.opentube.tv.ui.components.channelName
import com.opentube.tv.ui.components.heroImageUrl
import com.opentube.tv.ui.components.cardUrl
import com.opentube.tv.ui.components.greasePencil
import com.opentube.tv.ui.theme.Dims
import com.opentube.tv.ui.theme.Film
import com.opentube.tv.ui.theme.Type
import kotlinx.coroutines.delay

private enum class Panel { None, Quality, Speed }

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun PlayerScreen(player: OtPlayer, item: MediaItem, onExit: () -> Unit) {
    val st by player.state.collectAsState()
    var overlay by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(Panel.None) }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var everPlayed by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(item.videoId) { everPlayed = false; player.open(item.videoId, item) }
    LaunchedEffect(st.phase) { if (st.phase == Phase.Playing) everPlayed = true }
    // 播放中 4 秒无操作自动收起控制层
    LaunchedEffect(overlay, lastInput, st.phase, panel) {
        if (overlay && panel == Panel.None && st.phase == Phase.Playing) { delay(4_000); overlay = false; runCatching { rootFocus.requestFocus() } }
    }
    LaunchedEffect(overlay) { if (overlay) runCatching { playFocus.requestFocus() } else runCatching { rootFocus.requestFocus() } }
    LaunchedEffect(st.lastSkipped) { if (st.lastSkipped != null) { delay(3_500); player.clearSkipNotice() } }
    DisposableEffect(Unit) { onDispose { player.exo.stop() } }

    BackHandler {
        when {
            panel != Panel.None -> panel = Panel.None
            overlay && st.phase == Phase.Playing -> overlay = false
            else -> onExit()
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                lastInput = System.currentTimeMillis()
                val code = ev.key.nativeKeyCode
                if (code == AKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { player.togglePlay(); overlay = true; return@onPreviewKeyEvent true }
                if (!overlay && panel == Panel.None) {
                    when (code) {
                        AKeyEvent.KEYCODE_DPAD_LEFT -> { player.seekBy(-10_000); overlay = true; true }
                        AKeyEvent.KEYCODE_DPAD_RIGHT -> { player.seekBy(10_000); overlay = true; true }
                        AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER -> { player.togglePlay(); overlay = true; true }
                        AKeyEvent.KEYCODE_DPAD_UP, AKeyEvent.KEYCODE_DPAD_DOWN -> { overlay = true; true }
                        else -> false
                    }
                } else false
            }
            .focusRequester(rootFocus).focusable(),
    ) {
        // 画面
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { ctx -> SurfaceView(ctx).also(player::attach) },
                onRelease = { player.detach(it) },
                modifier = Modifier.fillMaxHeight().aspectRatio(st.videoAspect.coerceIn(0.5f, 3f), matchHeightConstraintsFirst = true),
            )
        }

        // 起播前：铺封面 + 标题 + 显影中的琥珀线，绝不黑屏
        AnimatedVisibility(!everPlayed, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                GlideImage(model = item.heroImageUrl(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) {
                    it.thumbnail(it.clone().load(item.cardUrl()))
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Film.Base.copy(alpha = 0.35f), Film.Base.copy(alpha = 0.92f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = Dims.SafeH, vertical = 40.dp)) {
                    Text(item.channelName(), style = Type.Meta)
                    Spacer(Modifier.height(6.dp))
                    Text(item.title.orEmpty(), style = Type.HeroTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(18.dp))
                    if (st.phase == Phase.Error) {
                        Text(st.message ?: "播放失败", style = Type.Body1.copy(color = Film.Grease))
                        Spacer(Modifier.height(14.dp))
                        PillButton("重试", onClick = { player.open(item.videoId, item) }, modifier = Modifier.focusRequester(playFocus))
                    } else {
                        LoadingLine(Modifier.width(220.dp))
                    }
                }
            }
        }

        // 播放中卡顿
        if (everPlayed && st.phase == Phase.Buffering) {
            LoadingLine(Modifier.align(Alignment.Center).width(160.dp))
        }

        // SponsorBlock 跳过提示
        AnimatedVisibility(st.lastSkipped != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopStart).padding(Dims.SafeH, Dims.SafeV)) {
            st.lastSkipped?.let { SkipNotice(it) }
        }

        // 控制层
        AnimatedVisibility(everPlayed && overlay, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomStart)) {
            Controls(
                item = item, st = st,
                playFocus = playFocus,
                onToggle = player::togglePlay,
                onSeek = { player.seekBy(it); lastInput = System.currentTimeMillis() },
                onQuality = { panel = Panel.Quality },
                onSpeed = { panel = Panel.Speed },
            )
        }

        // 侧边面板：清晰度 / 倍速
        AnimatedVisibility(
            panel != Panel.None, enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            when (panel) {
                Panel.Quality -> OptionPanel(
                    title = "清晰度",
                    options = listOf("自动" to (st.quality == Quality.Auto)) + st.availableHeights.map { "${it}p" to (st.quality == Quality.Max(it)) },
                    note = if (st.quality == Quality.Auto && st.currentHeight > 0) "当前 ${st.currentHeight}p，随网速自动调整" else null,
                    onPick = { i -> player.setQuality(if (i == 0) Quality.Auto else Quality.Max(st.availableHeights[i - 1])); panel = Panel.None },
                )
                Panel.Speed -> OptionPanel(
                    title = "播放速度",
                    options = SPEEDS.map { (if (it == 1f) "正常" else "${it}×") to (st.speed == it) },
                    onPick = { i -> player.setSpeed(SPEEDS[i]); panel = Panel.None },
                )
                Panel.None -> {}
            }
        }
    }
}

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@Composable
private fun Controls(
    item: MediaItem, st: PlayerState, playFocus: FocusRequester,
    onToggle: () -> Unit, onSeek: (Long) -> Unit, onQuality: () -> Unit, onSpeed: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Film.Base.copy(alpha = 0.85f), Film.Base)))
            .padding(start = Dims.SafeH, end = Dims.SafeH, top = 60.dp, bottom = Dims.SafeV),
    ) {
        Text(item.channelName(), style = Type.Meta)
        Text(item.title.orEmpty(), style = Type.ScreenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        Timeline(st, onSeek)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val playing = st.phase == Phase.Playing || st.phase == Phase.Buffering
            PillButton(if (playing) "暂停" else "播放", onClick = onToggle, icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, modifier = Modifier.focusRequester(playFocus))
            val q = when (val q = st.quality) { Quality.Auto -> if (st.currentHeight > 0) "自动 · ${st.currentHeight}p" else "自动"; is Quality.Max -> "${q.height}p" }
            PillButton(q, onClick = onQuality, icon = Icons.Outlined.HighQuality, primary = false)
            PillButton(if (st.speed == 1f) "正常速度" else "${st.speed}×", onClick = onSpeed, icon = Icons.Outlined.Speed, primary = false)
            Spacer(Modifier.weight(1f))
            if (st.segments.isNotEmpty()) Text("SponsorBlock 已标记 ${st.segments.size} 段", style = Type.Label)
        }
    }
}

/** 时间线：琥珀色是看过的精确比例；SponsorBlock 片段按分类色画在轨上方，已越过的被红笔打 X */
@Composable
private fun Timeline(st: PlayerState, onSeek: (Long) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key.nativeKeyCode) {
                    AKeyEvent.KEYCODE_DPAD_LEFT -> { onSeek(-10_000); true }
                    AKeyEvent.KEYCODE_DPAD_RIGHT -> { onSeek(10_000); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val dur = st.durationMs.coerceAtLeast(1)
            val y = size.height * 0.62f
            val track = if (focused) 5.dp.toPx() else 3.dp.toPx()
            fun x(ms: Long) = size.width * (ms.toFloat() / dur).coerceIn(0f, 1f)
            drawLine(Film.Seam, Offset(0f, y), Offset(size.width, y), track, StrokeCap.Round)
            drawLine(Film.Edge3, Offset(0f, y), Offset(x(st.bufferedMs), y), track, StrokeCap.Round)
            drawLine(Film.Amber, Offset(0f, y), Offset(x(st.positionMs), y), track, StrokeCap.Round)
            val segY = y - 7.dp.toPx()
            st.segments.forEach { s ->
                val a = x(s.startMs); val b = x(s.endMs)
                drawLine(Film.segmentColor(s.category), Offset(a, segY), Offset(b, segY), 3.dp.toPx(), StrokeCap.Butt)
                if (st.positionMs >= s.endMs) { // 剪掉了：红笔 X
                    val h = 6.dp.toPx(); val cx = (a + b) / 2; val w = ((b - a) / 2).coerceIn(4.dp.toPx(), 9.dp.toPx())
                    drawLine(Film.Grease, Offset(cx - w, segY - h), Offset(cx + w, segY + h), 2.dp.toPx(), StrokeCap.Round)
                    drawLine(Film.Grease, Offset(cx - w, segY + h), Offset(cx + w, segY - h), 2.dp.toPx(), StrokeCap.Round)
                }
            }
            // 播放头
            drawCircle(if (focused) Film.Grease else Film.Edge, radius = if (focused) 7.dp.toPx() else 5.dp.toPx(), center = Offset(x(st.positionMs), y))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(formatTime(st.positionMs), style = Type.Facts.copy(color = Film.Edge))
            Spacer(Modifier.weight(1f))
            if (focused) Text("← → 快退 / 快进 10 秒", style = Type.Label)
            Spacer(Modifier.weight(1f))
            Text("-" + formatTime((st.durationMs - st.positionMs).coerceAtLeast(0)), style = Type.Facts)
        }
    }
}

@Composable
private fun SkipNotice(seg: SponsorSegment) {
    Row(
        Modifier.background(Film.Scrim, RoundedCornerShape(4.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(14.dp)) {
            drawLine(Film.Grease, Offset(0f, 0f), Offset(size.width, size.height), 2.5.dp.toPx(), StrokeCap.Round)
            drawLine(Film.Grease, Offset(0f, size.height), Offset(size.width, 0f), 2.5.dp.toPx(), StrokeCap.Round)
        }
        Spacer(Modifier.width(10.dp))
        Text("已剪掉${categoryName(seg.category)}", style = Type.Body1)
        Spacer(Modifier.width(10.dp))
        Text(formatTime(seg.endMs - seg.startMs), style = Type.Facts)
    }
}

private fun categoryName(c: String) = when (c) {
    "sponsor" -> "赞助片段"; "intro" -> "片头"; "outro" -> "片尾"; "selfpromo" -> "自我推广"
    "interaction" -> "求关注提醒"; "music_offtopic" -> "非音乐部分"; "preview" -> "预告回顾"; else -> "片段"
}

@Composable
private fun OptionPanel(title: String, options: List<Pair<String, Boolean>>, note: String? = null, onPick: (Int) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(
        Modifier.fillMaxHeight().width(260.dp).background(Film.Rebate).padding(horizontal = 24.dp, vertical = Dims.SafeV),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = Type.ScreenTitle)
        if (note != null) Text(note, style = Type.Label)
        Spacer(Modifier.height(10.dp))
        val initial = options.indexOfFirst { it.second }.coerceAtLeast(0)
        options.forEachIndexed { i, (label, selected) ->
            OptionRow(label, selected, onClick = { onPick(i) }, modifier = if (i == initial) Modifier.focusRequester(first) else Modifier)
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.greasePencil(focused, label, outset = 3.dp),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Film.Raised),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(if (selected) Film.Amber else Color.Transparent, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Text(label, style = Type.Body1.copy(color = if (selected || focused) Film.Edge else Film.Edge2))
        }
    }
}
