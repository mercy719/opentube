package com.opentube.tv.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.opentube.tv.ui.components.focusRing
import com.opentube.tv.ui.theme.Dims
import com.opentube.tv.ui.theme.Film
import com.opentube.tv.ui.theme.Type

enum class Tab(val label: String, val icon: ImageVector) {
    Home("首页", Icons.Outlined.Home),
    Subscriptions("订阅", Icons.Outlined.Subscriptions),
    Search("搜索", Icons.Outlined.Search),
    History("历史", Icons.Outlined.History),
    Settings("设置", Icons.Outlined.Settings),
}

/** 窄导航栏：焦点移入即切换（电视上少按一次确认） */
@Composable
fun NavRail(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxHeight().width(Dims.RailWidth).background(Film.Base).padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Tab.entries.forEach { tab ->
            var focused by remember { mutableStateOf(false) }
            val selected = tab == current
            Surface(
                onClick = { onSelect(tab) },
                modifier = Modifier
                    .onFocusChanged { focused = it.isFocused; if (it.isFocused && !selected) onSelect(tab) }
                    .focusRing(focused, outset = 1.5.dp, stroke = 2.dp),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                colors = ClickableSurfaceDefaults.colors(containerColor = Film.Base, focusedContainerColor = Film.Raised),
            ) {
                Column(Modifier.width(52.dp).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(30.dp).background(if (selected) Film.Edge else Film.Base, RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(tab.icon, null, tint = if (selected) Film.Base else Film.Edge2, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(tab.label, style = Type.Label.copy(color = if (selected || focused) Film.Edge else Film.Edge2))
                }
            }
        }
    }
}
