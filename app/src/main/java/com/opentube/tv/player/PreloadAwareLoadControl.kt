package com.opentube.tv.player

import com.google.android.exoplayer2.LoadControl
import com.google.android.exoplayer2.Renderer
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.Allocator

/** 包一层 LoadControl：预加载模式下缓冲到 [preloadBufferUs] 就停，正式播放时完全交给原策略 */
class PreloadAwareLoadControl(private val base: LoadControl, private val preloadBufferUs: Long) : LoadControl {
    @Volatile var preloading = false

    override fun onPrepared() = base.onPrepared()
    override fun onTracksSelected(renderers: Array<out Renderer>, trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) =
        base.onTracksSelected(renderers, trackGroups, trackSelections)
    override fun onStopped() = base.onStopped()
    override fun onReleased() = base.onReleased()
    override fun getAllocator(): Allocator = base.allocator
    override fun getBackBufferDurationUs(): Long = base.backBufferDurationUs
    override fun retainBackBufferFromKeyframe(): Boolean = base.retainBackBufferFromKeyframe()

    override fun shouldContinueLoading(bufferedDurationUs: Long, playbackSpeed: Float): Boolean =
        if (preloading) bufferedDurationUs < preloadBufferUs else base.shouldContinueLoading(bufferedDurationUs, playbackSpeed)

    override fun shouldStartPlayback(bufferedDurationUs: Long, playbackSpeed: Float, rebuffering: Boolean): Boolean =
        base.shouldStartPlayback(bufferedDurationUs, playbackSpeed, rebuffering)
}
