package com.iptv.family.desktop.remote

import com.iptv.family.shared.log.AppLog
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Multiplexor de fuente unica ("single-connection mux") hacia la URL real del
 * proveedor.
 *
 * Problema que resuelve: los paneles Xtream limitan normalmente a 1 conexion
 * por linea. Si VLC local y el navegador remoto consumen el canal por separado,
 * el panel patea una de las dos sesiones (513/403) y se ve el clasico
 * "en la app se ve, en el navegador no".
 *
 * Solucion: TODO el trafico upstream pasa por aqui, serializado con un mutex y
 * con cache de manifiestos (TTL corto) y de segmentos (inmutables, cachear es
 * gratis). VLC de escritorio se apunta al mux via
 * `http://127.0.0.1:puerto/stream/current.m3u8` (ver PlayerScreen), el navegador
 * consume la misma ruta: el proveedor ve UN solo cliente, con un patron de
 * peticiones secuencial, y ambos consumidores comparten los mismos bytes.
 */
class StreamProxy(private val client: HttpClient) {

    /** Manifest ya bajado y su marca de tiempo, para reusarlo entre consumidores. */
    private data class CachedManifest(val raw: String, val finalUrl: String, val at: Long)

    /** Segmento ya bajado: los segmentos HLS son inmutables, se cachean enteros. */
    private class CachedSegment(val bytes: ByteArray, val type: ContentType, val at: Long)

    private sealed interface Upstream {
        data class Text(val text: String, val finalUrl: String) : Upstream
        data class Binary(val segment: CachedSegment) : Upstream
        data class Failed(val status: Int) : Upstream
        data class Error(val message: String) : Upstream
    }

    private val manifestCache = ConcurrentHashMap<String, CachedManifest>()
    private val segmentCache = ConcurrentHashMap<String, CachedSegment>()

    /**
     * Serializa TODO el trafico upstream: aunque haya 3 consumidores (VLC, un
     * navegador, otro movil), hacia el proveedor hay como maximo 1 peticion en
     * vuelo en cada instante. Es lo que un panel "1 conexion" espera ver.
     */
    private val upstreamMutex = Mutex()

    /** Sirve un manifest .m3u8: reescribe las URIs de segmento para que pasen por este proxy. */
    suspend fun proxyManifest(call: ApplicationCall, originUrl: String) {
        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        val hit = manifestCache[originUrl]
        if (hit != null && System.currentTimeMillis() - hit.at < MANIFEST_TTL_MS) {
            respondManifest(call, hit.finalUrl, hit.raw)
            return
        }
        when (val r = fetchManifest(originUrl)) {
            is Upstream.Text -> respondManifest(call, r.finalUrl, r.text)
            is Upstream.Failed -> call.respondText("", ContentType.Text.Plain, HttpStatusCode.fromValue(r.status))
            is Upstream.Error -> {
                AppLog.e("StreamProxy", "mux manifest: fallo de red con ${AppLog.redactUrl(originUrl)}: ${r.message}")
                call.respondText("", ContentType.Text.Plain, HttpStatusCode.BadGateway)
            }
            is Upstream.Binary -> Unit // imposible en un manifest
        }
    }

