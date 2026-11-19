package com.opentube.tv.player

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceView
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrBackoffError
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrPoTokenError
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrReloadPlayerError
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.video.VideoListener
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment
import com.opentube.tv.data.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 清晰度选择：Auto = 按带宽自适应；否则锁定最高到指定高度 */
sealed interface Quality {
    data object Auto : Quality
    data class Max(val height: Int) : Quality
}

data class PlayerState(
    val phase: Phase = Phase.Loading,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val videoAspect: Float = 16f / 9f,
    val currentHeight: Int = 0,
    val availableHeights: List<Int> = emptyList(),
    val quality: Quality = Quality.Auto,
    val speed: Float = 1f,
    val segments: List<SponsorSegment> = emptyList(),
    val lastSkipped: SponsorSegment? = null,
    val message: String? = null,
) {
    enum class Phase { Loading, Buffering, Playing, Paused, Ended, Error }
}

/**
 * OpenTube 播放引擎，封装 SmartTube 的 ExoPlayer 2.10 fork。
 *
 * 起播速度优化：
 *  1. 播放信息走 [Prefetcher]（焦点停留即预取）；
 *  2. 低起播门槛：缓冲 0.8 秒即开播，卡顿后 2 秒恢复；
 *  3. 自适应码率默认开启，并记住上次测得的带宽作为初始估计，起播即接近合适画质；
 *  4. 视口限制在物理屏幕分辨率，避免在 1080p 电视上白白拉 4K。
 */
class OtPlayer(private val context: Context) {

    private val prefs = context.getSharedPreferences("player", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
        .setInitialBitrateEstimate(prefs.getLong(KEY_BITRATE, 2_500_000L))
        .build()

    private val trackSelector = DefaultTrackSelector(
        AdaptiveTrackSelection.Factory(
            /* minDurationForQualityIncreaseMs = */ 6_000,
            /* maxDurationForQualityDecreaseMs = */ 20_000,
            /* minDurationToRetainAfterDiscardMs = */ 20_000,
            /* bandwidthFraction = */ 0.75f,
        )
    ).apply {
        parameters = buildUponParameters().setViewportSizeToPhysicalDisplaySize(context, true).build()
    }

    private val baseLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 15_000,
            /* maxBufferMs = */ 50_000,
            /* bufferForPlaybackMs = */ 800,
            /* bufferForPlaybackAfterRebufferMs = */ 2_000,
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .createDefaultLoadControl()
    /** 预加载期间只缓冲几秒，避免浏览时白白下载大量数据 */
    private val loadControl = PreloadAwareLoadControl(baseLoadControl, preloadBufferUs = 6_000_000)

    val exo: SimpleExoPlayer = ExoPlayerFactory.newSimpleInstance(
        context,
        DefaultRenderersFactory(context).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON),
        trackSelector,
        loadControl,
        null,
        bandwidthMeter,
    )

    private val sources = MediaSources(context)
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private var videoId: String? = null
    private var mediaItem: MediaItem? = null
    private var retries = 0
    private var loadJob: Job? = null
    private var lastReportAt = 0L
    private var preloadedId: String? = null
    private var preloadJob: Job? = null
    private var openedAt = 0L
    private var firstFrameLogged = false

    private fun mark(step: String) = Log.i("OtTiming", "$step +${SystemClock.elapsedRealtime() - openedAt}ms id=$videoId")

    var sponsorCategories: Set<String> = DEFAULT_SPONSOR_CATEGORIES

