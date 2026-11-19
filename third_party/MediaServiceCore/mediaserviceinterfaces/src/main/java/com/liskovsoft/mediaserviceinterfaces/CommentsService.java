package com.liskovsoft.mediaserviceinterfaces;

import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import io.reactivex.Observable;

public interface CommentsService {
    Observable<CommentGroup> getCommentsObserve(String key);
    Observable<Void> toggleLikeObserve(String key);
    Observable<Void> toggleDislikeObserve(String key);
    /** OPENTUBE: 直接用评论自带的点赞参数，任何评论（包括没有回复的）都能点赞/取消 */
    Observable<Void> setLikeObserve(CommentItem item, boolean like);
}