    private suspend fun fetchManifest(originUrl: String): Upstream = upstreamMutex.withLock {
        // Doble comprobacion dentro del lock: mientras esperabamos el turno, otro
        // consumidor pudo traer este mismo manifest (single-flight real).
        val hit = manifestCache[originUrl]
        if (hit != null && System.currentTimeMillis() - hit.at < MANIFEST_TTL_MS) {
            return@withLock Upstream.Text(hit.raw, hit.finalUrl)
        }
        runCatching {
            client.prepareGet(originUrl) {
                headers.append(HttpHeaders.UserAgent, UPSTREAM_USER_AGENT)
            }.execute { response ->
                val status = response.status.value
                if (status !in 200..299) {
                    // El panel rechazo el manifiesto (513/404/...): propagar el codigo para
                    // que hls.js falle rapido y la web muestre "canal caido" en vez de
                    // quedarse encadenando reintentos contra un 200-vacio.
                    AppLog.w("StreamProxy", "mux manifest: upstream $status para ${AppLog.redactUrl(originUrl)}")
                    Upstream.Failed(status)
                } else {
                    val text = response.bodyAsText()
                    // El panel rota host (y token de sesion) con redirecciones: las URIs
                    // relativas del manifest hay que resolverlas contra la URL FINAL de
                    // upstream, no contra la original, o cada consumidor acabaria en un
                    // host/sesion distinto (y el panel contaria conexiones nuevas).
                    val finalUrl = response.call.request.url.toString()
                    manifestCache[originUrl] = CachedManifest(text, finalUrl, System.currentTimeMillis())
                    Upstream.Text(text, finalUrl)
                }
            }
        }.getOrElse { Upstream.Error(it.message ?: it::class.simpleName.orEmpty()) }
    }