    init {
        exo.addListener(object : Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                val phase = when (playbackState) {
                    Player.STATE_BUFFERING -> if (_state.value.phase == PlayerState.Phase.Loading) PlayerState.Phase.Loading else PlayerState.Phase.Buffering
                    Player.STATE_READY -> if (playWhenReady) PlayerState.Phase.Playing else PlayerState.Phase.Paused
                    Player.STATE_ENDED -> PlayerState.Phase.Ended
                    else -> _state.value.phase
                }
                if (playbackState == Player.STATE_READY) { retries = 0; if (!firstFrameLogged) mark("ready") }
                if (!playWhenReady || playbackState == Player.STATE_ENDED) reportPosition()
                _state.update { it.copy(phase = phase) }
            }

            override fun onPlayerError(error: ExoPlaybackException) = handleError(error)

            override fun onTracksChanged(trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {
                _state.update { it.copy(availableHeights = videoHeights()) }
            }
        })
        exo.addVideoListener(object : VideoListener {
            override fun onRenderedFirstFrame() {
                if (!firstFrameLogged) { firstFrameLogged = true; mark("first-frame") }
            }

            override fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {
                if (height > 0) _state.update { it.copy(videoAspect = width * pixelWidthHeightRatio / height, currentHeight = height) }
            }
        })
        scope.launch { tickLoop() }
    }

    fun attach(surface: SurfaceView) = exo.setVideoSurfaceView(surface)
    fun detach(surface: SurfaceView) = exo.clearVideoSurfaceView(surface)

    /**
     * 浏览时的后台预热：焦点停留的视频先在后台开流（不播放、不上报、只缓冲几秒）。
     * YouTube SABR 在会话的第一个请求后固定要求等 ~4 秒，预热把这段等待挪到用户看标题的时间里，
     * 按下确认时多半已经有画面可播。
     */
    fun preload(item: MediaItem) {
        val id = item.videoId ?: return
        if (videoId != null || preloadedId == id) return
        preloadJob?.cancel()
        preloadJob = scope.launch {
            val fi = Prefetcher.get(id) ?: return@launch
            val r = sources.create(fi) as? MediaSources.Result.Ready ?: return@launch
            if (videoId != null) return@launch
            preloadedId = id
            loadControl.preloading = true
            exo.playWhenReady = false
            exo.prepare(r.source, true, true)
            val resume = resumePositionMs(item, fi.lengthSeconds?.toLongOrNull()?.times(1000) ?: 0)
            if (resume > 0) exo.seekTo(resume)
            Log.i("OtTiming", "preload id=$id kind=${r.kind}")
        }
    }

    /** 离开播放页：上报进度、停止播放，之后可以继续预热别的视频 */
    fun close() {
        reportPosition()
        loadJob?.cancel()
        videoId = null
        mediaItem = null
        preloadedId = null
        exo.stop()
    }

    /** App 退到后台时停止预热 */
    fun cancelPreload() {
        preloadJob?.cancel()
        if (videoId == null && preloadedId != null) { preloadedId = null; exo.stop() }
    }

    /** 打开视频。startMs < 0 表示按后端记录的观看进度续播 */
    fun open(videoId: String, item: MediaItem? = null, startMs: Long = -1) {
        reportPosition()
        val warm = preloadedId == videoId && startMs < 0 && exo.playbackState != Player.STATE_IDLE
        preloadJob?.cancel()
        preloadedId = null
        loadControl.preloading = false
        this.videoId = videoId
        this.mediaItem = item
        retries = 0
        openedAt = SystemClock.elapsedRealtime(); firstFrameLogged = false
        mark("open prefetched=${Prefetcher.isReady(videoId)} preloaded=$warm")
        val prev = _state.value
        _state.value = PlayerState(quality = prev.quality, speed = prev.speed)
        if (warm) {
            _state.update { it.copy(availableHeights = videoHeights(), videoAspect = prev.videoAspect, currentHeight = prev.currentHeight) }
            exo.playWhenReady = true
        } else {
            load(startMs)
        }
        scope.launch {
            val segs = YouTube.sponsorSegments(videoId, sponsorCategories)
            if (this@OtPlayer.videoId == videoId) _state.update { it.copy(segments = segs) }
        }
    }

    private fun load(startMs: Long) {
        val id = videoId ?: return
        loadJob?.cancel()
        loadJob = scope.launch {
            val fi = Prefetcher.get(id)
            mark("format-info")
            if (fi == null) {
                _state.update { it.copy(phase = PlayerState.Phase.Error, message = "无法获取视频信息，请检查网络") }
                return@launch
            }
            when (val r = sources.create(fi)) {
                is MediaSources.Result.Unplayable -> _state.update { it.copy(phase = PlayerState.Phase.Error, message = r.reason ?: "该视频无法播放") }
                is MediaSources.Result.NotYet -> _state.update { it.copy(phase = PlayerState.Phase.Error, message = r.reason ?: "直播尚未开始") }
                is MediaSources.Result.Ready -> {
                    exo.prepare(r.source, true, true)
                    mark("prepare ${r.kind}")
                    val resume = when {
                        startMs >= 0 -> startMs
                        else -> resumePositionMs(mediaItem, fi.lengthSeconds?.toLongOrNull()?.times(1000) ?: 0)
                    }
                    if (resume > 0) exo.seekTo(resume)
                    exo.playWhenReady = true
                }
            }
        }
    }

    private fun resumePositionMs(item: MediaItem?, durationMs: Long): Long {
        if (item == null) return 0
        if (item.startTimeSeconds > 0) return item.startTimeSeconds * 1000L
        val pct = item.percentWatched
        if (pct in 3..94 && durationMs > 0) return durationMs * pct / 100
        return 0
    }

    private fun handleError(error: ExoPlaybackException) {
        Log.w("OtTiming", "error retry=$retries", error)
        val pos = exo.currentPosition
        val id = videoId ?: return
        val cause = generateSequence<Throwable>(error) { it.cause }.toList()
        val reload = cause.firstNotNullOfOrNull { it as? SabrReloadPlayerError }
        val needsNewClient = cause.any {
            it is SabrPoTokenError || (it is HttpDataSource.InvalidResponseCodeException && it.responseCode == 403)
        }
        val backoff = cause.any { it is SabrBackoffError }
        if (retries >= 3) {
            _state.update { it.copy(phase = PlayerState.Phase.Error, message = "播放失败：${error.cause?.message ?: error.message}") }
            return
        }
        retries++
        when {
            reload != null -> YouTube.reloadPlayerResponse(id, reload.reloadPlaybackToken)
            needsNewClient -> YouTube.switchNextClient()
            backoff -> Unit
        }
        Prefetcher.invalidate(id)
        _state.update { it.copy(phase = PlayerState.Phase.Buffering) }
        scope.launch {
            if (backoff) delay(2_000)
            load(pos)
        }
    }

    fun togglePlay() { exo.playWhenReady = !exo.playWhenReady }
    fun seekBy(deltaMs: Long) = seekTo(exo.currentPosition + deltaMs)
    fun seekTo(ms: Long) {
        val d = exo.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        exo.seekTo(ms.coerceIn(0, d))
        reportPosition()
    }

    fun setSpeed(speed: Float) {
        exo.playbackParameters = PlaybackParameters(speed)
        _state.update { it.copy(speed = speed) }
    }

    /** 自动模式下的画质上限（设置项）。0 = 不限，仍受屏幕分辨率约束 */
    var maxHeightCap: Int = 0
        set(v) { field = v; if (_state.value.quality == Quality.Auto) setQuality(Quality.Auto) }

    fun setQuality(q: Quality) {
        val b = trackSelector.buildUponParameters().clearSelectionOverrides()
            .setViewportSizeToPhysicalDisplaySize(context, true)
            .setMaxVideoSize(Int.MAX_VALUE, if (maxHeightCap > 0 && q == Quality.Auto) maxHeightCap else Int.MAX_VALUE)
        if (q is Quality.Max) {
            // 2.10 没有 minVideoSize：直接覆盖选择该高度的轨道（同一轨道组内，可保留帧率/码率差异间的自适应）
            val info = trackSelector.currentMappedTrackInfo
            if (info != null) for (r in 0 until info.rendererCount) {
                if (info.getRendererType(r) != C.TRACK_TYPE_VIDEO) continue
                val groups = info.getTrackGroups(r)
                for (g in 0 until groups.length) {
                    val idx = (0 until groups[g].length).filter { groups[g].getFormat(it).height == q.height }
                    if (idx.isNotEmpty()) {
                        b.setSelectionOverride(r, groups, DefaultTrackSelector.SelectionOverride(g, *idx.toIntArray()))
                        break
                    }
                }
            }
        }
        trackSelector.parameters = b.build()
        _state.update { it.copy(quality = q) }
    }

    private fun videoHeights(): List<Int> {
        val info = trackSelector.currentMappedTrackInfo ?: return emptyList()
        val heights = sortedSetOf<Int>(compareByDescending { it })
        for (r in 0 until info.rendererCount) {
            if (info.getRendererType(r) != C.TRACK_TYPE_VIDEO) continue
            val groups = info.getTrackGroups(r)
            for (g in 0 until groups.length) for (t in 0 until groups[g].length) {
                val h = groups[g].getFormat(t).height
                if (h > 0) heights += h
            }
        }
        return heights.toList()
    }

    /** 每 250ms：刷新进度、执行 SponsorBlock 跳过、定期上报观看记录 */
    private suspend fun tickLoop() {
        while (scope.isActive) {
            delay(250)
            if (videoId == null) continue
            val pos = exo.currentPosition
            val dur = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0
            _state.update { it.copy(positionMs = pos, durationMs = dur, bufferedMs = exo.bufferedPosition) }

            if (exo.playWhenReady && exo.playbackState == Player.STATE_READY) {
                // 只在刚进入片段的 2 秒窗口内跳过，用户手动 seek 到片段中间不会被拽走
                val window = (2_000 * exo.playbackParameters.speed).toLong()
                val seg = _state.value.segments.firstOrNull { pos >= it.startMs && pos <= minOf(it.startMs + window, it.endMs) }
                if (seg != null) {
                    exo.seekTo(if (dur > 0) minOf(seg.endMs, dur) else seg.endMs)
                    _state.update { it.copy(lastSkipped = seg) }
                }
                if (System.currentTimeMillis() - lastReportAt > 3 * 60_000) reportPosition()
            }
        }
    }

    fun clearSkipNotice() = _state.update { it.copy(lastSkipped = null) }

    private fun reportPosition() {
        val id = videoId ?: return
        val pos = exo.currentPosition
        if (pos <= 0) return
        lastReportAt = System.currentTimeMillis()
        runCatching { YouTube.reportPosition(mediaItem, id, pos) }
    }

    fun release() {
        reportPosition()
        bandwidthMeter.bitrateEstimate.takeIf { it > 0 }?.let { prefs.edit().putLong(KEY_BITRATE, it).apply() }
        scope.cancel()
        exo.release()
    }

    companion object {
        private const val KEY_BITRATE = "bitrate_estimate"
        val DEFAULT_SPONSOR_CATEGORIES = setOf(
            SponsorSegment.CATEGORY_SPONSOR, SponsorSegment.CATEGORY_INTRO, SponsorSegment.CATEGORY_OUTRO,
            SponsorSegment.CATEGORY_SELF_PROMO, SponsorSegment.CATEGORY_INTERACTION,
            SponsorSegment.CATEGORY_MUSIC_OFF_TOPIC, SponsorSegment.CATEGORY_PREVIEW_RECAP,
        )
    }
}
