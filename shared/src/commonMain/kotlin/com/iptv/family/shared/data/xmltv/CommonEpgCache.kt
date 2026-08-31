package com.iptv.family.shared.data.xmltv

import com.iptv.family.shared.log.AppLog
import com.iptv.family.shared.model.EPGProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.PushbackInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Carga y cachea en memoria la guia XMLTV de la playlist activa, y resuelve
 * "que dan ahora" y "que dan despues" para un canal.
 *
 * - Descarga una vez y reutiliza: TTL de 6 horas (las guias pesan decenas de MB).
 * - Indexa por dos claves normalizadas del atributo `channel` para absorber
 *   diferencias tipicas entre el `tvg-id` del M3U y el `channel` del XMLTV.
 *   En Xtream el cruce es exacto (el parser usa el `stream_id`).
 * - Un fallo de descarga no borra la guia ya cargada.
 */
class CommonEpgCache(private val parser: XmltvParser = XmltvParser()) {

    private val mutex = Mutex()
    private var byChannel: Map<String, List<EPGProgram>> = emptyMap()
    private var loadedForUrl: String? = null
    private var loadedAtMs: Long = 0L

    val isLoaded: Boolean get() = loadedForUrl != null

    /** Descarga y parsea la guia si toca (primera vez, TTL vencido o refresh). */
    suspend fun ensureLoaded(url: String?, forceRefresh: Boolean = false) {
        val target = url?.trim()?.takeUnless { it.isEmpty() } ?: return
        mutex.withLock {
            if (!forceRefresh && loadedForUrl == target && !isStale()) return
            val result = withContext(Dispatchers.IO) { runCatching { fetchAndParse(target) } }
            result.fold(
                onSuccess = { programs ->
                    byChannel = index(programs)
                    loadedForUrl = target
                    loadedAtMs = System.currentTimeMillis()
                    AppLog.d("EPG", "Guia cargada: ${programs.size} programas")
                },
                onFailure = { e ->
                    AppLog.e("EPG", "No se pudo cargar la guia: ${e.message}", e)
                },
            )
        }
    }

    /** Programa que se esta emitiendo ahora para el canal (null si no hay guia). */
    fun currentFor(channelId: String?, nowMs: Long = System.currentTimeMillis()): EPGProgram? {
        val list = programsFor(channelId) ?: return null
        var current: EPGProgram? = null
        for (program in list) {
            if (program.startTime > nowMs) break
            // endTime 0 = la guia no declara fin: vale mientras no empiece otra cosa.
            if (program.endTime > nowMs || program.endTime <= 0L) current = program
        }
        return current
    }

    /** Siguiente programa del canal (null si no hay mas). */
    fun nextFor(channelId: String?, nowMs: Long = System.currentTimeMillis()): EPGProgram? =
        programsFor(channelId)?.firstOrNull { it.startTime > nowMs }

    private fun isStale() = System.currentTimeMillis() - loadedAtMs > TTL_MS

    private fun programsFor(channelId: String?): List<EPGProgram>? {
        if (channelId.isNullOrBlank()) return null
        val index = byChannel
        for (key in keysFor(channelId)) index[key]?.let { return it }
        return null
    }

    private fun index(programs: List<EPGProgram>): Map<String, List<EPGProgram>> {
        val out = HashMap<String, List<EPGProgram>>()
        for ((channelId, list) in programs.groupBy { it.channelId }) {
            val sorted = list.sortedBy { it.startTime }
            for (key in keysFor(channelId)) out[key] = sorted
        }
        return out
    }

    /** Claves de busqueda: el id tal cual (minusculas) y su version alfanumerica. */
    private fun keysFor(raw: String): List<String> = buildList {
        raw.lowercase().trim().takeIf { it.isNotEmpty() }?.let { add(it) }
        raw.lowercase().filter { it.isLetterOrDigit() }
            .takeIf { it.isNotEmpty() && it != raw.lowercase().trim() }?.let { add(it) }
    }

