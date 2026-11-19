package com.opentube.tv.player

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSourceFactory
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser2
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.sabr.DefaultSabrChunkSource
import com.google.android.exoplayer2.source.sabr.SabrMediaSource
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifestParser
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.TransferListener
import com.liskovsoft.googlecommon.common.helpers.DefaultHeaders
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import com.liskovsoft.sharedutils.okhttp.OkHttpManager
import java.util.concurrent.TimeUnit

/** 把后端返回的 MediaItemFormatInfo 变成 ExoPlayer 的 MediaSource（逻辑移植自 SmartTube ExoMediaSourceFactory） */
class MediaSources(private val context: Context) {

    sealed interface Result {
        data class Ready(val source: MediaSource, val kind: String) : Result
        data class Unplayable(val reason: String?) : Result
        /** 预告中的直播等：没有可播放数据 */
        data class NotYet(val reason: String?) : Result
    }

    fun create(fi: MediaItemFormatInfo): Result {
        if (fi.isUnplayable) return Result.Unplayable(fi.playabilityReason)
        val acceptAdaptive = !(fi.isLive && fi.startTimeMs == 0L)
        return when {
            acceptAdaptive && fi.containsDashFormats() -> Result.Ready(dash(fi), "DASH")
            acceptAdaptive && fi.containsSabrFormats() -> Result.Ready(sabr(fi), "SABR")
            fi.isLive && fi.containsDashUrl() -> Result.Ready(dashUrl(fi.dashManifestUrl), "DASH-LIVE")
            fi.isLive && fi.containsHlsUrl() -> Result.Ready(hls(fi.hlsManifestUrl), "HLS")
            fi.containsUrlFormats() -> Result.Ready(progressive(fi.createUrlList().first()), "URL")
            else -> Result.NotYet(fi.playabilityReason)
        }
    }

    private fun dash(fi: MediaItemFormatInfo): MediaSource {
        val manifest = DashManifestParser2().parse(fi)
        return DashMediaSource.Factory(DefaultDashChunkSource.Factory(media(), MAX_SEGMENTS_PER_LOAD), null)
            .setLoadErrorHandlingPolicy(DashLoadErrorPolicy())
            .createMediaSource(manifest)
    }

    private fun sabr(fi: MediaItemFormatInfo): MediaSource {
        val manifest = SabrManifestParser().parse(fi)
        return SabrMediaSource.Factory(DefaultSabrChunkSource.Factory(media(SABR_READ_TIMEOUT_MS), MAX_SEGMENTS_PER_LOAD), null)
            .setLoadErrorHandlingPolicy(SabrLoadErrorPolicy())
            .createMediaSource(manifest)
    }

    private fun dashUrl(url: String): MediaSource =
        DashMediaSource.Factory(DefaultDashChunkSource.Factory(media(), MAX_SEGMENTS_PER_LOAD), media())
            .setLoadErrorHandlingPolicy(DashLoadErrorPolicy())
            .createMediaSource(Uri.parse(url))

    private fun hls(url: String): MediaSource = HlsMediaSource.Factory(media()).createMediaSource(Uri.parse(url))

    private fun progressive(url: String): MediaSource =
        ExtractorMediaSource.Factory(media()).setExtractorsFactory(DefaultExtractorsFactory()).createMediaSource(Uri.parse(url))

    private fun media(readTimeoutMs: Long = OkHttpManager.getReadTimeoutMs()): DataSource.Factory {
        var client = OkHttpManager.instance().client
        if (client.readTimeoutMillis().toLong() != readTimeoutMs) {
            client = client.newBuilder().readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS).build()
        }
        val http = OkHttpDataSourceFactory(client, DefaultHeaders.APP_USER_AGENT, null as TransferListener?)
        return DefaultDataSourceFactory(context, null, http)
    }

    companion object {
        private const val MAX_SEGMENTS_PER_LOAD = 1
        /** SABR 读超时短一点，失败快、切换快 */
        private const val SABR_READ_TIMEOUT_MS = 4_000L
    }
}
