# OpenTube 设计系统：接触印样（Contact Sheet）

> 来源：35mm 胶片接触印样——黑色片基、片边印字、剪辑师用红色油性笔圈出选中的那一格。
> 世界只贡献四样东西：配色、字体、密度、一个签名动作。导航和控件都按电视标准来。

代码位置：`app/src/main/java/com/opentube/tv/ui/theme/Theme.kt`（色板 `Film`、字体 `Type`、尺寸 `Dims`）。

## 颜色

| Token | 值 | 用途 |
|---|---|---|
| `Film.Base` | `#0F0D0B` | 片基黑，全屏底色 |
| `Film.Rebate` | `#1B1815` | 片边色带：内容行、设置行、侧边面板 |
| `Film.Seam` | `#2A2621` | 1px 细缝、静止的次级控件底、时间线轨道 |
| `Film.Raised` | `#26221D` | 获焦的非内容控件底 |
| `Film.Edge` | `#E9E0CC` | 主文字、主按钮底 |
| `Film.Edge2` | `#E9E0CC` 65% | 次级文字 |
| `Film.Edge3` | `#E9E0CC` 45% | 帧号、已缓冲 |
| `Film.Grease` | `#E2362B` | **只用于**焦点圈、SponsorBlock 剪切 X、直播标记、破坏性操作文字 |
| `Film.Amber` | `#F0AD3A` | **只用于**观看进度、播放进度、“已看 x%”、加载线、选中项圆点 |

SponsorBlock 分类线色（来自地铁线路图的分色纪律）：赞助 `#4CB782`、片头/片尾 `#5AA7E8`、自我推广 `#E8C547`、求关注 `#C77DE8`、非音乐 `#F08A4B`、预告 `#6FD0D0`。

规则：不用阴影；层次靠底色台阶与 1px 细缝。红色和琥珀不做装饰。

## 字体

- 中文与正文：系统 Noto Sans CJK（`FontFamily.Default`）。
- 数字、帧号、时长、片边印字、登录码：Barlow Semi Condensed（Medium / SemiBold / Bold，OFL，打包在 `res/font/`）。
- 字号阶梯（sp，1080p 电视 = 960×540dp）：HeroTitle 30 / ScreenTitle 24 / RowTitle 15 / Body 14 / CardTitle 13 / Meta 13 / Label 11 / FrameNo 11 / BigCode 56。

## 布局

- 安全区：左右 48dp、上下 27dp。
- 导航栏 64dp 宽，焦点移入即切换标签；焦点在导航栏时页面内容不得自动抢焦点（`LocalAllowAutoFocus`）。
- 帧（视频卡）：184×103.5dp，间距 14dp，**全应用唯一的卡片尺寸与基线**。帧下：帧号（左）+ 频道名（右）+ 两行标题。
- 首屏：上方 230dp 高的焦点大图 + 片名/事实；下方胶片行，焦点行对齐列表顶部。
- 横向滚动只在获焦项进入两侧 48dp 边距时发生，且滚动量最小（`PivotSpec(marginPx)`）。
- 订阅 / 历史：长列表按 12 帧切成多条胶片，行名带范围，如“订阅更新 13–24”。

## 签名动作

1. **红笔圈选**（`Modifier.greasePencil`）：任何可获焦元素获焦时，用 240ms 一笔画出手绘红框；每个元素的笔迹由 key 决定，收笔越过起点。全应用统一：焦点 = 红笔。
2. **剪辑时间线**：播放进度条上方用分类色画出 SponsorBlock 片段，已越过（已剪掉）的片段打红色 X；跳过时左上角提示“已剪掉赞助片段 0:45”。

## 动效

- 红笔绘入 240ms，`CubicBezier(0.2, 0, 0, 1)`。
- 大图切换 220ms 交叉淡化；焦点停留 180ms 才换图，450ms 开始预取播放信息。
- 加载：琥珀光带在细缝上往返（1.1s）。不使用旋转菊花。
- 播放控制层 4 秒无操作自动淡出。

## 状态

- 加载 / 空 / 错误统一用 `StatusPanel`：标题说状态，正文说下一步，必要时给一个主按钮。
- 播放起播前：全屏封面 + 渐变遮罩 + 标题 + 加载线，绝不黑屏；出错时同一版式显示原因和“重试”。

## 资源来源

- 字体：Barlow Semi Condensed，Google Fonts（SIL OFL 1.1）。
- 图标：Material Icons Extended。
- TV Banner：`res/drawable/banner.xml` 矢量占位，待替换为正式图标。
- 应用内无其他位图资源；所有缩略图来自 YouTube（`i.ytimg.com`）。
