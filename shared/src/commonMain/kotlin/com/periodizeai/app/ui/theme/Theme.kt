package com.periodizeai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JuggernautRed    = Color(0xFFD32F2F)
private val JuggernautRedDim = Color(0xFF9A0007)
private val SurfaceDark      = Color(0xFF121212)
private val SurfaceVariant   = Color(0xFF1E1E1E)
private val OnSurfaceDark    = Color(0xFFE0E0E0)
private val OutlineDark      = Color(0xFF3A3A3A)

private val DarkColorScheme = darkColorScheme(
    primary          = JuggernautRed,
    onPrimary        = Color.White,
    primaryContainer = JuggernautRedDim,
    secondary        = Color(0xFFFF8A65),
    background       = SurfaceDark,
    surface          = SurfaceVariant,
    onSurface        = OnSurfaceDark,
    onBackground     = OnSurfaceDark,
    outline          = OutlineDark,
    error            = Color(0xFFCF6679),
)

private val LightColorScheme = lightColorScheme(
    primary          = JuggernautRed,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFFFCDD2),
    secondary        = Color(0xFFE64A19),
    background       = Color(0xFFFAFAFA),
    surface          = Color.White,
    onSurface        = Color(0xFF1C1C1E),
    onBackground     = Color(0xFF1C1C1E),
    outline          = Color(0xFFD1D1D1),
    error            = Color(0xFFB00020),
)

@Composable
fun PeriodizeAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography  = Typography,
        content     = content,
    )
}
