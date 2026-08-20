package com.iptv.family.desktop.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF5B8DEF)
private val AccentDark = Color(0xFF2F6BD8)

private val Dark = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF06111F),
    primaryContainer = Color(0xFF1B3A63),
    onPrimaryContainer = Color(0xFFD3E3FF),
    secondary = Color(0xFF22D3EE),
    onSecondary = Color(0xFF04222A),
    tertiary = Color(0xFFF472B6),
    onTertiary = Color(0xFF2B0517),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE4E8F1),
    surface = Color(0xFF11151E),
    onSurface = Color(0xFFE4E8F1),
    surfaceVariant = Color(0xFF1B2130),
    onSurfaceVariant = Color(0xFFA9B2C6),
    surfaceContainer = Color(0xFF161B27),
    surfaceContainerHigh = Color(0xFF1B2130),
    surfaceContainerHighest = Color(0xFF222939),
    outline = Color(0xFF394154),
    outlineVariant = Color(0xFF2A3143),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2B0000),
    errorContainer = Color(0xFF4A1414),
    onErrorContainer = Color(0xFFFFD9D9),
)

private val Light = lightColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E5FF),
    onPrimaryContainer = Color(0xFF0B2A55),
    secondary = Color(0xFF0E7490),
    onSecondary = Color.White,
    tertiary = Color(0xFFBE185D),
    onTertiary = Color.White,
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF161A22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF161A22),
    surfaceVariant = Color(0xFFE6EAF2),
    onSurfaceVariant = Color(0xFF4A5468),
    surfaceContainer = Color(0xFFEFF2F8),
    surfaceContainerHigh = Color(0xFFE6EAF2),
    surfaceContainerHighest = Color(0xFFDDE3ED),
    outline = Color(0xFFC3CAD8),
    outlineVariant = Color(0xFFD8DEE9),
    error = Color(0xFFC62828),
    onError = Color.White,
)

enum class AppThemeMode { LIGHT, DARK }

@Composable
fun AppTheme(mode: AppThemeMode = AppThemeMode.DARK, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (mode == AppThemeMode.LIGHT) Light else Dark,
        content = content,
    )
}
