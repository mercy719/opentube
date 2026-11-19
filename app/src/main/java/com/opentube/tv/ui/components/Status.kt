package com.opentube.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.opentube.tv.ui.theme.Type

/** 加载 / 空 / 错误：同一种安静的版式，标题说清状态，正文说清下一步 */
@Composable
fun StatusPanel(
    title: String,
    body: String?,
    loading: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(title, style = Type.ScreenTitle)
            if (body != null) { Spacer(Modifier.height(10.dp)); Text(body, style = Type.Body1.copy(color = Type.Meta.color)) }
            if (loading) { Spacer(Modifier.height(20.dp)); LoadingLine(Modifier.width(160.dp)) }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(24.dp))
                val fr = remember { FocusRequester() }
                PillButton(actionLabel, onClick = onAction, modifier = Modifier.focusRequester(fr))
                val allow = LocalAllowAutoFocus.current
                LaunchedEffect(Unit) { if (allow) runCatching { fr.requestFocus() } }
            }
        }
    }
}
