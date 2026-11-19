package com.opentube.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.opentube.tv.ui.theme.Film
import com.opentube.tv.ui.theme.Type

/** 主操作：米白实心药丸；获焦时被红笔圈住 */
@Composable
fun PillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, primary: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused }.focusRing(focused, outset = 3.dp, corner = 100.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Film.Edge else Film.Seam,
            focusedContainerColor = if (primary) Film.Edge else Film.Raised,
            contentColor = if (primary) Film.Base else Film.Edge,
            focusedContentColor = if (primary) Film.Base else Film.Edge,
        ),
    ) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
            Text(label, style = Type.Button.copy(color = if (primary) Film.Base else Film.Edge))
        }
    }
}

/** 不确定进度：一段琥珀色光带在细缝上来回扫，像暗房安全灯下等待显影 */
@Composable
fun LoadingLine(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "load")
    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "x")
    Canvas(modifier.height(3.dp)) {
        drawLine(Film.Seam, Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = size.height, cap = StrokeCap.Round)
        val w = size.width * 0.3f
        val start = (size.width - w) * x
        drawLine(Film.Amber, Offset(start, center.y), Offset(start + w, center.y), strokeWidth = size.height, cap = StrokeCap.Round)
    }
}
