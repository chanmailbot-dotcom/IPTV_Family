package com.iptv.family.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Primary = Color(0xFF6C8CFF)
private val Secondary = Color(0xFF00D0B0)
private val Background = Color(0xFF0B0D12)
private val Surface = Color(0xFF13161D)
private val SurfaceContainer = Color(0xFF1A1E27)
private val SurfaceContainerHigh = Color(0xFF232837)
private val Error = Color(0xFFFF6B6B)

private val DarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = Color(0xFF00164D),
    secondary = Secondary,
    background = Background,
    onBackground = Color(0xFFEAEDF5),
    surface = Surface,
    onSurface = Color(0xFFEAEDF5),
    surfaceVariant = SurfaceContainerHigh,
    onSurfaceVariant = Color(0xFFAAB2C5),
    error = Error,
    onError = Color(0xFF3B0A0A),
    errorContainer = Color(0xFF4A1616),
    onErrorContainer = Color(0xFFFFD9D9),
)

@Composable
fun IptvFamilyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}

// Superficies elevadas que Material3 deriva automaticamente en desktop pero que
// aqui se fijan a mano para mantener el mismo aspecto "capas" en TV.
val AndroidSurfaceContainer = SurfaceContainer
val AndroidSurfaceContainerHigh = SurfaceContainerHigh

/**
 * Color del foco de mando a distancia. Con azul-sobre-azul-oscuro (el resto del
 * tema) el resaltado de seleccion no se distinguia; este ambar es el color que
 * mas contraste da contra toda la paleta fria de la app, en cualquier fondo.
 */
val TvFocusColor = Color(0xFFFFC02E)
