package com.opentube.tv.ui.components

import androidx.compose.runtime.compositionLocalOf

/** 焦点在导航栏上浏览时，页面内容不得自动抢焦点 */
val LocalAllowAutoFocus = compositionLocalOf { true }
