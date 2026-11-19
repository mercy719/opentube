package com.opentube.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opentube.tv.ui.theme.Film

/**
 * 获焦高亮：一圈简洁的暖白描边，和元素之间留一道缝，圆角随元素。
 * corner = 元素自身的圆角；描边圆角 = corner + 外扩量，保持同心。
 */
fun Modifier.focusRing(focused: Boolean, outset: Dp = 3.dp, corner: Dp = 6.dp, stroke: Dp = 2.5.dp): Modifier = composed {
    val a by animateFloatAsState(if (focused) 1f else 0f, tween(if (focused) 120 else 80), label = "ring")
    drawWithContent {
        drawContent()
        if (a <= 0f) return@drawWithContent
        val w = stroke.toPx()
        val o = outset.toPx() + w / 2
        val r = corner.toPx() + o
        drawRoundRect(
            color = Film.Edge.copy(alpha = a),
            topLeft = Offset(-o, -o),
            size = Size(size.width + 2 * o, size.height + 2 * o),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = w),
        )
    }
}
