# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack
Kotlin + Jetpack Compose for TV（androidx.tv:tv-material），AGP 8 / Kotlin 2。YouTube 后端复用 SmartTube 的 MediaServiceCore，播放内核复用 SmartTube 的 ExoPlayer 2.10.6 fork（含 SABR）。见 `third_party/README.md`。

## Users
只有一位用户：开发者本人，在客厅电视上用遥控器看 YouTube。单账号，熟悉 SmartTube，不需要新手引导。

## Product Purpose
一个顺手、好看的 Android TV YouTube 客户端。SmartTube 功能完备但视觉拥挤丑陋、播放器难用、设置项繁杂；OpenTube 保留其可靠的后端能力（登录、推荐、订阅、播放解析、SponsorBlock、无广告），把界面和交互完全重做。成功标准：拿起遥控器就能刷首页、找订阅、搜索、播放，不需要钻设置。

## Positioning
SmartTube 的后端能力 + 克制、为 10 英尺距离设计的界面。功能少而精，默认值替用户做好决定。

## Operating Context
- 客厅电视，1080p/4K，观看距离 2–3 米，遥控器方向键 + 确认 + 返回，偶有播放/暂停键。
- 首版测试在 Android TV 模拟器（1920×1080）。
- 第一版范围：首页推荐、搜索、播放；Google 账号电视码登录、订阅、观看记录；SponsorBlock 自动跳过、无广告。
- 未做（后续再说）：手机投屏、评论、直播聊天、多账号。

## Capabilities and Constraints
- 界面文案简体中文为主，视频内容原样显示。
- 设置项只暴露真正有用的少数几项（默认清晰度、SponsorBlock 开关等），其余用合理默认值。
- 播放器：清晰度、字幕、倍速、SponsorBlock 跳过必须一两步可达；方向键左右快退快进。
- **起播速度是核心体验**（用户最痛的点之一）：SmartTube 点进视频常黑屏等待 10–20 秒。OpenTube 必须做到：焦点停留时预取播放信息、启动时预热解析引擎、低起播缓冲、加载期间显示封面与标题而非黑屏。
- **清晰度默认"自动"**：像 YouTube 一样按实测带宽自适应码率（先低后高），手动清晰度为可选项而非默认锁定。
- minSdk 21。

## Product Principles
1. 默认就对：替用户做决定，设置是例外而非入口。
2. 内容优先：缩略图和标题是主角，界面框架退后。
3. 焦点永远清楚：任何时候一眼看出遥控器焦点在哪、按返回会去哪。
4. 少一步：常用操作（播放、换清晰度、跳过）路径最短。
5. 快于一切：按下确认到画面动起来要尽可能短，等待时永远有内容可看，不出现黑屏。

## Accessibility & Inclusion
10 英尺可读性：正文字号不小于电视安全尺寸，焦点态高对比，内容在 overscan 安全区内。
