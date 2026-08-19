package com.iptv.family.shared.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF1E88E5),
    primaryContainer = Color(0xFF1565C0),
    secondary = Color(0xFF039BE5),
    secondaryContainer = Color(0xFF0277BD),
    tertiary = Color(0xFF43A047),
    tertiaryContainer = Color(0xFF2E7D32),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF1E1E1E),
    background = Color(0xFF0F0F0F),
    error = Color(0xFFEF5350),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB0B0B0),
    onBackground = Color.White,
    onError = Color.White,
    outline = Color(0xFF333333),
    outlineVariant = Color(0xFF444444),
    shadow = Color.Black,
    scrim = Color.Black,
    inverseSurface = Color(0xFFE0E0E0),
    inverseOnSurface = Color(0xFF121212),
    inversePrimary = Color(0xFF1E88E5)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1E88E5),
    primaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFF039BE5),
    secondaryContainer = Color(0xFFB3E5FC),
    tertiary = Color(0xFF43A047),
    tertiaryContainer = Color(0xFFC8E6C9),
    surface = Color.White,
    surfaceVariant = Color(0xFFF5F5F5),
    background = Color(0xFFFAFAFA),
    error = Color(0xFFC62828),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onSurface = Color(0xFF121212),
    onSurfaceVariant = Color(0xFF444444),
    onBackground = Color(0xFF121212),
    onError = Color.White,
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE0E0E0),
    shadow = Color.Black,
    scrim = Color.Black,
    inverseSurface = Color(0xFF121212),
    inverseOnSurface = Color.White,
    inversePrimary = Color(0xFF1E88E5)
)

@Composable
fun IPTVFamilyTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}