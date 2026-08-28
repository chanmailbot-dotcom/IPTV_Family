package com.iptv.family.desktop.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.iptv.family.shared.model.ThemeType

// Mismos tonos que el icono de la app (badge degradado indigo -> verde azulado):
// mantiene una sola identidad de marca entre el icono, escritorio y Android.
private val Accent = Color(0xFF6C8CFF)
private val AccentDark = Color(0xFF3D5BDB)
private val BrandTeal = Color(0xFF00D0B0)
private val BrandTealDark = Color(0xFF00937B)

private val Dark = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF06111F),
    primaryContainer = Color(0xFF1B3A63),
    onPrimaryContainer = Color(0xFFD3E3FF),
    secondary = BrandTeal,
    onSecondary = Color(0xFF04221C),
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
    secondary = BrandTealDark,
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

/**
 * Tema de la aplicacion.
 *
 * Acepta el [ThemeType] compartido (LIGHT / DARK / SYSTEM) y resuelve SYSTEM
 * en tiempo de composicion mirando el tema del escritorio con
 * [isSystemInDarkTheme]. Como `settings.selectedTheme` es estado de Compose y
 * llega como parametro, cambiar el selector en Ajustes recompone al instante.
 */
@Composable
fun AppTheme(
    theme: ThemeType = ThemeType.DARK,
    content: @Composable () -> Unit,
) {
    val mode = when (theme) {
        ThemeType.LIGHT -> AppThemeMode.LIGHT
        ThemeType.DARK -> AppThemeMode.DARK
        ThemeType.SYSTEM -> if (isSystemInDarkTheme()) AppThemeMode.DARK else AppThemeMode.LIGHT
    }
    MaterialTheme(
        colorScheme = if (mode == AppThemeMode.LIGHT) Light else Dark,
        content = content,
    )
}
