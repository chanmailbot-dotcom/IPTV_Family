package com.iptv.family.desktop.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    secondary = Color(0xFF03DAC6),
    background = Color(0xFF0D1015),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF15181E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2A2E35),
    onSurfaceVariant = Color(0xFFC0C0C0),
    error = Color(0xFFFF5252),
    onError = Color.Black,
)

private val Light = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    secondary = Color(0xFF03DAC6),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF1F1F1F),
)

enum class AppThemeMode { LIGHT, DARK }

@Composable
fun AppTheme(mode: AppThemeMode = AppThemeMode.DARK, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (mode == AppThemeMode.LIGHT) Light else Dark,
        content = content,
    )
}
