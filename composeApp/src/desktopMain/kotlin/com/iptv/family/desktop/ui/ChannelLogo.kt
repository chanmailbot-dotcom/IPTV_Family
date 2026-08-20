package com.iptv.family.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.imageio.ImageIO

/**
 * Cache de logos de canal. Las listas IPTV traen miles de entradas, asi que la
 * cache esta acotada y los fallos se recuerdan para no reintentar en cada scroll.
 *
 * ponytail: LRU con lock global y tope de 400 entradas. Si algun dia hace falta
 * mas, cache en disco bajo ~/.iptv-family/logos.
 */
private object LogoCache {
    private const val MAX = 400
    private val lock = Any()
    private val cache = object : LinkedHashMap<String, ImageBitmap?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>) = size > MAX
    }

    fun cached(url: String): Result? = synchronized(lock) {
        if (cache.containsKey(url)) Result(cache[url]) else null
    }

    suspend fun load(url: String): ImageBitmap? {
        cached(url)?.let { return it.bitmap }
        val bitmap = withContext(Dispatchers.IO) { fetch(url) }
        synchronized(lock) { cache[url] = bitmap }
        return bitmap
    }

    private fun fetch(url: String): ImageBitmap? = try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "IPTV-Family/1.0")
        }
        try {
            connection.inputStream.use { ImageIO.read(it) }?.toComposeImageBitmap()
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    /** Envoltorio para distinguir "no cacheado" de "cacheado como fallo". */
    class Result(val bitmap: ImageBitmap?)
}

/**
 * Logo del canal, o sus iniciales sobre un color derivado del nombre si la
 * lista no trae logo o la descarga falla.
 */
@Composable
fun ChannelLogo(
    logoUrl: String?,
    name: String,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(logoUrl) { mutableStateOf(logoUrl?.let { LogoCache.cached(it)?.bitmap }) }

    LaunchedEffect(logoUrl) {
        if (logoUrl != null && bitmap == null) bitmap = LogoCache.load(logoUrl)
    }

    Box(
        modifier
            .size(size)
            .background(
                if (bitmap != null) MaterialTheme.colorScheme.surfaceContainerHighest else initialsColor(name),
                MaterialTheme.shapes.small,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSizeMinusPadding(),
            )
        } else {
            Text(
                initials(name),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value / 3f).sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun Modifier.fillMaxSizeMinusPadding() = this.padding(4.dp)

private fun initials(name: String): String {
    val words = name.trim().split(' ', '-', '|').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/** Color estable por canal: el mismo nombre siempre da el mismo tono. */
private fun initialsColor(name: String): androidx.compose.ui.graphics.Color {
    val palette = listOf(
        0xFF2A4A7C, 0xFF1F5F5B, 0xFF5C3A6E, 0xFF6E4630,
        0xFF3B4B6B, 0xFF255A44, 0xFF6B3550, 0xFF44506B,
    )
    val index = (name.hashCode().toLong() and 0xFFFFFFFFL) % palette.size
    return androidx.compose.ui.graphics.Color(palette[index.toInt()])
}
