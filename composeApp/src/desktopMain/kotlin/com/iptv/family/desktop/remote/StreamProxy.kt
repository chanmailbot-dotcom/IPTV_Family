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

    /**
     * Manifest ya bajado y su marca de tiempo, para reusarlo entre consumidores.
     *
     * [ttlMs] se deriva del propio manifest (#EXT-X-TARGETDURATION): un TTL fijo
     * de 2 s hacia que, con dos consumidores (VLC local + navegador) preguntando,
     * saliera ~1 peticion por segundo hacia el panel, cuando un reproductor normal
     * pide la lista una vez por duracion de segmento (6-10 s). El panel lo tomaba
     * por abuso y respondia 407, y ahi empezaba el corte.
     */
    private data class CachedManifest(
        val raw: String,
        val finalUrl: String,
        val at: Long,
        val ttlMs: Long,
    )

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

    /** Hasta cuando no volver a pedirle la lista al panel, por URL, tras un rechazo. */
    private val manifestBackoffUntil = ConcurrentHashMap<String, Long>()

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
        if (hit != null && System.currentTimeMillis() - hit.at < hit.ttlMs) {
            respondManifest(call, hit.finalUrl, hit.raw)
            return
        }
        when (val r = fetchManifest(originUrl)) {
            is Upstream.Text -> respondManifest(call, r.finalUrl, r.text)
            // Ante un fallo de upstream, servir la ultima lista buena si no es muy
            // vieja ("stale-while-error"). Propagar el error hacia el reproductor
            // hacia que este reintentase mas rapido, lo que provocaba mas 407 del
            // panel: un bucle que acababa parando la emision. Con la lista de hace
            // unos segundos el reproductor sigue con los segmentos que ya conoce
            // mientras el mux espera a que el panel se calme.
            is Upstream.Failed, is Upstream.Error -> {
                val stale = manifestCache[originUrl]
                if (stale != null && System.currentTimeMillis() - stale.at < STALE_MANIFEST_MAX_MS) {
                    AppLog.w(
                        "StreamProxy",
                        "mux manifest: upstream fallo, sirviendo la ultima lista buena" +
                            " (${System.currentTimeMillis() - stale.at} ms de antiguedad)"
                    )
                    respondManifest(call, stale.finalUrl, stale.raw)
                } else when (r) {
                    is Upstream.Failed -> call.respondText("", ContentType.Text.Plain, HttpStatusCode.fromValue(r.status))
                    is Upstream.Error -> {
                        AppLog.e("StreamProxy", "mux manifest: fallo de red con ${AppLog.redactUrl(originUrl)}: ${r.message}")
                        call.respondText("", ContentType.Text.Plain, HttpStatusCode.BadGateway)
                    }
                    else -> Unit
                }
            }
            is Upstream.Binary -> Unit // imposible en un manifest
        }
    }

    private suspend fun fetchManifest(originUrl: String): Upstream = upstreamMutex.withLock {
        // Doble comprobacion dentro del lock: mientras esperabamos el turno, otro
        // consumidor pudo traer este mismo manifest (single-flight real).
        val hit = manifestCache[originUrl]
        if (hit != null && System.currentTimeMillis() - hit.at < hit.ttlMs) {
            return@withLock Upstream.Text(hit.raw, hit.finalUrl)
        }
        // Freno tras un rechazo: si el panel acaba de contestar 407/513, insistir
        // de inmediato solo alarga el bloqueo. Se deja pasar un momento y mientras
        // el llamante sirve la ultima lista buena.
        val blockedUntil = manifestBackoffUntil[originUrl]
        if (blockedUntil != null && System.currentTimeMillis() < blockedUntil) {
            return@withLock Upstream.Failed(429)
        }
        runCatching {
            client.prepareGet(originUrl) {
                headers.append(HttpHeaders.UserAgent, UPSTREAM_USER_AGENT)
            }.execute { response ->
                val status = response.status.value
                if (status !in 200..299) {
                    // El panel rechazo el manifiesto (407/513/404...). El llamante
                    // servira la ultima lista buena si la tiene; aqui solo se anota
                    // para no volver a insistir de inmediato.
                    AppLog.w("StreamProxy", "mux manifest: upstream $status para ${AppLog.redactUrl(originUrl)}")
                    manifestBackoffUntil[originUrl] = System.currentTimeMillis() + MANIFEST_BACKOFF_MS
                    Upstream.Failed(status)
                } else {
                    val text = response.bodyAsText()
                    // El panel rota host (y token de sesion) con redirecciones: las URIs
                    // relativas del manifest hay que resolverlas contra la URL FINAL de
                    // upstream, no contra la original, o cada consumidor acabaria en un
                    // host/sesion distinto (y el panel contaria conexiones nuevas).
                    val finalUrl = response.call.request.url.toString()
                    manifestBackoffUntil.remove(originUrl)
                    manifestCache[originUrl] = CachedManifest(
                        raw = text,
                        finalUrl = finalUrl,
                        at = System.currentTimeMillis(),
                        ttlMs = manifestTtlFor(text),
                    )
                    Upstream.Text(text, finalUrl)
                }
            }
        }.getOrElse { Upstream.Error(it.message ?: it::class.simpleName.orEmpty()) }
    }

    private suspend fun respondManifest(call: ApplicationCall, finalUrl: String, text: String) {
        // El <video> del navegador no puede enviar cabeceras Authorization: propagamos
        // en cada URL reescrita la MISMA credencial con la que se pidio este manifest,
        // para que los segmentos (y sub-manifiestos y claves) autentiquen por query y
        // no reciban 401.
        //
        // Hay dos tipos de credencial y hay que respetar la que venga:
        //  - `k`: la clave interna del mux, que usan el VLC del escritorio y ffmpeg
        //    (no tienen cuenta de usuario).
        //  - `s`: el token de sesion de una persona identificada en la web.
        // Propagar siempre `s` dejaba los segmentos sin credencial para VLC (que no
        // tiene sesion), y VLC fallaba con "Failed to create demuxer" al recibir 401.
        val credential = credentialParamOf(call)
        // Base de resolucion de URIs relativas: la URL final (tras redirecciones).
        val base = finalUrl.substringBeforeLast('/', missingDelimiterValue = "")
        val rewritten = text.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() || trimmed.startsWith("#EXT") && trimmed.contains("URI=\"") ->
                    rewriteTagUris(line, base, credential)
                trimmed.startsWith("#") -> line
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> segmentProxyUrl(trimmed, credential)
                else -> segmentProxyUrl(resolve(base, trimmed), credential)
            }
        }
        call.respondText(rewritten, ContentType.parse("application/vnd.apple.mpegurl"))
    }

    /**
     * Cuanto se puede reusar esta lista antes de volver a preguntar al panel.
     *
     * Un reproductor HLS refresca una lista de directo cada ~duracion de segmento.
     * Con dos consumidores (VLC local + navegador) y un TTL fijo de 2 s salian ~2
     * peticiones por segundo hacia el panel: por eso contestaba 407. Se usa la
     * mitad de #EXT-X-TARGETDURATION, que es la recomendacion del propio HLS, con
     * un minimo de 3 s para no pasarse de listo con targetduration pequeños.
     *
     * Una lista VOD (#EXT-X-ENDLIST) no cambia nunca: se puede cachear largo.
     */
    private fun manifestTtlFor(text: String): Long {
        if (text.contains("#EXT-X-ENDLIST")) return VOD_MANIFEST_TTL_MS
        val target = Regex("#EXT-X-TARGETDURATION:\\s*(\\d+)")
            .find(text)?.groupValues?.get(1)?.toLongOrNull()
        val half = target?.let { it * 1000 / 2 } ?: 0L
        return half.coerceAtLeast(MIN_MANIFEST_TTL_MS)
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
    private fun rewriteTagUris(line: String, base: String, credential: String?): String =
        Regex("URI=\"([^\"]+)\"").replace(line) { match ->
            val ref = match.groupValues[1]
            if (ref.startsWith("data:")) match.value
            else "URI=\"${segmentProxyUrl(resolve(base, ref), credential)}\""
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

    /** [credential] ya viene formado como "k=..." o "s=..." (ver credentialParamOf). */
    private fun segmentProxyUrl(url: String, credential: String?): String {
        val base = "/stream/segment?src=" + URLEncoder.encode(url, "UTF-8")
        return if (credential.isNullOrBlank()) base else "$base&$credential"
    }

    /**
     * Credencial de esta peticion, ya lista para pegar en una query: la clave
     * interna del mux si la trae (VLC del escritorio, ffmpeg) o, si no, el token
     * de sesion del navegador.
     */
    private fun credentialParamOf(call: ApplicationCall): String? {
        val localKey = call.request.queryParameters[LocalMuxKey.PARAM]
        if (localKey == LocalMuxKey.value) {
            return "${LocalMuxKey.PARAM}=" + URLEncoder.encode(localKey, "UTF-8")
        }
        val session = RemoteAuth.sessionToken(call) ?: return null
        return "s=" + URLEncoder.encode(session, "UTF-8")
    }

    companion object {
        fun decodeSrc(src: String): String = URLDecoder.decode(src, "UTF-8")

        /** Heuristica simple: si la URL de origen es un manifest de texto o un segmento binario. */
        fun looksLikeManifest(url: String): Boolean = url.substringBefore('?').endsWith(".m3u8")

        /**
         * Suelo del TTL de un manifest de directo (ver [manifestTtlFor], que lo
         * calcula de #EXT-X-TARGETDURATION). Antes habia aqui un TTL fijo de 2 s
         * que, con dos consumidores, disparaba ~2 peticiones/segundo al panel y
         * acababa en 407 -> emision cortada.
         */
        const val MIN_MANIFEST_TTL_MS = 3_000L

        /** Una lista con #EXT-X-ENDLIST (pelicula/serie) ya no cambia nunca. */
        const val VOD_MANIFEST_TTL_MS = 300_000L

        /**
         * Tras un rechazo del panel, tiempo sin volver a pedirle la lista. Mientras,
         * se sirve la ultima buena: el reproductor sigue con los segmentos que ya
         * conoce en vez de entrar en un bucle de reintentos.
         */
        const val MANIFEST_BACKOFF_MS = 2_000L

        /**
         * Cuanto se puede seguir sirviendo una lista vieja cuando el panel falla.
         * Pasado esto es mejor admitir el error que fingir un directo congelado.
         */
        const val STALE_MANIFEST_MAX_MS = 30_000L

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
