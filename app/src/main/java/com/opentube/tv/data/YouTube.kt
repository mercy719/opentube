package com.opentube.tv.data

import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment
import com.liskovsoft.sharedutils.rx.RxHelper
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import io.reactivex.Observable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.rx2.awaitFirstOrNull

/**
 * SmartTube 后端的协程封装。所有 *Observe() 本身已在后台线程执行、主线程回调。
 */
object YouTube {
    private val service get() = YouTubeServiceManager.instance()
    private val content get() = service.contentService
    private val items get() = service.mediaItemService
    val signIn get() = service.signInService

    /** 首页会分多批返回行，逐批发出 */
    fun home(): Flow<List<MediaGroup>> = content.homeObserve.asFlow()

    fun trending(): Flow<List<MediaGroup>> = content.trendingObserve.asFlow()

    suspend fun subscriptions(): MediaGroup? = content.subscriptionsObserve.awaitFirstOrNull()
    suspend fun history(): MediaGroup? = content.historyObserve.awaitFirstOrNull()
    suspend fun search(query: String): List<MediaGroup> = content.getSearchObserve(query).awaitFirstOrNull().orEmpty()
    suspend fun searchSuggestions(query: String): List<String> = content.getSearchTagsObserve(query).awaitFirstOrNull().orEmpty()
    suspend fun continueGroup(group: MediaGroup): MediaGroup? = content.continueGroupObserve(group).awaitFirstOrNull()
    suspend fun channel(item: MediaItem): List<MediaGroup> = content.getChannelObserve(item).awaitFirstOrNull().orEmpty()
    suspend fun group(item: MediaItem): MediaGroup? = content.getGroupObserve(item).awaitFirstOrNull()

    suspend fun formatInfo(videoId: String): MediaItemFormatInfo? {
        service.allowRestrictedVideos(true)
        return items.getFormatInfoObserve(videoId).awaitFirstOrNull()
    }

    suspend fun metadata(videoId: String): MediaItemMetadata? = items.getMetadataObserve(videoId).awaitFirstOrNull()

    /** 评论分页：首页 key 来自 metadata.commentsKey；下一页用 nextCommentsKey；回复用 nestedCommentsKey */
    suspend fun comments(key: String): CommentGroup? = service.commentsService.getCommentsObserve(key).awaitFirstOrNull()

    /** 给评论点赞/取消（需要已登录） */
    suspend fun likeComment(item: CommentItem, like: Boolean) { service.commentsService.setLikeObserve(item, like).awaitFirstOrNull() }

    suspend fun sponsorSegments(videoId: String, categories: Set<String>): List<SponsorSegment> =
        runCatching { items.getSponsorSegmentsObserve(videoId, categories).awaitFirstOrNull().orEmpty() }.getOrDefault(emptyList())

    /** 上报观看进度。0 表示“已看完”，所以最少报 3 秒 */
    fun reportPosition(item: MediaItem?, videoId: String, positionMs: Long) {
        val sec = maxOf(positionMs, 3_000L) / 1000f
        val obs: Observable<Void> = if (item != null) items.updateHistoryPositionObserve(item, sec)
        else items.updateHistoryPositionObserve(videoId, sec)
        RxHelper.execute(obs)
    }

    fun reloadPlayerResponse(videoId: String, token: String?) = service.reloadPlayerResponse(videoId, token)
    fun switchNextClient() = service.switchNextClient()
}
