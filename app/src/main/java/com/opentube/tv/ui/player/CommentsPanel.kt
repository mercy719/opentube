package com.opentube.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem
import com.opentube.tv.data.YouTube
import com.opentube.tv.ui.components.LoadingLine
import com.opentube.tv.ui.components.greasePencil
import com.opentube.tv.ui.theme.Dims
import com.opentube.tv.ui.theme.Film
import com.opentube.tv.ui.theme.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

val CommentsWidth: Dp = 340.dp

/** 一层评论列表（顶层或某条评论的回复），自带分页 */
@Stable
class CommentList(private val scope: CoroutineScope, private val firstKey: suspend () -> String?) {
    var items by mutableStateOf<List<CommentItem>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var failed by mutableStateOf<String?>(null); private set
    var started = false; private set
    private var nextKey: String? = null
    private var done = false
    private var job: Job? = null

    fun start() { if (!started) { started = true; load(first = true) } }

    fun retry() { failed = null; load(first = items.isEmpty()) }

    fun loadMore() { if (!done && !loading && failed == null && started) load(first = false) }

    private fun load(first: Boolean) {
        if (job?.isActive == true) return
        loading = true
        job = scope.launch {
            runCatching {
                val key = if (first) firstKey() else nextKey
                if (key.isNullOrEmpty()) { done = true; return@runCatching }
                val g = YouTube.comments(key)
                                val got = g?.comments.orEmpty().filter { !it.message.isNullOrBlank() } // 注意：后端 isEmpty() 表示“没有回复”，不能用来过滤
                val seen = items.mapNotNullTo(HashSet()) { it.id }
                items = items + got.filter { it.id == null || it.id !in seen }
                nextKey = g?.nextCommentsKey
                if (nextKey.isNullOrEmpty() || got.isEmpty()) done = true
            }.onFailure {
                // 没有评论区（关闭评论/直播）时后端常抛空结果，按“空”处理
                if (it.message?.contains("null", ignoreCase = true) == true) done = true
                else failed = "评论加载失败"
            }
            loading = false
        }
    }
}

/** 整个评论栏的状态：顶层列表 + 可能打开着的一层回复 */
@Stable
class CommentsState(private val scope: CoroutineScope, videoId: String) {
    val top = CommentList(scope) { YouTube.metadata(videoId)?.commentsKey }
    var thread by mutableStateOf<Pair<CommentItem, CommentList>?>(null); private set
    var parentIndex by mutableIntStateOf(0); private set

    fun openThread(index: Int, c: CommentItem) {
        if (c.isEmpty) return
        val key = c.nestedCommentsKey ?: return
        parentIndex = index
        thread = c to CommentList(scope) { key }.also { it.start() }
    }

    fun closeThread() { thread = null }
}

@Composable
fun CommentsPanel(state: CommentsState) {
    LaunchedEffect(state) { state.top.start() }
    val thread = state.thread

    Column(
        Modifier.fillMaxHeight().width(CommentsWidth).background(Film.Rebate)
            // 左右键不让焦点跑出评论栏（不用 focusGroup exit，否则会吞掉返回键）
            .onPreviewKeyEvent { ev ->
                ev.type == KeyEventType.KeyDown &&
                    (ev.key.nativeKeyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT || ev.key.nativeKeyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
            },
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = Dims.SafeV, bottom = 10.dp)) {
            if (thread == null) {
                Text("评论", style = Type.ScreenTitle)
                Text("↑ ↓ 浏览 · 确认看回复 · 返回收起", style = Type.Label)
            } else {
                Text("回复", style = Type.ScreenTitle)
                Text("${thread.first.authorName.orEmpty()} 的评论 · 返回上一层", style = Type.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Film.Seam))
        if (thread == null) {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.parentIndex)
            CommentColumn(state.top, listState, focusIndex = state.parentIndex, header = null) { i, c -> state.openThread(i, c) }
        } else {
            CommentColumn(thread.second, rememberLazyListState(), focusIndex = 0, header = thread.first, onOpen = null)
        }
    }
}

