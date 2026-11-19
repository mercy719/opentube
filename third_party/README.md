# third_party

这里是从 [SmartTube](https://github.com/yuliskov/SmartTube)（MIT License）中 vendored 进来的模块，
OpenTube 复用其 YouTube 后端与播放内核，UI 层完全重写。

| 目录 | 来源 | 上游 commit |
|---|---|---|
| `MediaServiceCore/` | yuliskov/MediaServiceCore | `b91d170de9b6a402795603bc5ec3b45fb93f0fab` |
| `SharedModules/` | yuliskov/SharedModules | `5475d31808139e21c0e55f4d3462481002afae63` |
| `exoplayer/` | SmartTube 仓库内 `exoplayer-amzn-2.10.6`（含 SABR 模块） | SmartTube `6a1ec672d67373346906cec2d0ba23d9682c111e` |

## OpenTube 对 vendored 代码的改动

为了在 AGP 8 / Kotlin 2 / Apple Silicon 下构建，做了以下最小改动（代码中以 `OPENTUBE:` 标注）：

1. 删除各模块 `AndroidManifest.xml` 中的 `package` 属性（AGP 8 不再支持），namespace 改在根 `build.gradle` 的 `vendoredNamespaces` 中设置。
2. ExoPlayer 模块：注释掉对 testutils 的测试依赖，以及 javadoc / publish 脚本。
3. `exoplayer/constants.gradle`：`protobufVersion` 由 3.17.3 升到 3.25.5（旧版 protoc 没有 arm64 原生二进制）。
4. 删除了所有 `src/test`、`src/androidTest`、demo 以及未使用的 ExoPlayer 扩展和 SharedModules 子模块。
5. 根 `build.gradle` 覆盖了部分常量：Kotlin 2.0.21、compileSdk 35。okhttp 必须保持上游的 3.12.x，后端多个库依赖其内部 API，升级到 4.x 会在运行时失败。

同步上游时，请重新应用以上改动。
- `CommentsService.setLikeObserve(item, like)`（接口 + `YouTubeCommentsService` + `CommentsServiceInt.setLike`）：用评论自带的 like/unlike 参数直接点赞，原来的 `toggleLikeObserve(nestedKey)` 只对有回复的评论有效。
- `OkHttpCommons.enableDebugInterceptors = false`：debug 构建下不再挂 BODY 日志和 OkHttpProfiler。它们会把整段响应（包括视频分片）读进内存再打印，导致起播极慢。
- SABR 退避只执行一次（`SabrProcessor.clearBackoff` / `SabrStream.consumeBackoffTimeMs`），重定向到新服务器时清除旧退避。原实现会在之后每个请求结束时重复等待同样时长（起播时多等约 4 秒）。
