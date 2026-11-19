package com.opentube.tv.ui.components

import androidx.compose.runtime.compositionLocalOf

/** 焦点在导航栏上浏览时，页面内容不得自动抢焦点 */
val LocalAllowAutoFocus = compositionLocalOf { true }

/** 焦点停留在某个视频上足够久时调用：后台预热播放（由 AppRoot 提供） */
val LocalPreload = androidx.compose.runtime.staticCompositionLocalOf<(com.liskovsoft.mediaserviceinterfaces.data.MediaItem) -> Unit> { {} }
