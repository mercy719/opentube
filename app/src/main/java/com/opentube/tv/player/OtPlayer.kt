package com.opentube.tv.player

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
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
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
 * 起播速度：YouTube 的 SABR 协议在每个播放会话的第一个请求后，固定要求客户端等 ~4 秒（服务器按时间计，跳不过）。
 * 所以这里维护一个小的播放器池（[POOL_SIZE] 个 ExoPlayer）：
 *  - 遥控器焦点所在的视频和左右邻居组成“热区”，每个占一个播放器在后台开流，
 *    从“跳过片头 SponsorBlock 片段后”的位置起缓冲 30 秒正片（不播放、不出声、不上报历史）；
 *    几个会话的 4 秒退避并行消化；
 *  - 焦点停留一会儿，焦点那个播放器在首页大图里静音播放预览；
 *  - 按确认时，直接把这个播放器切到全屏、开声音，接着播。
 * 其余优化：低起播门槛（缓冲 0.8 秒即开播）、记住上次带宽作为初始估计、视口限制在物理屏幕分辨率。
 */
class OtPlayer(private val context: Context) {

    private val prefs = context.getSharedPreferences("player", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
        .setInitialBitrateEstimate(prefs.getLong(KEY_BITRATE, 2_500_000L))
        .build()

    private val sources = MediaSources(context)

    /** 池中的一个播放器 */
    private inner class Engine(val slot: Int) {
        val trackSelector = DefaultTrackSelector(
            AdaptiveTrackSelection.Factory(
                /* minDurationForQualityIncreaseMs = */ 6_000,
                /* maxDurationForQualityDecreaseMs = */ 20_000,
                /* minDurationToRetainAfterDiscardMs = */ 20_000,
                /* bandwidthFraction = */ 0.75f,
            )
        ).apply { parameters = buildUponParameters().setViewportSizeToPhysicalDisplaySize(context, true).build() }

        val loadControl = PreloadAwareLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 15_000,
                    /* maxBufferMs = */ 50_000,
                    /* bufferForPlaybackMs = */ 800,
                    /* bufferForPlaybackAfterRebufferMs = */ 2_000,
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .createDefaultLoadControl(),
            preloadBufferUs = PRELOAD_BUFFER_MS * 1000,
        )

        val exo: SimpleExoPlayer = ExoPlayerFactory.newSimpleInstance(
            context,
            DefaultRenderersFactory(context).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON),
            trackSelector, loadControl, null, bandwidthMeter,
        )

        /** 当前装载的视频；null = 空闲 */
        var id: String? = null
        var item: MediaItem? = null
        var segments: List<SponsorSegment> = emptyList()
        var job: Job? = null
        var lastUsed = 0L
        /** 已调用 prepare（后台开流中或已就绪） */
        var prepared = false
        var aspect = 16f / 9f
        var height = 0
        /** 当前绑定的画面：SurfaceView（全屏）/ TextureView（预览）/ null */
        var boundTo: Any? = null
        /** 开流就绪后要跳到的位置（跳过开头的 SponsorBlock 片段 / 续播点）；-1 = 无 */
        var pendingSeek = -1L
        var seekAt = 0L
        var retried = 0

