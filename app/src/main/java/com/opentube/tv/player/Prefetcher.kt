package com.opentube.tv.player

import android.os.SystemClock
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import com.opentube.tv.data.YouTube
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * 播放信息预取：遥控器焦点在卡片上停留时就开始解析，按下确认时多半已经就绪。
 *
 * 后端的 getFormatInfo 是全局加锁串行的（每次 0.3～2 秒）。如果每个停留过的卡片都排队去取，
 * 用户真正点开的那个要等前面一串取完。所以这里只有一个工作协程，待办队列是“合并”的：
 * 新请求会顶掉还没开始的旧请求，永远优先取用户当前关心的那个。
 */
object Prefetcher {
    private const val TTL_MS = 20 * 60_000L      // 播放地址数小时才过期，20 分钟足够保守
    private const val MAX_ENTRIES = 12

    private class Entry(val result: CompletableDeferred<MediaItemFormatInfo?>, val createdAt: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cache = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private val queue = Channel<String>(Channel.CONFLATED)

    init {
        scope.launch {
            for (id in queue) {
                val e = cache[id] ?: continue
                if (e.result.isCompleted) continue
                e.result.complete(runCatching { YouTube.formatInfo(id) }.getOrNull())
            }
        }
    }

    /** 开始（或复用）预取，主线程调用 */
    fun prefetch(videoId: String): CompletableDeferred<MediaItemFormatInfo?> {
        val now = SystemClock.elapsedRealtime()
        cache[videoId]?.let { e ->
            val failed = e.result.isCompleted && runCatching { e.result.getCompleted() }.getOrNull() == null
            val fresh = now - e.createdAt < TTL_MS
            if (fresh && !failed) {
                // 还没取到：可能被更新的请求顶掉了，重新排到队首
                if (!e.result.isCompleted) queue.trySend(videoId)
                return e.result
            }
        }
        val entry = Entry(CompletableDeferred(), now)
        cache[videoId] = entry
        while (cache.size > MAX_ENTRIES) cache.remove(cache.keys.first())
        queue.trySend(videoId)
        return entry.result
    }

    suspend fun get(videoId: String): MediaItemFormatInfo? = prefetch(videoId).await()

    fun isReady(videoId: String): Boolean = cache[videoId]?.result?.isCompleted == true

    fun invalidate(videoId: String) { cache.remove(videoId) }
}
