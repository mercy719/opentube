package com.opentube.tv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.composed
import com.opentube.tv.ui.theme.Film
import kotlin.random.Random

private val Draw = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * 签名动作：剪辑师用油性红笔圈住选中的那一格。
 * 每个 key 有自己固定的笔迹（确定性抖动），获焦时用 240ms 一笔画出，起笔处略微越过收笔点。
 */
fun Modifier.greasePencil(focused: Boolean, key: Any, outset: Dp = 7.dp, stroke: Dp = 2.5.dp): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(focused) {
        if (focused) { progress.snapTo(0f); progress.animateTo(1f, tween(240, easing = Draw)) }
        else progress.snapTo(0f)
    }
    val seed = remember(key) { key.hashCode() }
    drawWithCache {
        val o = outset.toPx()
        val path = scribbleRect(Size(size.width + 2 * o, size.height + 2 * o), seed).apply { translate(androidx.compose.ui.geometry.Offset(-o, -o)) }
        val measure = PathMeasure().apply { setPath(path, false) }
        val len = measure.length
        val main = Stroke(width = stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val wax = Stroke(width = stroke.toPx() * 0.45f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        onDrawWithContent {
            drawContent()
            val p = progress.value
            if (p <= 0f) return@onDrawWithContent
            val seg = Path()
            measure.getSegment(0f, len * p, seg, true)
            drawPath(seg, Film.Grease, style = main)
            // 第二道细笔：蜡笔在纸上留下的不均匀边
            translate(0.6f * density, -0.5f * density) { drawPath(seg, Film.Grease.copy(alpha = 0.45f), style = wax) }
        }
    }
}

/** 手绘矩形：四条略弯的边，角点有轻微错位，收笔越过起点 */
private fun scribbleRect(s: Size, seed: Int): Path {
    val r = Random(seed)
    fun j(m: Float) = (r.nextFloat() - 0.5f) * 2f * m
    val w = s.width; val h = s.height
    val jit = minOf(w, h) * 0.03f
    val p0 = androidx.compose.ui.geometry.Offset(j(jit) + w * 0.02f, j(jit))
    val p1 = androidx.compose.ui.geometry.Offset(w + j(jit), j(jit))
    val p2 = androidx.compose.ui.geometry.Offset(w + j(jit), h + j(jit))
    val p3 = androidx.compose.ui.geometry.Offset(j(jit), h + j(jit))
    val end = androidx.compose.ui.geometry.Offset(w * 0.12f + j(jit), j(jit) - jit * 0.8f)
    return Path().apply {
        moveTo(p0.x, p0.y)
        quadraticTo((p0.x + p1.x) / 2 + j(jit), (p0.y + p1.y) / 2 + j(jit * 1.4f), p1.x, p1.y)
        quadraticTo((p1.x + p2.x) / 2 + j(jit * 1.4f), (p1.y + p2.y) / 2 + j(jit), p2.x, p2.y)
        quadraticTo((p2.x + p3.x) / 2 + j(jit), (p2.y + p3.y) / 2 + j(jit * 1.4f), p3.x, p3.y)
        quadraticTo((p3.x + p0.x) / 2 + j(jit * 1.4f), (p3.y + p0.y) / 2 + j(jit), p0.x - jit * 0.4f, p0.y + jit * 0.3f)
        // 越过起点再走一小段，像真的手画收不住笔
        quadraticTo((p0.x + end.x) / 2, p0.y - jit * 0.6f, end.x, end.y)
    }
}
