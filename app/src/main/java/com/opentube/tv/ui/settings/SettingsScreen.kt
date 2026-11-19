package com.opentube.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.opentube.tv.BuildConfig
import com.opentube.tv.data.Settings
import com.opentube.tv.data.YouTube
import com.opentube.tv.ui.components.focusRing
import com.opentube.tv.ui.theme.Dims
import com.opentube.tv.ui.theme.Film
import com.opentube.tv.ui.theme.Type

/** 设置只有四行。按确认在选项间循环，不弹二级菜单 */
@Composable
fun SettingsScreen(onSignIn: () -> Unit) {
    val ctx = LocalContext.current
    val settings = remember { Settings.get(ctx) }
    val sponsor by settings.sponsorBlock.collectAsState()
    val maxH by settings.maxHeight.collectAsState()
    var accountVersion by remember { mutableIntStateOf(0) }
    val account = remember(accountVersion) { runCatching { YouTube.signIn.selectedAccount }.getOrNull() }

    Column(Modifier.fillMaxSize().padding(horizontal = Dims.SafeH, vertical = Dims.SafeV)) {
        Text("设置", style = Type.ScreenTitle)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            SettingRow("账号", account?.name ?: account?.email ?: "未登录", onClick = if (account == null) onSignIn else null)
            SettingRow("画质上限", when (maxH) { 0 -> "自动（随网速）"; else -> "最高 ${maxH}p" }) {
                settings.setMaxHeight(when (maxH) { 0 -> 1080; 1080 -> 720; else -> 0 })
            }
            SettingRow("SponsorBlock 自动跳过", if (sponsor) "开" else "关") { settings.setSponsorBlock(!sponsor) }
            SettingRow("版本", "OpenTube ${BuildConfig.VERSION_NAME}", onClick = null)
        }
        if (account != null) {
            // 破坏性操作单独隔开，不和常用项挤在一起
            Spacer(Modifier.height(56.dp))
            SettingRow("退出登录", account.name ?: "", danger = true, modifier = Modifier.width(520.dp)) {
                YouTube.signIn.selectAccount(null); accountVersion++
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, danger: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth(), onClick: (() -> Unit)?) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = { onClick?.invoke() },
        modifier = modifier.onFocusChanged { focused = it.isFocused }.focusRing(focused, outset = 3.dp, corner = 3.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(3.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Film.Rebate, focusedContainerColor = Film.Raised),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.Body1.copy(color = if (danger) Film.Grease else Film.Edge))
            Spacer(Modifier.weight(1f))
            Text(value, style = Type.Body1.copy(color = Film.Edge2))
        }
    }
}
