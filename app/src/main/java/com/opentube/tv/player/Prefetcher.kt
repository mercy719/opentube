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
 * 播放信息预取。
 *
 * 后端的 getFormatInfo 是全局加锁串行的（每次 0.3～2 秒），所以这里只有一个工作协程，
 * 待办是一个很短的双端队列：焦点视频插到队首，热区邻居排在后面，超出长度的旧请求直接丢弃。
 * 被丢弃的请求如果之后又被需要，[prefetch] 会把它重新排进来。
 * 所有方法都在主线程调用。
 */
object Prefetcher {
    private const val TTL_MS = 20 * 60_000L      // 播放地址数小时才过期，20 分钟足够保守
    private const val MAX_ENTRIES = 16
    private const val MAX_PENDING = 4

    private class Entry(val result: CompletableDeferred<MediaItemFormatInfo?>, val createdAt: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cache = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private val pending = ArrayDeque<String>()
    private val signal = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (s in signal) {
                while (true) {
                    val id = pending.removeFirstOrNull() ?: break
                    val e = cache[id] ?: continue
                    if (e.result.isCompleted) continue
                    e.result.complete(runCatching { YouTube.formatInfo(id) }.getOrNull())
                }
            }
        }
    }

    /** 开始（或复用）预取。urgent = 插队到最前（当前焦点 / 用户已点开） */
    fun prefetch(videoId: String, urgent: Boolean = false): CompletableDeferred<MediaItemFormatInfo?> {
        val now = SystemClock.elapsedRealtime()
        cache[videoId]?.let { e ->
            val failed = e.result.isCompleted && runCatching { e.result.getCompleted() }.getOrNull() == null
            if (now - e.createdAt < TTL_MS && !failed) {
                if (!e.result.isCompleted) enqueue(videoId, urgent)
                return e.result
            }
        }
        val entry = Entry(CompletableDeferred(), now)
        cache[videoId] = entry
        while (cache.size > MAX_ENTRIES) cache.remove(cache.keys.first())
        enqueue(videoId, urgent)
        return entry.result
    }

    private fun enqueue(id: String, urgent: Boolean) {
        pending.remove(id)
        if (urgent) pending.addFirst(id) else pending.addLast(id)
        while (pending.size > MAX_PENDING) pending.removeLast()
        signal.trySend(Unit)
    }

    suspend fun get(videoId: String): MediaItemFormatInfo? = prefetch(videoId, urgent = true).await()

    fun isReady(videoId: String): Boolean = cache[videoId]?.result?.isCompleted == true

    fun invalidate(videoId: String) { cache.remove(videoId) }
}
