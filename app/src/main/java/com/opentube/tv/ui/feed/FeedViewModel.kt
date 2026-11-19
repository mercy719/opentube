package com.opentube.tv.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.opentube.tv.data.YouTube
import com.opentube.tv.ui.components.isShowable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 一条胶片行：标题 + 帧 + 用于翻页的最后一个 group */
data class Strip(val id: String, val title: String, val items: List<MediaItem>, val group: MediaGroup?, val loadingMore: Boolean = false)

data class FeedState(
    val strips: List<Strip> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val signedIn: Boolean = false,
)

enum class FeedKind { Home, Subscriptions, History }

/**
 * 首页：按后端分区成行。订阅/历史：一个长列表按 12 帧一条切成多条胶片（接触印样就是一条条剪开的胶片）。
 */
class FeedViewModel(private val kind: FeedKind) : ViewModel() {
    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state
    private var job: Job? = null

    // 订阅/历史：整条长胶片
    private var flatItems = listOf<MediaItem>()
    private var flatGroup: MediaGroup? = null
    private var flatLoadingMore = false

    init { reload() }

    fun reload() {
        job?.cancel()
        _state.value = FeedState(loading = true, signedIn = signedIn())
        job = viewModelScope.launch {
            try {
                when (kind) {
                    FeedKind.Home -> {
                        YouTube.home().collect(::addRows)
                        // 未登录时 YouTube 电视首页经常是空的：退回到热门，保证有东西可看
                        if (_state.value.strips.isEmpty()) runCatching { YouTube.trending().collect(::addRows) }
                    }
                    FeedKind.Subscriptions -> setFlat(YouTube.subscriptions())
                    FeedKind.History -> setFlat(YouTube.history())
                }
                _state.update { it.copy(loading = false) }
            } catch (e: Exception) {
                // 未登录时订阅/历史返回 null（fromNullable result is null）：这是“空”，不是错误
                val empty = e.message?.contains("is null") == true
                _state.update { it.copy(loading = false, error = if (empty) null else (e.message ?: "加载失败")) }
            }
        }
    }

    private fun addRows(batch: List<MediaGroup>) {
        val add = batch.mapNotNull { g -> g.mediaItems.orEmpty().filter { it.isShowable }.takeIf { it.isNotEmpty() }?.let { g to it } }
            .mapIndexed { i, (g, items) -> Strip("${g.title}-${_state.value.strips.size + i}", g.title?.takeIf { it.isNotBlank() } ?: "推荐", items, g) }
        _state.update { it.copy(strips = it.strips + add, loading = false) }
    }

    private fun setFlat(group: MediaGroup?) {
        flatGroup = group
        flatItems = group?.mediaItems.orEmpty().filter { it.isShowable }
        publishFlat()
    }

    private fun publishFlat() {
        val title = if (kind == FeedKind.Subscriptions) "订阅更新" else "观看记录"
        val strips = flatItems.chunked(CHUNK).mapIndexed { i, chunk ->
            val from = i * CHUNK + 1
            Strip("$title-$i", "$title  %02d–%02d".format(from, from + chunk.size - 1), chunk, null)
        }
        _state.update { it.copy(strips = strips, loading = false) }
    }

    /** 某一行快滚到头时加载更多 */
    fun loadMore(strip: Strip) {
        if (kind == FeedKind.Home) {
            val group = strip.group ?: return
            if (strip.loadingMore || group.nextPageKey == null) return
            markLoading(strip.id, true)
            viewModelScope.launch {
                val next = runCatching { YouTube.continueGroup(group) }.getOrNull()
                _state.update { st ->
                    st.copy(strips = st.strips.map {
                        if (it.id != strip.id) it
                        else it.copy(items = it.items + next?.mediaItems.orEmpty().filter { m -> m.isShowable }, group = next ?: it.group, loadingMore = false)
                    })
                }
            }
        } else {
            val group = flatGroup ?: return
            if (flatLoadingMore || group.nextPageKey == null) return
            // 只在最后一条胶片接近末尾时翻页
            if (strip.id != _state.value.strips.lastOrNull()?.id) return
            flatLoadingMore = true
            viewModelScope.launch {
                val next = runCatching { YouTube.continueGroup(group) }.getOrNull()
                if (next != null) { flatGroup = next; flatItems = flatItems + next.mediaItems.orEmpty().filter { it.isShowable }; publishFlat() }
                flatLoadingMore = false
            }
        }
    }

    private fun markLoading(id: String, v: Boolean) =
        _state.update { st -> st.copy(strips = st.strips.map { if (it.id == id) it.copy(loadingMore = v) else it }) }

    private fun signedIn() = runCatching { YouTube.signIn.isSigned }.getOrDefault(false)

    companion object { private const val CHUNK = 12 }
}