    private suspend fun respondManifest(call: ApplicationCall, finalUrl: String, text: String) {
        // El <video> del navegador no puede enviar cabeceras Authorization: propagamos el
        // token con el que se pidio este manifest a cada URL reescrita, para que los
        // segmentos (y sub-manifiestos y claves) autentiquen por query y no reciban 401.
        val token = RemoteAuth.callToken(call)
        // Base de resolucion de URIs relativas: la URL final (tras redirecciones).
        val base = finalUrl.substringBeforeLast('/', missingDelimiterValue = "")
        val rewritten = text.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() || trimmed.startsWith("#EXT") && trimmed.contains("URI=\"") ->
                    rewriteTagUris(line, base, token)
                trimmed.startsWith("#") -> line
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> segmentProxyUrl(trimmed, token)
                else -> segmentProxyUrl(resolve(base, trimmed), token)
            }
        }
        call.respondText(rewritten, ContentType.parse("application/vnd.apple.mpegurl"))
    }

    /** Resuelve una referencia (relativa o absoluta) contra la base del manifest. */
    private fun resolve(base: String, ref: String): String = runCatching {
        java.net.URL(java.net.URL(base), ref).toString()
    }.getOrDefault(ref)

    /**
     * Reescribe los atributos URI="..." de #EXT-X-KEY (claves AES: si el navegador
     * las pidiera directo al panel, abriria una sesion propia), #EXT-X-MAP y
     * #EXT-X-MEDIA para que pasen por este mux.
     */
    private fun rewriteTagUris(line: String, base: String, token: String?): String =
        Regex("URI=\"([^\"]+)\"").replace(line) { match ->
            val ref = match.groupValues[1]
            if (ref.startsWith("data:")) match.value
            else "URI=\"${segmentProxyUrl(resolve(base, ref), token)}\""
        }

    /** Sirve un segmento (.ts/.m4s) desde cache o, si no esta, lo baja y lo cachea. */
    suspend fun proxySegment(call: ApplicationCall, originUrl: String) {
        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        val hit = segmentCache[originUrl]
        if (hit != null) {
            if (System.currentTimeMillis() - hit.at < SEGMENT_TTL_MS) {
                call.respondBytes(hit.bytes, hit.type)
                return
            }
            segmentCache.remove(originUrl, hit)
        }
        when (val r = fetchSegment(originUrl)) {
            is Upstream.Binary -> call.respondBytes(r.segment.bytes, r.segment.type)
            is Upstream.Failed -> call.respondText("", ContentType.Text.Plain, HttpStatusCode.fromValue(r.status))
            is Upstream.Error -> {
                AppLog.e("StreamProxy", "mux segmento: fallo de red con ${AppLog.redactUrl(originUrl)}: ${r.message}")
                call.respondText("", ContentType.Text.Plain, HttpStatusCode.BadGateway)
            }
            is Upstream.Text -> Unit // imposible en un segmento
        }
    }

    private suspend fun fetchSegment(originUrl: String): Upstream = upstreamMutex.withLock {
        val hit = segmentCache[originUrl]
        if (hit != null && System.currentTimeMillis() - hit.at < SEGMENT_TTL_MS) {
            return@withLock Upstream.Binary(hit)
        }
        runCatching {
            client.prepareGet(originUrl) {
                headers.append(HttpHeaders.UserAgent, UPSTREAM_USER_AGENT)
            }.execute { response ->
                val status = response.status.value
                if (status !in 200..299) {
                    AppLog.w("StreamProxy", "mux segmento: upstream $status para ${AppLog.redactUrl(originUrl)}")
                    Upstream.Failed(status)
                } else {
                    // Cuando el canal no emite, el panel responde 200 con HTML vacio en vez
                    // de un .ts: convertir eso en un error real (502) para que el reproductor
                    // no se quede en el bucle de "sintonizando" con datos que no son video.
                    val type = response.contentType() ?: ContentType.Application.OctetStream
                    if (type.contentType == "text") {
                        AppLog.w("StreamProxy", "mux segmento: upstream devolvio text/html (canal caido) para ${AppLog.redactUrl(originUrl)}")
                        Upstream.Failed(502)
                    } else {
                        val segment = CachedSegment(response.readBytes(), type, System.currentTimeMillis())
                        segmentCache[originUrl] = segment
                        evictSegments()
                        AppLog.d("StreamProxy", "mux segmento: cache ${segmentCache.size} entradas (~${segmentCache.values.sumOf { it.bytes.size } / 1024} KB)")
                        Upstream.Binary(segment)
                    }
                }
            }
        }.getOrElse { Upstream.Error(it.message ?: it::class.simpleName.orEmpty()) }
    }

    /** Recorta la cache por tamaño total: fuera los segmentos mas viejos primero. */
    private fun evictSegments() {
        var total = segmentCache.values.sumOf { it.bytes.size }
        if (total <= MAX_CACHE_BYTES) return
        for (entry in segmentCache.entries.sortedBy { it.value.at }) {
            if (total <= MAX_CACHE_BYTES) break
            if (segmentCache.remove(entry.key, entry.value)) total -= entry.value.bytes.size
        }
    }

    private fun segmentProxyUrl(url: String, token: String?): String {
        val base = "/stream/segment?src=" + URLEncoder.encode(url, "UTF-8")
        return if (token.isNullOrBlank()) base else base + "&token=" + URLEncoder.encode(token, "UTF-8")
    }

    companion object {
        fun decodeSrc(src: String): String = URLDecoder.decode(src, "UTF-8")

        /** Heuristica simple: si la URL de origen es un manifest de texto o un segmento binario. */
        fun looksLikeManifest(url: String): Boolean = url.substringBefore('?').endsWith(".m3u8")

        /**
         * Reutilizacion de un manifest ya bajado. Corto a proposito: si fuese mayor que
         * el ciclo de refresco de los reproductores, servirian playlists congeladas y
         * el directo se pararia; su funcion es solo deduplicar peticiones simultaneas.
         */
        const val MANIFEST_TTL_MS = 2_000L

        /** Los segmentos son inmutables: TTL alto y eviction solo por tamaño. */
        const val SEGMENT_TTL_MS = 600_000L

        /** Tope de cache de segmentos (~96 MB: sobra para cualquier lag razonable). */
        const val MAX_CACHE_BYTES = 96 * 1024 * 1024

        /**
         * UA con el que el mux habla con el proveedor. Como VLC consume via mux, este
         * proxy es el unico cliente real: con un UA estable el panel ve un cliente
         * coherente en todas las peticiones (antes mezclaba VLC + Java/Ktor).
         */
        const val UPSTREAM_USER_AGENT = "VLC/3.0.21 LibVLC/3.0.21"
    }
}