        fun reset() {
            job?.cancel(); job = null
            id = null; item = null; segments = emptyList(); prepared = false; pendingSeek = -1L
            exo.playWhenReady = false
            exo.stop(true)
        }
    }

    private val engines = List(POOL_SIZE) { Engine(it) }
    /** 正在播放页使用的播放器 */
    private var active: Engine? = null
    /** 正在大图里预览的播放器 */
    private var previewEngine: Engine? = null
    private var previewWanted: String? = null
    private var mainSurface: SurfaceView? = null
    private var previewSurface: TextureView? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private val _previewId = MutableStateFlow<String?>(null)
    /** 已在大图里出画面的预览视频 id（大图据此把封面淡出） */
    val previewId: StateFlow<String?> = _previewId

    private val videoId get() = active?.id
    private var retries = 0
    private var loadJob: Job? = null
    private var lastReportAt = 0L
    private var openedAt = 0L
    private var firstFrameLogged = false

    private val sponsorCache = object : LinkedHashMap<String, List<SponsorSegment>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SponsorSegment>>?) = size > 32
    }

    var sponsorCategories: Set<String> = DEFAULT_SPONSOR_CATEGORIES
        set(v) { field = v; sponsorCache.clear() }

    private fun mark(step: String) = Log.i("OtTiming", "$step +${SystemClock.elapsedRealtime() - openedAt}ms id=$videoId")

    init {
        engines.forEach { e -> listen(e) }
        scope.launch { tickLoop() }
    }

    private fun listen(e: Engine) {
        e.exo.addListener(object : Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                // SABR 在会话初始化完成前 seek 会让首个分片多等好几秒，所以等第一次就绪再跳
                if (playbackState == Player.STATE_READY) {
                    if (e.pendingSeek > 0) {
                        Log.i("OtTiming", "deferred-seek slot=${e.slot} id=${e.id} to=${e.pendingSeek}")
                        e.exo.seekTo(e.pendingSeek); e.pendingSeek = -1L; e.seekAt = SystemClock.elapsedRealtime()
                    } else if (e.seekAt > 0) { Log.i("OtTiming", "seek-ready slot=${e.slot} +${SystemClock.elapsedRealtime() - e.seekAt}ms id=${e.id}"); e.seekAt = 0 }
                }
                if (e !== active) return
                val phase = phaseOf(playWhenReady, playbackState)
                if (playbackState == Player.STATE_READY) { retries = 0; if (!firstFrameLogged) mark("ready") }
                if (!playWhenReady || playbackState == Player.STATE_ENDED) reportPosition()
                _state.update { it.copy(phase = phase) }
            }

            override fun onPlayerError(error: ExoPlaybackException) {
                if (e === active) handleError(error)
                else {
                    val item = e.item
                    val poPending = generateSequence<Throwable>(error) { it.cause }.any { it is SabrPoTokenError }
                    Log.w("OtTiming", "preload error id=${e.id} poToken=$poPending")
                    if (e === previewEngine) stopPreview()
                    e.id?.let(Prefetcher::invalidate)
                    e.reset()
                    // PoToken 刚启动时还没算好，稍等再试一次；期间如果这个槽位被别的视频占用就作罢
                    if (poPending && item != null && e.retried < 2) {
                        val n = e.retried + 1
                        e.id = item.videoId; e.item = item
                        e.job = scope.launch {
                            delay(3_000)
                            if (e.id == item.videoId && !e.prepared && active !== e) { preload(e, item); e.retried = n }
                        }
                    }
                }
            }

            override fun onTracksChanged(trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {
                if (e === active) _state.update { it.copy(availableHeights = videoHeights(e)) }
            }
        })
        e.exo.addVideoListener(object : VideoListener {
            override fun onRenderedFirstFrame() {
                if (e === active && !firstFrameLogged) { firstFrameLogged = true; mark("first-frame") }
                if (e === previewEngine && e.boundTo is TextureView) _previewId.value = e.id
            }

            override fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {
                if (height <= 0) return
                e.aspect = width * pixelWidthHeightRatio / height; e.height = height
                if (e === active) _state.update { it.copy(videoAspect = e.aspect, currentHeight = height) }
            }
        })
    }

    private fun phaseOf(playWhenReady: Boolean, playbackState: Int) = when (playbackState) {
        Player.STATE_BUFFERING -> if (_state.value.phase == PlayerState.Phase.Loading) PlayerState.Phase.Loading else PlayerState.Phase.Buffering
        Player.STATE_READY -> if (playWhenReady) PlayerState.Phase.Playing else PlayerState.Phase.Paused
        Player.STATE_ENDED -> PlayerState.Phase.Ended
        else -> _state.value.phase
    }

    // ---------------- 画面绑定 ----------------

    /** 全屏播放页的画面 */
    fun attach(surface: SurfaceView) { mainSurface = surface; bindSurfaces() }
    fun detach(surface: SurfaceView) { if (mainSurface === surface) { mainSurface = null; bindSurfaces() } }

    /** 首页大图里的预览画面 */
    fun attachPreview(view: TextureView) { previewSurface = view; bindSurfaces() }
    fun detachPreview(view: TextureView) { if (previewSurface === view) { previewSurface = null; _previewId.value = null; bindSurfaces() } }

    /** 让每块画面只绑在一个播放器上：先解绑，再绑定 */
    private fun bindSurfaces() {
        val want = engines.associateWith { e ->
            when {
                e === active -> mainSurface
                e === previewEngine -> previewSurface
                else -> null
            }
        }
        for (e in engines) if (e.boundTo != null && e.boundTo !== want[e]) { e.exo.clearVideoSurface(); e.boundTo = null }
        for (e in engines) {
            val v = want[e] ?: continue
            if (e.boundTo === v) continue
            when (v) {
                is SurfaceView -> e.exo.setVideoSurfaceView(v)
                is TextureView -> e.exo.setVideoTextureView(v)
            }
            e.boundTo = v
        }
    }

    // ---------------- 热区预热与预览 ----------------

    /**
     * 浏览时调用：zone 第一个是焦点视频，后面是邻居。
     * 已经装着这些视频的播放器保持不动，其余播放器按最久未用的顺序换装新视频。
     */
    fun warm(zone: List<MediaItem>) {
        if (active != null) return
        val wanted = zone.filter { it.videoId != null }.distinctBy { it.videoId }.take(POOL_SIZE)
        if (wanted.isEmpty()) return
        // 邻居先排，焦点最后插到队首
        wanted.drop(1).forEach { Prefetcher.prefetch(it.videoId!!) }
        Prefetcher.prefetch(wanted.first().videoId!!, urgent = true)

        val now = SystemClock.elapsedRealtime()
        val ids = wanted.map { it.videoId }
        val free = engines.filter { it.id !in ids && it !== previewEngine }.sortedBy { it.lastUsed }.toMutableList()
        wanted.forEachIndexed { i, item ->
            val held = engines.firstOrNull { it.id == item.videoId }
            if (held != null) { held.lastUsed = now - i; return@forEachIndexed }
            val e = free.removeFirstOrNull() ?: return@forEachIndexed
            e.lastUsed = now - i
            preload(e, item)
        }
    }

    private fun preload(e: Engine, item: MediaItem) {
        val id = item.videoId ?: return
        e.reset(); e.retried = 0
        e.id = id; e.item = item
        e.job = scope.launch {
            val segsAsync = async { sponsorFor(id) }
            val fi = Prefetcher.get(id)
            val r = fi?.let { sources.create(it) } as? MediaSources.Result.Ready
            if (fi == null || r == null || e.id != id) { if (e.id == id) e.id = null; return@launch }
            val segs = withTimeoutOrNull(1_500) { segsAsync.await() } ?: emptyList()
            e.segments = segs
            val durMs = fi.lengthSeconds?.toLongOrNull()?.times(1000) ?: 0
            val start = skipSegmentsAt(resumePositionMs(item, durMs), segs)
            // 目标：30 秒正片。窗口里的 SponsorBlock 片段会被跳过，它们的时长另外补上
            val window = segs.filter { it.endMs > start && it.startMs < start + PRELOAD_BUFFER_MS }
                .sumOf { minOf(it.endMs, start + PRELOAD_BUFFER_MS) - maxOf(it.startMs, start) }
            e.loadControl.preloadBufferUs = (PRELOAD_BUFFER_MS + window) * 1000
            e.loadControl.preloading = true
            e.exo.volume = 0f
            e.exo.playWhenReady = false
            e.exo.prepare(r.source, true, true)
            e.pendingSeek = start
            e.prepared = true
            Log.i("OtTiming", "preload slot=${e.slot} id=$id kind=${r.kind} start=${start}ms segs=${segs.size}")
            if (previewWanted == id) beginPreview(e)
        }
    }

    /** 焦点停留够久：在大图里静音播放焦点视频 */
    fun startPreview(videoId: String) {
        if (active != null) return
        previewWanted = videoId
        engines.firstOrNull { it.id == videoId && it.prepared }?.let(::beginPreview)
    }

    private fun beginPreview(e: Engine) {
        if (active != null || previewWanted != e.id) return
        previewEngine?.takeIf { it !== e }?.exo?.playWhenReady = false
        previewEngine = e
        _previewId.value = null
        e.exo.volume = 0f
        e.exo.playWhenReady = true
        bindSurfaces()
    }

    /** 焦点移走 / 离开首页：停止预览（播放器保留缓冲） */
    fun stopPreview() {
        previewWanted = null
        previewEngine?.exo?.playWhenReady = false
        previewEngine = null
        _previewId.value = null
        bindSurfaces()
    }

    /** App 退到后台：暂停一切，丢掉后台缓冲 */
    fun pauseAll() {
        stopPreview()
        active?.exo?.playWhenReady = false
        engines.filter { it !== active }.forEach { it.reset() }
    }

    // ---------------- 播放 ----------------

    /** 打开视频。startMs < 0 表示按后端记录的观看进度续播 */
    fun open(videoId: String, item: MediaItem? = null, startMs: Long = -1) {
        reportPosition()
        val warmEngine = if (startMs < 0) engines.firstOrNull {
            it.id == videoId && it.prepared && it.exo.playbackState != Player.STATE_IDLE
        } else null
        previewWanted = null
        previewEngine = null
        _previewId.value = null

        val e = warmEngine ?: engines.minByOrNull { it.lastUsed }!!
        // 正式播放时其他播放器全部停下，把带宽让出来
        engines.filter { it !== e }.forEach { it.reset() }
        active = e
        e.lastUsed = SystemClock.elapsedRealtime()
        e.loadControl.preloading = false
        e.exo.volume = 1f
        retries = 0
        openedAt = SystemClock.elapsedRealtime(); firstFrameLogged = false
        val prev = _state.value
        _state.value = PlayerState(quality = prev.quality, speed = prev.speed)
        applyQuality(e, prev.quality)
        e.exo.playbackParameters = PlaybackParameters(prev.speed)
        bindSurfaces()

        if (warmEngine != null) {
            mark("open warm slot=${e.slot} pos=${e.exo.currentPosition}")
            e.exo.playWhenReady = true
            _state.update {
                it.copy(
                    phase = if (e.exo.playbackState == Player.STATE_READY) PlayerState.Phase.Playing else PlayerState.Phase.Loading,
                    availableHeights = videoHeights(e), videoAspect = e.aspect, currentHeight = e.height,
                    segments = e.segments,
                )
            }
        } else {
            e.reset()
            e.id = videoId; e.item = item; e.prepared = true
            mark("open cold prefetched=${Prefetcher.isReady(videoId)}")
            load(startMs)
        }
        if (e.segments.isEmpty()) scope.launch {
            val segs = sponsorFor(videoId)
            if (this@OtPlayer.videoId == videoId) { e.segments = segs; _state.update { it.copy(segments = segs) } }
        }
    }

    private fun load(startMs: Long) {
        val e = active ?: return
        val id = e.id ?: return
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
                    e.exo.prepare(r.source, true, true)
                    mark("prepare ${r.kind}")
                    val resume = when {
                        startMs >= 0 -> startMs
                        else -> skipSegmentsAt(resumePositionMs(e.item, fi.lengthSeconds?.toLongOrNull()?.times(1000) ?: 0), e.segments)
                    }
                    if (resume > 0) e.exo.seekTo(resume)
                    e.exo.playWhenReady = true
                }
            }
        }
    }

    /** 离开播放页：上报进度、停止播放，播放器回到池里 */
    fun close() {
        reportPosition()
        loadJob?.cancel()
        val e = active
        active = null
        e?.reset()
        bindSurfaces()
    }

    private fun resumePositionMs(item: MediaItem?, durationMs: Long): Long {
        if (item == null) return 0
        if (item.startTimeSeconds > 0) return item.startTimeSeconds * 1000L
        val pct = item.percentWatched
        if (pct in 3..94 && durationMs > 0) return durationMs * pct / 100
        return 0
    }

    /** 起点落在 SponsorBlock 片段里（或紧挨着片段开头）就挪到片段结束；连续的片段一起跳 */
    private fun skipSegmentsAt(startMs: Long, segs: List<SponsorSegment>): Long {
        var pos = startMs
        while (true) {
            val s = segs.firstOrNull { pos >= it.startMs - 1_000 && pos < it.endMs } ?: return pos
            pos = s.endMs
        }
    }

    private suspend fun sponsorFor(id: String): List<SponsorSegment> {
        if (sponsorCategories.isEmpty()) return emptyList()
        sponsorCache[id]?.let { return it }
        val segs = YouTube.sponsorSegments(id, sponsorCategories)
        sponsorCache[id] = segs
        return segs
    }

    private fun handleError(error: ExoPlaybackException) {
        Log.w("OtTiming", "error retry=$retries", error)
        val e = active ?: return
        val pos = e.exo.currentPosition
        val id = e.id ?: return
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

    fun togglePlay() { active?.exo?.let { it.playWhenReady = !it.playWhenReady } }
    fun seekBy(deltaMs: Long) { val x = active?.exo ?: return; seekTo(x.currentPosition + deltaMs) }
    fun seekTo(ms: Long) {
        val x = active?.exo ?: return
        val d = x.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        x.seekTo(ms.coerceIn(0, d))
        reportPosition()
    }

    fun setSpeed(speed: Float) {
        active?.exo?.playbackParameters = PlaybackParameters(speed)
        _state.update { it.copy(speed = speed) }
    }

    /** 自动模式下的画质上限（设置项）。0 = 不限，仍受屏幕分辨率约束 */
    var maxHeightCap: Int = 0
        set(v) { field = v; engines.forEach { e -> applyQuality(e, if (e === active) _state.value.quality else Quality.Auto) } }

    fun setQuality(q: Quality) {
        val e = active ?: return
        applyQuality(e, q)
        _state.update { it.copy(quality = q) }
    }

    private fun applyQuality(e: Engine, q: Quality) {
        val b = e.trackSelector.buildUponParameters().clearSelectionOverrides()
            .setViewportSizeToPhysicalDisplaySize(context, true)
            .setMaxVideoSize(Int.MAX_VALUE, if (maxHeightCap > 0 && q == Quality.Auto) maxHeightCap else Int.MAX_VALUE)
        if (q is Quality.Max) {
            // 2.10 没有 minVideoSize：直接覆盖选择该高度的轨道
            val info = e.trackSelector.currentMappedTrackInfo
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
        e.trackSelector.parameters = b.build()
    }

    private fun videoHeights(e: Engine): List<Int> {
        val info = e.trackSelector.currentMappedTrackInfo ?: return emptyList()
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

    /** 每 250ms：刷新进度、执行 SponsorBlock 跳过（播放页和预览都跳）、定期上报观看记录 */
    private suspend fun tickLoop() {
        while (scope.isActive) {
            delay(250)
            previewEngine?.let { skipSponsor(it) }
            val e = active ?: continue
            val x = e.exo
            val pos = x.currentPosition
            val dur = x.duration.takeIf { it != C.TIME_UNSET } ?: 0
            _state.update { it.copy(positionMs = pos, durationMs = dur, bufferedMs = x.bufferedPosition) }
            if (x.playWhenReady && x.playbackState == Player.STATE_READY) {
                skipSponsor(e)?.let { seg -> _state.update { it.copy(lastSkipped = seg) } }
                if (System.currentTimeMillis() - lastReportAt > 3 * 60_000) reportPosition()
            }
        }
    }

    /** 只在刚进入片段的 2 秒窗口内跳过，用户手动 seek 到片段中间不会被拽走 */
    private fun skipSponsor(e: Engine): SponsorSegment? {
        val x = e.exo
        if (!x.playWhenReady || x.playbackState != Player.STATE_READY) return null
        val pos = x.currentPosition
        val dur = x.duration.takeIf { it != C.TIME_UNSET } ?: 0
        val window = (2_000 * x.playbackParameters.speed).toLong()
        val segs = if (e === active) _state.value.segments.ifEmpty { e.segments } else e.segments
        val seg = segs.firstOrNull { pos >= it.startMs && pos <= minOf(it.startMs + window, it.endMs) } ?: return null
        x.seekTo(if (dur > 0) minOf(seg.endMs, dur) else seg.endMs)
        return seg
    }

    fun clearSkipNotice() = _state.update { it.copy(lastSkipped = null) }

    private fun reportPosition() {
        val e = active ?: return
        val id = e.id ?: return
        val pos = e.exo.currentPosition
        if (pos <= 0) return
        lastReportAt = System.currentTimeMillis()
        runCatching { YouTube.reportPosition(e.item, id, pos) }
    }

    fun release() {
        reportPosition()
        bandwidthMeter.bitrateEstimate.takeIf { it > 0 }?.let { prefs.edit().putLong(KEY_BITRATE, it).apply() }
        scope.cancel()
        engines.forEach { it.exo.release() }
    }

    companion object {
        private const val KEY_BITRATE = "bitrate_estimate"
        /** 热区大小：焦点 + 左右邻居 */
        const val POOL_SIZE = 3
        /** 每个热区视频预先缓冲的正片时长 */
        private const val PRELOAD_BUFFER_MS = 30_000L
        val DEFAULT_SPONSOR_CATEGORIES = setOf(
            SponsorSegment.CATEGORY_SPONSOR, SponsorSegment.CATEGORY_INTRO, SponsorSegment.CATEGORY_OUTRO,
            SponsorSegment.CATEGORY_SELF_PROMO, SponsorSegment.CATEGORY_INTERACTION,
            SponsorSegment.CATEGORY_MUSIC_OFF_TOPIC, SponsorSegment.CATEGORY_PREVIEW_RECAP,
        )
    }
}
