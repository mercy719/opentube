package com.opentube.tv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.viewinterop.AndroidView
import android.view.TextureView
import com.opentube.tv.player.OtPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.opentube.tv.ui.theme.Film
import com.opentube.tv.ui.theme.Type

/**
 * 上半屏：焦点那一格的放大印样 + 片名与事实。
 * 焦点停留一会儿后，热区播放器在这里静音播放预览：视频画在封面下面的 TextureView 上，出画面后封面淡出。
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun Hero(item: MediaItem?, height: Dp, modifier: Modifier = Modifier, player: OtPlayer? = null) {
    val previewId = player?.previewId?.collectAsState()?.value
    val previewing = previewId != null && previewId == item?.videoId
    val posterAlpha by animateFloatAsState(if (previewing) 0f else 1f, tween(if (previewing) 450 else 150), label = "poster")
    Row(modifier.height(height), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Box(Modifier.fillMaxHeight().aspectRatio(16f / 9f).clip(RoundedCornerShape(3.dp)).background(Film.Base)) {
            if (player != null) AndroidView(
                factory = { ctx -> TextureView(ctx).also(player::attachPreview) },
                onRelease = { player.detachPreview(it) },
                modifier = Modifier.fillMaxSize(),
            )
            Crossfade(item?.videoId, animationSpec = tween(220), label = "hero", modifier = Modifier.alpha(posterAlpha)) { id ->
                if (id != null) GlideImage(
                    model = item?.heroImageUrl(), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(Film.Rebate),
                ) { it.thumbnail(it.clone().load(item?.cardUrl())) }
            }
            if (previewing) Text(
                "预览 · 静音", style = Type.Label.copy(color = Film.Edge),
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    .background(Film.Base.copy(alpha = 0.7f), RoundedCornerShape(2.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (item != null) Column(Modifier.padding(top = 10.dp).weight(1f)) {
            Text(item.channelName(), style = Type.Meta.copy(fontSize = Type.Body1.fontSize), maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Text(item.title.orEmpty(), style = Type.HeroTitle, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.isLive) Text("直播中", style = Type.Facts.copy(color = Film.Grease))
                else if (item.durationMs > 0) Text(formatTime(item.durationMs), style = Type.Facts)
                val meta = item.metaLine()
                if (meta.isNotEmpty()) Text(meta, style = Type.Meta.copy(fontSize = Type.Body1.fontSize), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (item.percentWatched in 1..99) Text("已看 ${item.percentWatched}%", style = Type.Facts.copy(color = Film.Amber))
            }
            Spacer(Modifier.height(22.dp))
            // 提示按确认键会发生什么（不是按钮，焦点留在胶片上）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).background(Film.Edge, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Film.Base, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                val resume = if (item.percentWatched in 3..94 && item.durationMs > 0) "  ·  从 ${formatTime(item.durationMs * item.percentWatched / 100)} 继续" else ""
                Text(if (item.videoId != null) "按确认播放$resume" else "按确认打开", style = Type.Body1.copy(color = Film.Edge2))
            }
        }
    }
}
