package com.iptv.family.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Theme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        androidx.compose.material3.darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF1DB9AA),
            onPrimary = androidx.compose.ui.graphics.Color.White,
            secondary = androidx.compose.ui.graphics.Color(0xFF1DB9AA),
            onSecondary = androidx.compose.ui.graphics.Color.White,
            background = androidx.compose.ui.graphics.Color(0xFF121212),
            onBackground = androidx.compose.ui.graphics.Color.White,
            surface = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
            onSurface = androidx.compose.ui.graphics.Color(0xFFE0E0E0),
            secondaryContainer = androidx.compose.ui.graphics.Color(0xFF2A2A2A),
            onSecondaryContainer = androidx.compose.ui.graphics.Color.White
        )
    } else {
        androidx.compose.material3.lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF1DB9AA),
            onPrimary = androidx.compose.ui.graphics.Color.White,
            secondary = androidx.compose.ui.graphics.Color(0xFF1DB9AA),
            onSecondary = androidx.compose.ui.graphics.Color.White,
            background = androidx.compose.ui.graphics.Color(0xFFF5F5F5),
            onBackground = androidx.compose.ui.graphics.Color(0xFF121212),
            surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
            onSurface = androidx.compose.ui.graphics.Color(0xFF121212)
        )
    }

    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier) {
            content()
        }
    }
}