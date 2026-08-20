package com.iptv.family.shared.data.xtream

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

    private val baseUrlClean: String = baseUrl.trim().trimEnd('/')

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
                LoginResult(success = false, error = "Credenciales inválidas (auth != 1)")
            }
        } catch (e: Exception) {
            LoginResult(success = false, error = "Error de conexión: ${e.message}")
        }
    }

    suspend fun getLiveCategories(): List<Category> = getCategories("live")
    suspend fun getVodCategories(): List<Category> = getCategories("vod")
    suspend fun getSeriesCategories(): List<Category> = getCategories("series")

    suspend fun getLiveStreams(): List<Channel> = getStreams("live")
    suspend fun getVodStreams(): List<Channel> = getStreams("vod")
    suspend fun getSeriesStreams(): List<Channel> = getStreams("series")

    fun getStreamUrl(type: String, streamId: String): String {
        val extension = when (type) {
            "live" -> "m3u8"
            "vod", "series" -> "mp4"
            else -> "m3u8"
        }
        return "$baseUrlClean/$type/$username/$password/$streamId.$extension"
    }

    /** URL de la serie completa para reproducción por toc.m3u8 */
    fun getSeriesStreamUrl(seriesId: String): String =
        "$baseUrlClean/series/$username/$password/$seriesId/toc.m3u8"

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
            val array = getJson("action=$action") as? JsonArray ?: return@withContext emptyList()

            array.mapNotNull { element ->
                val stream = element as? JsonObject ?: return@mapNotNull null
                val streamId = stream.stringOrNull("stream_id")
                    ?: stream.stringOrNull("series_id")
                    ?: return@mapNotNull null

                val categoryId = stream.stringOrNull("category_id") ?: ""
                Channel(
                    id = streamId,
                    name = stream.stringOrNull("name") ?: "Canal $streamId",
                    url = getStreamUrl(type, streamId),
                    logoUrl = stream.stringOrNull("stream_icon") ?: stream.stringOrNull("cover"),
                    group = categoryId.ifBlank { null },
                    epgChannelId = if (type == "live") streamId else null,
                    categoryType = type.toCategoryType()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getJson(queryString: String): Any? {
        val url = URL("$baseUrlClean/player_api.php?username=$username&password=$password&$queryString")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "IPTV-Family/1.0")
        }

        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (body.isBlank()) null else json.parseToJsonElement(body)
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