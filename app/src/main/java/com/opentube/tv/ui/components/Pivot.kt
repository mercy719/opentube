package com.opentube.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * 获焦项滚入视野的规则。
 * - margin > 0：只在获焦项进入两侧 margin 区时才滚，滚动量最小（左对齐 margin / 右对齐 margin），
 *   这样不会露出上一格被截断的半张卡。
 * - margin == null：固定把获焦项放在容器的 parentFraction 处。
 */
@OptIn(ExperimentalFoundationApi::class)
class PivotSpec(
    private val parentFraction: Float = 0f,
    private val marginPx: Float? = null,
    /** 固定模式下额外往下留的距离：例如把胶片行的页眉和获焦红框一起留在视野里 */
    private val leadPx: Float = 0f,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val m = marginPx ?: return offset - parentFraction * containerSize - leadPx
        return when {
            offset < m -> offset - m
            offset + size > containerSize - m -> (offset + size) - (containerSize - m)
            else -> 0f
        }
    }
}
