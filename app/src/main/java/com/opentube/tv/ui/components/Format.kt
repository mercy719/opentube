package com.opentube.tv.ui.components

import com.liskovsoft.mediaserviceinterfaces.data.MediaItem

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

val MediaItem.isPlayable: Boolean get() = !videoId.isNullOrEmpty()

private fun MediaItem.secondParts(): List<String> =
    secondTitle?.toString().orEmpty().split('•', '·').map { it.trim() }.filter { it.isNotEmpty() }

/** 频道名：后端常常不填 author，此时取 secondTitle 的第一段 */
fun MediaItem.channelName(): String =
    (author?.takeIf { it.isNotBlank() } ?: secondTitle?.toString().orEmpty()).split('•', '·').first().trim()

/** secondTitle 形如 “频道 • 观看次数 • 时间”，取频道之外的部分 */
fun MediaItem.metaLine(): String {
    val parts = secondParts()
    val channel = channelName()
    return parts.filterNot { it == channel }.joinToString("  ")
}

/** 能显示成一格的条目：有标题、有图 */
val MediaItem.isShowable: Boolean get() = !title.isNullOrBlank()

/** 卡片缩略图换成更清晰的版本，用于大图预览 */
fun MediaItem.heroImageUrl(): String? {
    val id = videoId ?: return backgroundImageUrl ?: cardImageUrl
    return "https://i.ytimg.com/vi/$id/maxresdefault.jpg"
}

fun MediaItem.cardUrl(): String? = videoId?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" } ?: cardImageUrl
