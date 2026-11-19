---
version: 1
slug: "app-src-main-java-com-opentube-tv-mainactivity-kt"
primary_target: "app/src/main/java/com/opentube/tv/MainActivity.kt"
related_targets: []
---

# Surface: OpenTube TV app (all screens)

Scope: 首页、订阅、搜索、历史、播放器、登录、设置。Mode: Operate（遥控器 10 英尺操作）。
Audience/job: 单个用户在客厅用遥控器刷推荐、看订阅、搜索、播放；要快、要清楚焦点在哪。

## Direction contract

THESIS: 每一行内容是一条胶片，遥控器焦点是剪辑师用红色油性笔圈出的那一格。拒绝的品类默认：灰底卡片墙 + 白色描边焦点 + 红色品牌色到处撒。

OWN-WORLD: 片基黑 #0F0D0B 底，片边 #1B1815 色带承载内容行；片边印字米白 #E9E0CC（次级 55% 透明）；油性笔红 #E2362B 只用于焦点圈与 SponsorBlock 剪切标记；安全灯琥珀 #F0AD3A 只用于进度/已看。分隔靠 1px 细缝和底色台阶，不用阴影。Barlow Semi Condensed 做数字/帧号/时长，系统 Noto Sans CJK 做中文。所有缩略图同尺寸同基线。

STORY: 打开即看到焦点视频的大图与标题，一眼知道按确认会播放什么、从哪里续播；左右浏览一条胶片，上下换胶片；播放时进度条像剪辑时间线，赞助片段被红色 X 剪掉。

FIRST VIEWPORT: 左侧 120dp 窄导航栏（图标+中文标签：首页/订阅/搜索/历史/设置）；上半屏 16:9 焦点大图（约 836×470）居左，右侧频道名、两行大标题、时长/观看/日期/已看百分比、“继续播放 · 从 mm:ss”主按钮；下半屏胶片行，每行片边印字“行名 01 → N ▸ 当前帧号”，帧宽 368、间距 28，帧号+频道名在帧下，标题一行截断。

FORM: 接触印样（35mm contact sheet），我方 grounded 列表第 4 位。seed:3347af328ab7a8d2。签名动作：焦点帧被手绘红笔框圈住（描边动画绘入），播放器时间线上 SponsorBlock 片段以分类色线段标出、已跳过段画红色 X。Raises：transit→分类色线段；folio→统一尺寸基线；labanotation→进度精确比例；console→细缝分隔、破坏性操作隔离。

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
