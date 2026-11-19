package com.opentube.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.opentube.tv.ui.theme.Dims
import com.opentube.tv.ui.theme.Film
import com.opentube.tv.ui.theme.Type

/**
 * 一条胶片：片边印字（行名 + 帧范围 + 当前帧）+ 一排同尺寸、同基线的帧。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilmStrip(
    title: String,
    items: List<MediaItem>,
    modifier: Modifier = Modifier,
    onFocused: (MediaItem) -> Unit = {},
    /** 焦点变化时报告热区：[焦点, 右邻, 左邻] */
    onZone: (List<MediaItem>) -> Unit = {},
    onClick: (MediaItem) -> Unit,
    onNearEnd: () -> Unit = {},
    firstFrameFocus: androidx.compose.ui.focus.FocusRequester? = null,
) {
    var focusedIndex by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()
    val nearEnd by remember(items.size) { derivedStateOf { focusedIndex >= items.size - 5 } }
    LaunchedEffect(nearEnd) { if (nearEnd && items.isNotEmpty()) onNearEnd() }

    Column(modifier.fillMaxWidth().background(Film.Rebate)) {
        Row(
            Modifier.padding(start = Dims.SafeH, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = Type.RowTitle)
            Spacer(Modifier.width(12.dp))
            Text("01 → %02d".format(items.size), style = Type.EdgePrint)
            if (focusedIndex >= 0) {
                Spacer(Modifier.width(12.dp))
                Text("▸ %02d".format(focusedIndex + 1), style = Type.EdgePrint.copy(color = Film.Grease))
            }
        }
        val margin = with(androidx.compose.ui.platform.LocalDensity.current) { Dims.SafeH.toPx() }
        CompositionLocalProvider(LocalBringIntoViewSpec provides remember(margin) { PivotSpec(marginPx = margin) }) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(start = Dims.SafeH, end = Dims.SafeH, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(Dims.FrameGap),
            ) {
                itemsIndexed(items, key = { i, it -> "${it.videoId ?: it.channelId ?: it.playlistId}-$i" }) { i, item ->
                    Frame(
                        item = item,
                        index = i,
                        modifier = if (i == 0 && firstFrameFocus != null) Modifier.focusRequester(firstFrameFocus) else Modifier,
                        onFocused = { focusedIndex = i; onFocused(item); onZone(listOfNotNull(item, items.getOrNull(i + 1), items.getOrNull(i - 1))) },
                        onClick = { onClick(item) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun Frame(item: MediaItem, index: Int, onFocused: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(Dims.FrameWidth)) {
        Surface(
            onClick = onClick,
            modifier = modifier
                .size(Dims.FrameWidth, Dims.FrameHeight)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
                .greasePencil(focused, item.videoId ?: item.title ?: index),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(2.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            colors = ClickableSurfaceDefaults.colors(containerColor = Film.Seam, focusedContainerColor = Film.Seam),
            glow = ClickableSurfaceDefaults.glow(),
        ) {
            Box(Modifier.fillMaxSize()) {
                GlideImage(
                    model = item.cardUrl(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (item.durationMs > 0 || item.isLive) {
                    Text(
                        if (item.isLive) "直播" else formatTime(item.durationMs),
                        style = Type.Duration.copy(color = if (item.isLive) Film.Grease else Film.Edge),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
                            .background(Film.Base.copy(alpha = 0.8f), RoundedCornerShape(2.dp)).padding(horizontal = 4.dp),
                    )
                }
                val pct = item.percentWatched
                if (pct in 1..100) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(pct / 100f).height(3.dp).background(Film.Amber))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%02d".format(index + 1),
                style = Type.FrameNo.copy(color = if (focused) Film.Grease else Film.Edge3),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                item.channelName(),
                style = Type.Label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
        Text(
            item.title.orEmpty(),
            style = Type.CardTitle.copy(color = if (focused) Film.Edge else Film.Edge2),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp).height(36.dp),
        )
    }
}
