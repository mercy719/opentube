package com.opentube.tv.ui.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.opentube.tv.player.Prefetcher
import com.opentube.tv.ui.components.FilmStrip
import com.opentube.tv.ui.components.Hero
import com.opentube.tv.ui.components.PivotSpec
import com.opentube.tv.ui.components.StatusPanel
import com.opentube.tv.ui.theme.Dims
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    vm: FeedViewModel,
    emptyTitle: String,
    emptyBody: String,
    onOpen: (MediaItem) -> Unit,
    onSignIn: (() -> Unit)? = null,
) {
    val state by vm.state.collectAsState()
    var heroItem by remember { mutableStateOf<MediaItem?>(null) }
    val firstFrame = remember { FocusRequester() }
    var gaveInitialFocus by remember { mutableStateOf(false) }
    val allowAuto = com.opentube.tv.ui.components.LocalAllowAutoFocus.current
    LaunchedEffect(state.strips.isNotEmpty()) {
        if (state.strips.isNotEmpty() && !gaveInitialFocus && allowAuto) { delay(50); gaveInitialFocus = runCatching { firstFrame.requestFocus() }.isSuccess }
    }

    // 焦点停留 180ms 换大图；350ms 起热区（焦点+左右邻居）后台开流缓冲 30 秒；1.5 秒后大图静音预览
    val player = com.opentube.tv.ui.components.LocalPlayer.current
    var zone by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    LaunchedEffect(zone) {
        val f = zone.firstOrNull() ?: return@LaunchedEffect
        player?.stopPreview()
        delay(180); heroItem = f
        delay(170); player?.warm(zone)
        delay(1_150); f.videoId?.let { player?.startPreview(it) }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { player?.stopPreview() } }
    LaunchedEffect(state.strips.isNotEmpty()) {
        if (heroItem == null) heroItem = state.strips.firstOrNull()?.items?.firstOrNull()
    }

    when {
        state.loading && state.strips.isEmpty() -> StatusPanel("正在冲洗……", null, loading = true)
        state.error != null && state.strips.isEmpty() -> StatusPanel("没加载出来", state.error, actionLabel = "重试", onAction = vm::reload)
        state.strips.isEmpty() -> StatusPanel(
            emptyTitle, emptyBody,
            actionLabel = if (!state.signedIn && onSignIn != null) "登录 YouTube 账号" else "刷新",
            onAction = if (!state.signedIn && onSignIn != null) onSignIn else vm::reload,
        )
        else -> Column(Modifier.fillMaxSize()) {
            Hero(heroItem, height = 230.dp, player = player, modifier = Modifier.padding(start = Dims.SafeH, end = Dims.SafeH, top = Dims.SafeV))
            // 获焦的是缩略图，顶到最上沿会把这一行的页眉和红框上边挤到大图下面；留出页眉高度
            val lead = with(androidx.compose.ui.platform.LocalDensity.current) { Dims.StripHeader.toPx() }
            CompositionLocalProvider(LocalBringIntoViewSpec provides remember(lead) { PivotSpec(0f, leadPx = lead) }) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp), // 胶片之间 1px 细缝
                    contentPadding = PaddingValues(bottom = 200.dp),
                ) {
                    items(state.strips, key = { it.id }) { strip ->
                        FilmStrip(
                            firstFrameFocus = if (strip.id == state.strips.first().id) firstFrame else null,
                            title = strip.title,
                            items = strip.items,
                            onZone = { zone = it },
                            onClick = onOpen,
                            onNearEnd = { vm.loadMore(strip) },
                        )
                    }
                }
            }
        }
    }
}
