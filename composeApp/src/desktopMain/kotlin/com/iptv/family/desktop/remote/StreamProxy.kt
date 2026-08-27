package com.iptv.family.desktop.remote

import com.iptv.family.shared.log.AppLog
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.utils.io.copyAndClose
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Relay HTTP transparente ("pass-through") hacia la URL real del proveedor,
 * para que el navegador remoto pueda ver el mismo canal sin abrir su propia
 * conexion directa contra el proveedor (algunos paneles Xtream limitan a 1
 * conexion simultanea).
 *
 * Limitacion conocida del MVP: si VLC local Y un navegador remoto consumen a
 * la vez, se abren 2 conexiones reales (una de VLC, una de este proxy). Un
 * multiplexor de fuente unica compartida se deja para una iteracion futura.
 */
class StreamProxy(private val client: HttpClient) {

    /** Sirve un manifest .m3u8: reescribe las URIs de segmento absolutas para que pasen por este proxy. */
    suspend fun proxyManifest(call: ApplicationCall, originUrl: String) {
        val text = runCatching { client.get(originUrl).bodyAsText() }.getOrElse {
            AppLog.e("StreamProxy", "proxyManifest: fallo descargando ${AppLog.redactUrl(originUrl)}", it)
            call.respondText("", ContentType.Text.Plain, HttpStatusCode.BadGateway)
            return
        }
        val base = originUrl.substringBeforeLast('/', missingDelimiterValue = "")
        val rewritten = text.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() || trimmed.startsWith("#") -> line
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> segmentProxyUrl(trimmed)
                else -> segmentProxyUrl("$base/$trimmed")
            }
        }
        call.respondText(rewritten, ContentType.parse("application/vnd.apple.mpegurl"))
    }

    /** Sirve un segmento (.ts/.m4s) o cualquier binario tal cual, copiando bytes sin cargarlos en memoria. */
    suspend fun proxySegment(call: ApplicationCall, originUrl: String) {
        runCatching {
            client.prepareGet(originUrl).execute { response ->
                val type = response.contentType() ?: ContentType.Application.OctetStream
                call.respondBytesWriter(contentType = type) {
                    response.bodyAsChannel().copyAndClose(this)
                }
            }
        }.onFailure {
            AppLog.e("StreamProxy", "proxySegment: fallo con ${AppLog.redactUrl(originUrl)}", it)
        }
    }

    private fun segmentProxyUrl(url: String): String =
        "/stream/segment?src=" + URLEncoder.encode(url, "UTF-8")

    companion object {
        fun decodeSrc(src: String): String = URLDecoder.decode(src, "UTF-8")

        /** Heuristica simple: si la URL de origen es un manifest de texto o un segmento binario. */
        fun looksLikeManifest(url: String): Boolean = url.substringBefore('?').endsWith(".m3u8")
    }
}
