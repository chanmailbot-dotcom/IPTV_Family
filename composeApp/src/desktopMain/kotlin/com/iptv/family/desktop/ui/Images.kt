package com.iptv.family.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.ui.graphics.awt.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO

/**
 * Carga asíncronamente una imagen desde una URL (logo de canal / EPG) a un
 * [ImageBitmap] usable por Compose. Sin dependencias externas (Coil) usando AWT.
 */
@Composable
fun rememberRemoteImageBitmap(url: String?): ImageBitmap? {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = null
        if (!url.isNullOrBlank()) {
            try {
                val img = withContext(Dispatchers.IO) { ImageIO.read(URL(url)) }
                bitmap = (img as? BufferedImage)?.toComposeImageBitmap()
            } catch (e: Exception) {
                bitmap = null
            }
        }
    }
    return bitmap
}
