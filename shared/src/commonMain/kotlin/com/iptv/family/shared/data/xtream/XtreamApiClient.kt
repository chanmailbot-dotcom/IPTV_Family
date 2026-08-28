package com.iptv.family.shared.data.xtream

import com.iptv.family.shared.log.AppLog
import com.iptv.family.shared.model.Category
import com.iptv.family.shared.model.CategoryType
import com.iptv.family.shared.model.Channel
import com.iptv.family.shared.model.EPGProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente para la API de Xtream Codes (player_api.php).
 * Se ejecuta en IO y devuelve modelos del dominio compartido.
 */
class XtreamApiClient(
    baseUrl: String,
    private val username: String,
    private val password: String
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Si el usuario pega "midominio.com:8080" sin esquema, java.net.URL lanza
    // "no protocol: ..." al primer request. Los paneles Xtream son casi siempre
    // http (no https), así que ese es el valor por defecto razonable.
    private val baseUrlClean: String = baseUrl.trim().trimEnd('/').let {
        if (it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)) it
        else "http://$it"
    }

    suspend fun login(): LoginResult = withContext(Dispatchers.IO) {
        try {
            val obj = getJson("action=user_info") as? JsonObject
                ?: return@withContext LoginResult(success = false, error = "Respuesta inválida")

            val userInfo = obj["user_info"] as? JsonObject
            val auth = (userInfo?.get("auth") as? JsonPrimitive)?.content ?: "0"
            val status = (userInfo?.get("status") as? JsonPrimitive)?.content ?: ""
            val expDate = (userInfo?.get("exp_date") as? JsonPrimitive)?.content ?: ""
            val userName = (userInfo?.get("username") as? JsonPrimitive)?.content ?: this@XtreamApiClient.username

            if (auth == "1") {
                LoginResult(
                    success = true,
                    username = userName,
                    status = status,
                    expDate = expDate,
                    rawUserInfo = userInfo
                )
            } else {
                AppLog.w("Xtream", "login: auth=$auth status=$status (respuesta: $obj)")
                LoginResult(success = false, error = "Credenciales inválidas (auth != 1)")
            }
        } catch (e: Exception) {
            AppLog.e("Xtream", "login: excepción", e)
            LoginResult(success = false, error = "Error de conexión: ${e.message}")
        }
    }

    suspend fun getLiveCategories(): List<Category> = getCategories("live")
    suspend fun getVodCategories(): List<Category> = getCategories("vod")
    suspend fun getSeriesCategories(): List<Category> = getCategories("series")

    suspend fun getLiveStreams(): List<Channel> = getStreams("live")
    suspend fun getVodStreams(): List<Channel> = getStreams("vod")
    suspend fun getSeriesStreams(): List<Channel> = getStreams("series")

    /**
     * [containerExtension] es el que devuelve la propia API en cada stream
     * (mkv, avi, mp4...); si no viene, se usa un valor por defecto razonable.
     *
     * OJO: la carpeta de la URL para peliculas es "movie", no "vod" -- el
     * nombre "vod" solo se usa en la propia API (get_vod_streams); Xtream
     * Codes sirve el fichero real bajo /movie/usuario/clave/id.ext. Usar
     * "vod" en la URL da 404 (ERROR_CODE_IO_BAD_HTTP_STATUS en ExoPlayer).
     */
    fun getStreamUrl(type: String, streamId: String, containerExtension: String? = null): String {
        val path = when (type) {
            "vod" -> "movie"
            else -> type
        }
        val extension = containerExtension?.takeIf { it.isNotBlank() } ?: when (type) {
            "live" -> "m3u8"
            "vod", "series" -> "mp4"
            else -> "m3u8"
        }
        return "$baseUrlClean/$path/$username/$password/$streamId.$extension"
    }

    /**
     * `get_series` solo da el show (temporada/episodios agregados): el
     * `series_id` que devuelve NO es un stream reproducible por si mismo. Hay
     * que pedir `get_series_info` para sacar los episodios reales, cada uno
     * con su propio id reproducible bajo /series/user/pass/episodio_id.ext.
     * Antes se intentaba reproducir directamente el series_id como si fuera
     * un canal -- de ahi el mismo ERROR_CODE_IO_BAD_HTTP_STATUS que en VOD.
     */
    suspend fun getSeriesEpisodes(seriesId: String): List<Channel> = withContext(Dispatchers.IO) {
        try {
            val obj = getJson("action=get_series_info&series_id=$seriesId") as? JsonObject
                ?: return@withContext emptyList()
            val episodesBySeason = obj["episodes"] as? JsonObject ?: return@withContext emptyList()

            episodesBySeason.entries
                .sortedBy { it.key.toIntOrNull() ?: 0 }
                .flatMap { (season, episodesForSeason) ->
                    val array = episodesForSeason as? JsonArray ?: return@flatMap emptyList()
                    array.mapNotNull { element ->
                        val ep = element as? JsonObject ?: return@mapNotNull null
                        val episodeId = ep.stringOrNull("id") ?: return@mapNotNull null
                        val title = ep.stringOrNull("title") ?: "Episodio $episodeId"
                        val containerExtension = (ep["container_extension"] as? JsonPrimitive)?.content
                            ?: (ep["info"] as? JsonObject)?.stringOrNull("container_extension")
                        Channel(
                            id = episodeId,
                            name = "T$season · $title",
                            url = getStreamUrl("series", episodeId, containerExtension),
                            logoUrl = null,
                            group = null,
                            categoryType = CategoryType.SERIES,
                        )
                    }
                }
        } catch (e: Exception) {
            AppLog.e("Xtream", "getSeriesEpisodes($seriesId): excepción", e)
            emptyList()
        }
    }

    suspend fun getEPG(limit: Int = 1000): List<EPGProgram> = withContext(Dispatchers.IO) {
        try {
            val obj = getJson("action=get_epg&limit=$limit") as? JsonObject
                ?: return@withContext emptyList()
            val epgArray = obj["epg"] as? JsonArray ?: return@withContext emptyList()

            epgArray.mapNotNull { element ->
                val epg = element as? JsonObject ?: return@mapNotNull null
                EPGProgram(
                    id = epg.stringOrNull("id") ?: "",
                    channelId = epg.stringOrNull("channel_id") ?: "",
                    title = epg.stringOrNull("title") ?: "",
                    description = epg.stringOrNull("description"),
                    startTime = (epg.longOrNull("start") ?: 0L) * 1000L,
                    endTime = (epg.longOrNull("end") ?: 0L) * 1000L,
                    category = epg.stringOrNull("category"),
                    iconUrl = epg.stringOrNull("icon")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ------------------------------------------------------------------
    // Privado
    // ------------------------------------------------------------------

    private suspend fun getCategories(type: String): List<Category> = withContext(Dispatchers.IO) {
        try {
            val array = getJson("action=get_${type}_categories") as? JsonArray
                ?: return@withContext emptyList()

            array.mapNotNull { element ->
                val cat = element as? JsonObject ?: return@mapNotNull null
                val id = cat.stringOrNull("category_id") ?: return@mapNotNull null
                Category(
                    id = id,
                    name = cat.stringOrNull("category_name") ?: "Categoría $id",
                    type = type.toCategoryType(),
                    channels = emptyList()
                )
            }
        } catch (e: Exception) {
            AppLog.e("Xtream", "getCategories($type): excepción", e)
            emptyList()
        }
    }

    private suspend fun getStreams(type: String): List<Channel> = withContext(Dispatchers.IO) {
        val action = when (type) {
            "live" -> "get_live_streams"
            "vod" -> "get_vod_streams"
            "series" -> "get_series"
            else -> "get_live_streams"
        }

        try {
            val raw = getJson("action=$action")
            val array = raw as? JsonArray
            if (array == null) {
                AppLog.w("Xtream", "getStreams($type): respuesta no es un array: ${raw?.let { it::class.simpleName }}")
                return@withContext emptyList()
            }

            array.mapNotNull { element ->
                val stream = element as? JsonObject ?: return@mapNotNull null
                val streamId = stream.stringOrNull("stream_id")
                    ?: stream.stringOrNull("series_id")
                    ?: return@mapNotNull null

                val categoryId = stream.stringOrNull("category_id") ?: ""
                val containerExtension = stream.stringOrNull("container_extension")
                Channel(
                    id = streamId,
                    name = stream.stringOrNull("name") ?: "Canal $streamId",
                    url = getStreamUrl(type, streamId, containerExtension),
                    logoUrl = stream.stringOrNull("stream_icon") ?: stream.stringOrNull("cover"),
                    group = categoryId.ifBlank { null },
                    // El id de guia real del panel (ej. "la1.es"); muchos paneles
                    // lo traen vacio, entonces el stream_id es el mejor candidato.
                    epgChannelId = if (type == "live") {
                        stream.stringOrNull("epg_channel_id")?.takeIf { it.isNotBlank() } ?: streamId
                    } else null,
                    categoryType = type.toCategoryType(),
                    // `num` es el orden de dial que publica el panel; sin esto la
                    // lista sale en el orden crudo del JSON, que no es el que el
                    // usuario espera ver.
                    number = stream.longOrNull("num")?.toInt()
                )
            }
        } catch (e: Exception) {
            AppLog.e("Xtream", "getStreams($type): excepción", e)
            emptyList()
        }
    }

    private fun getJson(queryString: String): Any? {
        val url = URL("$baseUrlClean/player_api.php?username=$username&password=$password&$queryString")
        AppLog.d("Xtream", "getJson: ${AppLog.redactUrl(url.toString())}")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "IPTV-Family/1.0")
        }

        return try {
            val code = connection.responseCode
            AppLog.d("Xtream", "getJson: HTTP $code")
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (body.isBlank()) {
                AppLog.w("Xtream", "getJson: cuerpo vacío (HTTP $code)")
                null
            } else {
                json.parseToJsonElement(body)
            }
        } catch (e: Exception) {
            AppLog.e("Xtream", "getJson: excepción de red", e)
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private fun String.toCategoryType(): CategoryType = when (this) {
        "live" -> CategoryType.LIVE
        "vod" -> CategoryType.VOD
        "series" -> CategoryType.SERIES
        else -> CategoryType.LIVE
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.longOrNull(key: String): Long? =
        (this[key] as? JsonPrimitive)?.content?.toLongOrNull()

    data class LoginResult(
        val success: Boolean,
        val error: String? = null,
        val username: String? = null,
        val status: String? = null,
        val expDate: String? = null,
        val rawUserInfo: JsonObject? = null
    )
}