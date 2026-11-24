package com.luogen.filehub.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 浅色主题配色（默认）
val BrandBlue = Color(0xFF2563EB)
val BrandBlueDeep = Color(0xFF1D4ED8)
val BgDeep = Color(0xFFF5F7FB)          // 页面底色（浅灰蓝）
val SurfaceDark = Color(0xFFFFFFFF)     // 卡片/列表底色（白）
val SurfaceDark2 = Color(0xFFEDF1F8)    // 输入框/进度轨道
val TextDim = Color(0xFF6B778C)
val GoodGreen = Color(0xFF0E9F6E)
val WarnAmber = Color(0xFFD97706)
val DangerRed = Color(0xFFDC2626)
val TextMain = Color(0xFF101828)
val BorderSoft = Color(0xFFE4E9F2)

private val scheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = BrandBlueDeep,
    secondary = Color(0xFF0E9F6E),
    onSecondary = Color.White,
    background = BgDeep,
    onBackground = TextMain,
    surface = SurfaceDark,
    onSurface = TextMain,
    surfaceVariant = SurfaceDark2,
    onSurfaceVariant = TextDim,
    outline = BorderSoft,
    error = DangerRed
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, shapes = shapes, content = content)
}
