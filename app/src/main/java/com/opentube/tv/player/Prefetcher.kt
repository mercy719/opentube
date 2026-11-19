package com.opentube.tv.player

import android.os.SystemClock
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import com.opentube.tv.data.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * 播放信息预取：遥控器焦点在卡片上停留时就开始解析，按下确认时多半已经就绪。
 * 这是解决 SmartTube “点进去黑屏十几秒” 的第一道措施。
 */
object Prefetcher {
    private const val TTL_MS = 20 * 60_000L      // 播放地址数小时才过期，20 分钟足够保守
    private const val MAX_ENTRIES = 12

    private class Entry(val job: Deferred<MediaItemFormatInfo?>, val createdAt: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cache = LinkedHashMap<String, Entry>(16, 0.75f, true)

    /** 开始（或复用）预取，主线程调用 */
    fun prefetch(videoId: String): Deferred<MediaItemFormatInfo?> {
        val now = SystemClock.elapsedRealtime()
        cache[videoId]?.let { e ->
            val failed = e.job.isCompleted && runCatching { e.job.getCompleted() }.getOrNull() == null
            if (now - e.createdAt < TTL_MS && !failed) return e.job
        }
        val job = scope.async { runCatching { YouTube.formatInfo(videoId) }.getOrNull() }
        cache[videoId] = Entry(job, now)
        while (cache.size > MAX_ENTRIES) cache.remove(cache.keys.first())
        return job
    }

    suspend fun get(videoId: String): MediaItemFormatInfo? = prefetch(videoId).await()

    fun invalidate(videoId: String) { cache.remove(videoId) }
}
