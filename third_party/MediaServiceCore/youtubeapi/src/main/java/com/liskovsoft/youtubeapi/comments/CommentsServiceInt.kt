package com.liskovsoft.youtubeapi.comments

import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup
import com.liskovsoft.youtubeapi.comments.gen.getDislikeParams
import com.liskovsoft.youtubeapi.comments.gen.getLikeParams
import com.liskovsoft.youtubeapi.comments.gen.getActiveCommentItem
import com.liskovsoft.youtubeapi.comments.gen.getUnLikeParams
import com.liskovsoft.youtubeapi.comments.impl.CommentGroupImpl
import com.liskovsoft.googlecommon.common.helpers.RetrofitHelper

internal object CommentsServiceInt {
    private val mApi = RetrofitHelper.create(CommentsApi::class.java)

    fun getComments(key: String): CommentGroup? {
        val commentsResult = getCommentsResult(key)
        return commentsResult?.let { CommentGroupImpl(it) }
    }

    fun toggleLike(key: String) {
        val commentsResult = getCommentsResult(key)
        val activeCommentItem = commentsResult?.getActiveCommentItem()
        val likeParam = activeCommentItem?.let { if (it.isLiked == true) it.getUnLikeParams() else it.getLikeParams() }
        likeParam?.let { getActionResult(it) }
    }

    fun toggleDislike(key: String) {
        val commentsResult = getCommentsResult(key)
        val likeParam = commentsResult?.getActiveCommentItem()?.getDislikeParams()
        likeParam?.let { getActionResult(it) }
    }

    /** OPENTUBE: 用评论自带的 like/unlike 参数直接操作，不再借道回复列表 */
    fun setLike(item: com.liskovsoft.youtubeapi.comments.impl.CommentItemImpl, like: Boolean) {
        val w = item.commentItemWrapper
        val renderer = w.commentThreadRenderer?.comment?.commentRenderer ?: w.commentRenderer
            ?: throw IllegalStateException("no comment renderer")
        val param = (if (like) renderer.getLikeParams() else renderer.getUnLikeParams())
            ?: throw IllegalStateException("no like params")
        getActionResult(param) ?: throw IllegalStateException("like action failed")
    }

    private fun getCommentsResult(commentsKey: String) = RetrofitHelper.get(mApi.getComments(CommentsApiParams.getCommentsQuery(commentsKey)))

    private fun getActionResult(actionKey: String) = RetrofitHelper.get(mApi.commentAction(CommentsApiParams.getActionQuery(actionKey)))
}