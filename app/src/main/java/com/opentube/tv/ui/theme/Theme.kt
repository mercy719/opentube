package com.opentube.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.opentube.tv.R

/**
 * 接触印样（Contact Sheet）世界的色板。
 * 红色只给焦点圈与剪切标记；琥珀只给进度与“已看”。其余都是片基与片边印字。
 */
object Film {
    val Base = Color(0xFF0F0D0B)        // 片基黑：整屏底色
    val Rebate = Color(0xFF1B1815)      // 片边色带：内容行
    val Seam = Color(0xFF2A2621)        // 1px 细缝与静止控件底
    val Raised = Color(0xFF26221D)      // 获焦的非内容控件底
    val Edge = Color(0xFFE9E0CC)        // 片边印字：主文字
    val Edge2 = Color(0xA6E9E0CC)       // 次级文字（≈65%，在片基上对比度 > 7:1）
    val Edge3 = Color(0x73E9E0CC)       // 三级：帧号等
    val Grease = Color(0xFFE2362B)      // 油性笔红
    val Amber = Color(0xFFF0AD3A)       // 安全灯琥珀
    val Scrim = Color(0xE60F0D0B)

    /** SponsorBlock 分类线色（来自地铁线路图的分色纪律） */
    fun segmentColor(category: String): Color = when (category) {
        "sponsor" -> Color(0xFF4CB782)
        "intro", "outro" -> Color(0xFF5AA7E8)
        "selfpromo" -> Color(0xFFE8C547)
        "interaction" -> Color(0xFFC77DE8)
        "music_offtopic" -> Color(0xFFF08A4B)
        "preview" -> Color(0xFF6FD0D0)
        else -> Edge2
    }
}

object Type {
    /** 数字、帧号、时长、片边印字 */
    val Numerals = FontFamily(
        Font(R.font.barlow_sc_medium, FontWeight.Medium),
        Font(R.font.barlow_sc_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_sc_bold, FontWeight.Bold),
    )
    /** 中文走系统 Noto Sans CJK */
    val Body = FontFamily.Default

    val HeroTitle = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, color = Film.Edge)
    val ScreenTitle = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, color = Film.Edge)
    val RowTitle = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp, color = Film.Edge)
    val Body1 = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = Film.Edge)
    val CardTitle = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = Film.Edge)
    val Meta = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, color = Film.Edge2)
    val Label = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, color = Film.Edge2)
    val EdgePrint = TextStyle(fontFamily = Numerals, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.06.sp, color = Film.Edge2)
    val FrameNo = TextStyle(fontFamily = Numerals, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = Film.Edge3)
    val Duration = TextStyle(fontFamily = Numerals, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Film.Edge)
    val Facts = TextStyle(fontFamily = Numerals, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Film.Edge2)
    val Button = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Film.Base)
    val BigCode = TextStyle(fontFamily = Numerals, fontWeight = FontWeight.Bold, fontSize = 56.sp, letterSpacing = 4.sp, color = Film.Edge)
}

/** 布局常量（dp，1080p 电视 = 960×540dp） */
object Dims {
    val SafeH = 48.dp
    val SafeV = 27.dp
    val RailWidth = 64.dp
    val FrameWidth = 184.dp
    val FrameHeight = 103.5.dp
    val FrameGap = 14.dp
}

@Composable
fun OpenTubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Film.Edge, onPrimary = Film.Base,
            background = Film.Base, onBackground = Film.Edge,
            surface = Film.Base, onSurface = Film.Edge,
            surfaceVariant = Film.Seam, onSurfaceVariant = Film.Edge2,
            border = Film.Seam, error = Film.Grease,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides Film.Edge, content = content)
    }
}
