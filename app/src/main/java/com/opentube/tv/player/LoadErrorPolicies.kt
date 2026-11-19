package com.opentube.tv.player

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ParserException
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrBackoffError
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrPoTokenError
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrReloadPlayerError
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException
import com.google.android.exoplayer2.upstream.Loader.UnexpectedLoaderException
import java.io.FileNotFoundException
import java.io.IOException

/** 移植自 SmartTube：仅 404/410 拉黑轨道；重试间隔线性增长，最多 5 秒 */
open class DashLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getBlacklistDurationMsFor(dataType: Int, loadDurationMs: Long, exception: IOException, errorCount: Int): Long {
        val code = (exception as? InvalidResponseCodeException)?.responseCode ?: return C.TIME_UNSET
        return if (code == 404 || code == 410) DEFAULT_TRACK_BLACKLIST_MS else C.TIME_UNSET
    }

    override fun getRetryDelayMsFor(dataType: Int, loadDurationMs: Long, exception: IOException, errorCount: Int): Long =
        if (exception is ParserException || exception is FileNotFoundException || exception is UnexpectedLoaderException) C.TIME_UNSET
        else minOf((errorCount - 1) * 1000L, 5000L)
}

/** SABR：服务器要求重新拉取播放信息 / 退避 / PoToken 失效时直接失败，交给上层重载 */
class SabrLoadErrorPolicy : DashLoadErrorPolicy() {
    override fun getRetryDelayMsFor(dataType: Int, loadDurationMs: Long, exception: IOException, errorCount: Int): Long {
        if (exception.message?.contains("Wait 5 sec") == true) return 5_000
        return when (exception.cause) {
            is SabrReloadPlayerError, is SabrBackoffError, is SabrPoTokenError -> C.TIME_UNSET
            else -> super.getRetryDelayMsFor(dataType, loadDurationMs, exception, errorCount)
        }
    }
}
