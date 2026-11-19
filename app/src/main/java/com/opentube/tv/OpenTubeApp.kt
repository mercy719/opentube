package com.opentube.tv

import android.os.Handler
import android.os.Looper
import androidx.multidex.MultiDexApplication
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.sharedutils.rx.RxHelper
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager

class OpenTubeApp : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        // 与 SmartTube 一致：流媒体下关闭 keep-alive 更稳定
        System.setProperty("http.keepAlive", "false")
        RxHelper.setupGlobalErrorHandler()
        // 后端所有服务都通过 GlobalPreferences 取 Context，必须最先初始化
        GlobalPreferences.instance(this)

        // 预热播放解析引擎（JS 解密 / nsig / PoToken），让第一次点视频不再等它
        Handler(Looper.getMainLooper()).postDelayed({
            RxHelper.runAsync {
                val service = YouTubeServiceManager.instance()
                service.refreshCacheIfNeeded()
                service.contentService.enableHistory(true)
            }
        }, 500)
    }
}
