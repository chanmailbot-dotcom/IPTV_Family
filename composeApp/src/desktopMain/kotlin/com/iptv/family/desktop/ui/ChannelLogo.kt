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
import com.iptv.family.shared.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.imageio.ImageIO

/**
 * Cache de logos de canal en dos niveles: memoria (LRU de 400 entradas) y
 * disco (`~/.iptv-family/logos`). Las listas IPTV traen miles de logos, asi
 * que con la capa de disco solo se descargan una vez aunque la app se
 * reinicie; los fallos se recuerdan en memoria para no reintentar en cada
 * scroll, y la cache de disco se recorta por tamano para no crecer sin fin.
 */
private object LogoCache {
    private const val MAX = 400
    private const val DISK_MAX_BYTES = 256L * 1024 * 1024
    private val lock = Any()
    private val cache = object : LinkedHashMap<String, ImageBitmap?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>) = size > MAX
    }
    private val diskDir: File by lazy {
        File(File(System.getProperty("user.home"), ".iptv-family"), "logos").apply { mkdirs() }
    }
    private var diskWrites = 0

    fun cached(url: String): Result? = synchronized(lock) {
        if (cache.containsKey(url)) Result(cache[url]) else null
    }

    suspend fun load(url: String): ImageBitmap? {
        cached(url)?.let { return it.bitmap }
        val bitmap = withContext(Dispatchers.IO) { fetch(url) }
        synchronized(lock) { cache[url] = bitmap }
        return bitmap
    }

    /** Memoria -> disco -> red. Solo se persiste lo que se puede decodificar. */
    private fun fetch(url: String): ImageBitmap? {
        diskFileFor(url)?.takeIf { it.isFile }?.let { file ->
            val fromDisk = decode(file)
            if (fromDisk != null) return fromDisk
            file.delete() // corrupto o ilegible: se vuelve a descargar
        }
        val bytes = download(url) ?: return null
        val bitmap = decodeBytes(bytes) ?: return null
        writeToDisk(url, bytes)
        return bitmap
    }

    private fun download(url: String): ByteArray? = try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "IPTV-Family/1.0")
        }
        try {
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    private fun decode(file: File): ImageBitmap? = try {
        ImageIO.read(file)?.toComposeImageBitmap()
    } catch (e: Exception) {
        null
    }

    private fun decodeBytes(bytes: ByteArray): ImageBitmap? = try {
        ImageIO.read(ByteArrayInputStream(bytes))?.toComposeImageBitmap()
    } catch (e: Exception) {
        null
    }

    /** Nombre estable por URL: SHA-256 + extension si la URL la aporta. */
    private fun diskFileFor(url: String): File? = try {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        val hash = digest.joinToString("") { "%02x".format(it) }
        val ext = Regex("\\.(png|jpe?g|gif|bmp)($|[?#])", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.get(1)?.lowercase()?.let { ".$it" } ?: ".img"
        File(diskDir, "$hash$ext")
    } catch (e: Exception) {
        null
    }

    /** Escritura atomica: fichero temporal + renombrado. */
    private fun writeToDisk(url: String, bytes: ByteArray) {
        val target = diskFileFor(url) ?: return
        runCatching {
            val tmp = File(diskDir, "${target.name}.tmp")
            tmp.writeBytes(bytes)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) tmp.delete()
            if (++diskWrites % 400 == 0) trimDisk()
        }
    }

    /** Si la cache de disco supera el tope, elimina los ficheros mas viejos. */
    private fun trimDisk() {
        val files = diskDir.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= DISK_MAX_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= DISK_MAX_BYTES / 2) break
            val size = file.length()
            if (file.delete()) total -= size
        }
        AppLog.d("Logos", "trimDisk: cache reducida a ~${total / 1024} KB")
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
