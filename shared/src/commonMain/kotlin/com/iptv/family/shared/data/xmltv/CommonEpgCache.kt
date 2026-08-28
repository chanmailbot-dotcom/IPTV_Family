package com.iptv.family.shared.data.xmltv

import com.iptv.family.shared.log.AppLog
import com.iptv.family.shared.model.EPGProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

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

    private fun fetchAndParse(url: String): List<EPGProgram> {
        val xml = fetch(url)
        if (xml.isBlank()) throw IllegalStateException("La guia EPG llegó vacía")
        return parser.parse(xml)
    }

    /** Mismas reglas que LibraryRepository.fetch: sigue redirecciones entre esquemas. */
    private fun fetch(url: String, redirectsLeft: Int = 5): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 120_000 // guias completas: decenas de MB
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "IPTV-Family/1.0")
        }
        try {
            val code = connection.responseCode
            AppLog.d("EPG", "fetch: ${AppLog.redactUrl(url)} -> HTTP $code")
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: throw IllegalStateException("Redirección ($code) sin destino")
                if (redirectsLeft <= 0) throw IllegalStateException("Demasiadas redirecciones")
                val next = if (location.startsWith("http")) location else URL(URL(url), location).toString()
                return fetch(next, redirectsLeft - 1)
            }
            if (code !in 200..299) {
                throw IllegalStateException("El servidor respondió $code")
            }
            return connection.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        /** Recarga la guia como maximo cada 6 horas. */
        const val TTL_MS = 6 * 60 * 60 * 1000L
    }
}