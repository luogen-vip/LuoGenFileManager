package com.luogen.filehub.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandBlue = Color(0xFF3B82F6)
val BrandBlueDeep = Color(0xFF1D4ED8)
val BgDeep = Color(0xFF0B1220)
val SurfaceDark = Color(0xFF141C2B)
val SurfaceDark2 = Color(0xFF1B2536)
val TextDim = Color(0xFF9AA7BD)
val GoodGreen = Color(0xFF34D399)
val WarnAmber = Color(0xFFFBBF24)
val DangerRed = Color(0xFFF87171)

private val scheme = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueDeep,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF60A5FA),
    background = BgDeep,
    onBackground = Color(0xFFE8EDF6),
    surface = SurfaceDark,
    onSurface = Color(0xFFE8EDF6),
    surfaceVariant = SurfaceDark2,
    onSurfaceVariant = TextDim,
    error = DangerRed
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