@Composable
private fun CommentColumn(
    list: CommentList, listState: LazyListState, focusIndex: Int, header: CommentItem?,
    onOpen: ((Int, CommentItem) -> Unit)?,
) {
    val target = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }
    val rows = (if (header != null) listOf(header) else emptyList()) + list.items
    val wantIndex = focusIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))

    LaunchedEffect(rows.isNotEmpty()) {
        if (rows.isNotEmpty() && !focusedOnce) { focusedOnce = true; runCatching { target.requestFocus() } }
    }

    when {
        rows.isEmpty() && list.loading -> Box(Modifier.fillMaxWidth().padding(24.dp)) { LoadingLine(Modifier.width(160.dp)) }
        rows.isEmpty() && list.failed != null -> Status(list.failed!!, retry = list::retry)
        rows.isEmpty() -> Status("这个视频没有评论", retry = null)
        else -> LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(rows, key = { i, c -> (c.id ?: "i") + "#" + i }) { i, c ->
                val isHeader = header != null && i == 0
                val realIndex = if (header != null) i - 1 else i
                CommentRow(
                    c = c,
                    indent = header != null && !isHeader,
                    canOpen = onOpen != null && !c.isEmpty,
                    onClick = { if (onOpen != null) onOpen(realIndex, c) },
                    modifier = (if (i == wantIndex) Modifier.focusRequester(target) else Modifier)
                        .onFocusChanged { if (it.isFocused && i >= rows.size - 4) list.loadMore() },
                )
            }
            if (list.loading) item { Box(Modifier.padding(14.dp)) { LoadingLine(Modifier.width(120.dp)) } }
            if (list.failed != null) item { Status(list.failed!!, retry = list::retry) }
        }
    }
}

@Composable
private fun Status(text: String, retry: (() -> Unit)?) {
    Column(Modifier.padding(24.dp)) {
        Text(text, style = Type.Body1.copy(color = Film.Edge2))
        if (retry != null) {
            Spacer(Modifier.height(12.dp))
            com.opentube.tv.ui.components.PillButton("重试", onClick = retry, primary = false)
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun CommentRow(c: CommentItem, indent: Boolean, canOpen: Boolean, onClick: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(start = if (indent) 18.dp else 0.dp)
            .onFocusChanged { focused = it.isFocused }
            .greasePencil(focused, c.id ?: c.message.orEmpty(), outset = 2.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Film.Raised),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            GlideImage(
                model = c.authorPhoto, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(28.dp).clip(CircleShape).background(Film.Seam),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.authorName.orEmpty(), style = Type.Label.copy(color = if (focused) Film.Edge else Film.Edge2),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    c.publishedDate?.takeIf { it.isNotBlank() }?.let {
                        Text("  ·  $it", style = Type.Label.copy(color = Film.Edge3), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    c.message.orEmpty(),
                    style = Type.Body1.copy(color = if (focused) Film.Edge else Film.Edge2),
                    maxLines = if (focused) 18 else 4, overflow = TextOverflow.Ellipsis,
                )
                val likes = c.likeCount?.takeIf { it.isNotBlank() && it != "0" }
                // 后端有时给纯数字，有时已带“条回复”字样
                val replies = c.replyCount?.trim()?.takeIf { it.isNotBlank() && it != "0" }
                    ?.let { if (Regex("^[\\d.,]+\\s*[万千亿KMk]?$").matches(it)) "$it 条回复" else it }
                if (likes != null || replies != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (likes != null) Text("赞 $likes", style = Type.Label.copy(color = Film.Edge3))
                        if (replies != null) Text(
                            if (canOpen && focused) "$replies · 按确认查看" else replies,
                            style = Type.Label.copy(color = if (canOpen && focused) Film.Amber else Film.Edge3),
                        )
                    }
                }
            }
        }
    }
}
