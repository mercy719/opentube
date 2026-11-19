# SmartTube 后端接入笔记

（基于上游源码阅读整理，路径相对 upstream SmartTube 仓库）

## 初始化
Application.onCreate 中依次：
- `System.setProperty("http.keepAlive", "false")`
- `RxHelper.setupGlobalErrorHandler()`
- `GlobalPreferences.instance(appContext)` —— 必须在任何 `YouTubeServiceManager.instance()` 之前
- 稍后（延迟）`YouTubeServiceManager.instance().refreshCacheIfNeeded()`；后台线程 `getContentService().enableHistory(true)`

## 线程
所有 `*Observe()` 已经 subscribeOn 缓存线程池、observeOn 主线程。同步方法是阻塞网络调用。

## 登录（电视码）
`signInService.signInObserve()`：onNext 一次（用户码），onComplete = 登录成功。
激活地址 `https://yt.be/activate`，二维码 `https://youtube.com/qr/activate/<code 空格换 ->`。
退出：`selectAccount(null)` 或 `removeAccount(account)`。

## 浏览
- 首页 `getHomeObserve()`：多次 emit 行批次，追加即可
- 订阅 `getSubscriptionsObserve()`、历史 `getHistoryObserve()`
- 搜索 `getSearchObserve(text)`，联想 `getSearchTagsObserve(text)`
- 分页 `continueGroupObserve(lastGroup)`
- 频道 `getChannelObserve(item)`：首组 TYPE_CHANNEL_UPLOADS → 网格，TYPE_CHANNEL → 多行

## 播放
`allowRestrictedVideos(true)` 后 `getFormatInfoObserve(videoId)`，分派：
unplayable → 提示；DASH 格式 → `DashManifestParser2().parse(fi)`；SABR → `SabrManifestParser().parse(fi)`；
直播 DASH url / HLS url；url list；否则未开始的直播。
数据源：`OkHttpManager.instance().getClient()` + `OkHttpDataSourceFactory(client, DefaultHeaders.APP_USER_AGENT, null)`，SABR 读超时 4s。
错误策略：移植 `DashDefaultLoadErrorHandlingPolicy`、`SabrDefaultLoadErrorHandlingPolicy`。
SABR 恢复：`SabrReloadPlayerError` → `reloadPlayerResponse(videoId, token)` 后重取；403 / `SabrPoTokenError` → `switchNextClient()` 后重取。

## 历史进度
`updateHistoryPositionObserve(item, max(posMs,3000)/1000f)`：暂停、每 3 分钟、seek、结束时上报。
续播：`startTimeSeconds` > 0 用之，否则 `duration * percentWatched/100`。

## SponsorBlock
`getSponsorSegmentsObserve(videoId, categories)`，默认除 filler 外全部类别。
每秒 tick：pos 落在 [start, start+2s] 时 seek 到 end。

## 广告
后端请求层已规避（isInlinePlaybackNoAd、跳过 promo shelf），播放器无需处理。
