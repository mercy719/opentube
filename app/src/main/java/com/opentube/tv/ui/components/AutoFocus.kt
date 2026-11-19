package com.opentube.tv.ui.components

import androidx.compose.runtime.compositionLocalOf

/** 焦点在导航栏上浏览时，页面内容不得自动抢焦点 */
val LocalAllowAutoFocus = compositionLocalOf { true }

/** 播放引擎（由 AppRoot 提供）：浏览页用它做热区预热和大图预览 */
val LocalPlayer = androidx.compose.runtime.staticCompositionLocalOf<com.opentube.tv.player.OtPlayer?> { null }