    /**
     * Descarga y parsea SIN pasar por una cadena con la guia entera: se le da
     * al parser el flujo de la conexion tal cual. Una guia real son decenas o
     * cientos de MB, y tenerla completa en memoria antes de empezar a parsear
     * es justo lo que tumba la aplicacion en un aparato de television.
     */
    private fun fetchAndParse(url: String): List<EPGProgram> {
        val ahora = System.currentTimeMillis()
        val connection = open(url)
        try {
            val bruto = connection.inputStream ?: error("La guia EPG llegó vacía")
            // Tope de expansion: un .gz de pocos MB puede descomprimirse en
            // gigabytes, a propósito o por estar roto.
            val limitado = LimitedInputStream(bruto, MAX_BYTES)
            val programas = parser.parse(
                input = maybeGunzip(limitado),
                keepFromMs = ahora - KEEP_PAST_MS,
                keepUntilMs = ahora + KEEP_FUTURE_MS,
            )
            if (programas.isEmpty()) error("La guia EPG no traía programas")
            return programas
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Descomprime si lo que llega es un gzip, mirando los DOS PRIMEROS BYTES y
     * no la extension de la URL: las guias se publican casi siempre
     * comprimidas, unas veces con `.gz` en la direccion y otras sin ella, y hay
     * servidores que ademas la sirven con el nombre a secas.
     */
    private fun maybeGunzip(input: InputStream): InputStream {
        val pushback = PushbackInputStream(input, 2)
        val cabecera = ByteArray(2)
        val leidos = pushback.read(cabecera)
        if (leidos > 0) pushback.unread(cabecera, 0, leidos)
        val esGzip = leidos == 2 &&
            (cabecera[0].toInt() and 0xFF) == 0x1F &&
            (cabecera[1].toInt() and 0xFF) == 0x8B
        if (esGzip) AppLog.d("EPG", "La guía viene comprimida (gzip)")
        return if (esGzip) GZIPInputStream(pushback, BUFFER_BYTES) else pushback
    }

    /** Mismas reglas que LibraryRepository.fetch: sigue redirecciones entre esquemas. */
    private fun open(url: String, redirectsLeft: Int = 5): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 120_000 // guias completas: decenas de MB
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "IPTV-Family/1.0")
        }
        val code = try {
            connection.responseCode
        } catch (e: Exception) {
            connection.disconnect()
            throw e
        }
        AppLog.d("EPG", "fetch: ${AppLog.redactUrl(url)} -> HTTP $code")
        if (code in 300..399) {
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location == null) error("Redirección ($code) sin destino")
            if (redirectsLeft <= 0) error("Demasiadas redirecciones")
            val next = if (location.startsWith("http")) location else URL(URL(url), location).toString()
            return open(next, redirectsLeft - 1)
        }
        if (code !in 200..299) {
            connection.disconnect()
            error("El servidor respondió $code")
        }
        return connection
    }

    /** Corta la lectura al superar [maxBytes]. Contra guias rotas y bombas zip. */
    private class LimitedInputStream(private val origen: InputStream, private val maxBytes: Long) : InputStream() {
        private var leidos = 0L

        override fun read(): Int = origen.read().also { if (it >= 0) contar(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            origen.read(b, off, len).also { if (it > 0) contar(it.toLong()) }

        override fun close() = origen.close()

        private fun contar(n: Long) {
            leidos += n
            if (leidos > maxBytes) {
                error("La guia EPG supera ${maxBytes / (1024 * 1024)} MB: se descarta")
            }
        }
    }

    private companion object {
        /** Recarga la guia como maximo cada 6 horas. */
        const val TTL_MS = 6 * 60 * 60 * 1000L

        /**
         * Ventana que se conserva. Hacia atras basta con unas horas para que
         * "ahora" siga saliendo en un programa largo; hacia delante, dos dias
         * cubren de sobra "lo siguiente" y cualquier guia que se quiera enseñar.
         */
        const val KEEP_PAST_MS = 6 * 60 * 60 * 1000L
        const val KEEP_FUTURE_MS = 48 * 60 * 60 * 1000L

        /** Tope de bytes leidos de la guia, ya descomprimida. */
        const val MAX_BYTES = 512L * 1024 * 1024
        const val BUFFER_BYTES = 64 * 1024
    }
}
