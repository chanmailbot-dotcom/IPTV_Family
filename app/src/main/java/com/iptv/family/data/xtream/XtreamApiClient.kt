package com.iptv.family.data.xtream

import com.iptv.family.domain.model.Category
import com.iptv.family.domain.model.Channel
import com.iptv.family.domain.model.ChannelType
import com.iptv.family.domain.model.EpgEntry
import com.iptv.family.domain.model.Playlist
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente para Xtream Codes API
 *
 * Endpoints estándar:
 * - /login?username=X&password=Y
 * - /player_api.php?username=X&password=Y&action=get_live_streams
 * - /player_api.php?username=X&password=Y&action=get_vod_streams
 * - /player_api.php?username=X&password=Y&action=get_series
 * - /player_api.php?username=X&password=Y&action=get_short_epg&epg_id=ID
 */
class XtreamApiClient(
    private val baseUrl: String,
    private val username: String,
    private val password: String,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    private fun parseJson(response: String): JsonElement {
        return json.parseToJsonElement(response)
    }

    private fun getStringOrNull(obj: JsonObject, key: String): String? {
        val element = obj[key]
        return if (element is JsonPrimitive) element.content else null
    }

    private fun getIntOrNull(obj: JsonObject, key: String): Int? {
        val element = obj[key]
        return if (element is JsonPrimitive) element.content.toIntOrNull() else null
    }

    /**
     * Verifica las credenciales y obtiene información del servidor
     */
    fun login(): XtreamLoginResult {
        val url = "$baseUrl/login?username=$username&password=$password"
        return safeRequest(url) { response ->
            val obj = parseJson(response) as JsonObject
            val user = obj["user_info"] as? JsonObject
            if (user != null) {
                XtreamLoginResult.Success(
                    status = getStringOrNull(obj, "status") ?: "OK",
                    username = getStringOrNull(user, "username") ?: "",
                    expDate = getStringOrNull(user, "exp_date") ?: ""
                )
            } else {
                XtreamLoginResult.Error("Invalid credentials")
            }
        } ?: XtreamLoginResult.Error("Login failed")
    }

    /**
     * Obtiene todos los canales Live TV
     */
    fun getLiveStreams(): List<Channel> {
        val url = "$baseUrl/player_api.php?username=$username&password=$password&action=get_live_streams"
        return safeRequest(url) { response ->
            val elements = parseJson(response) as JsonArray
            elements.mapNotNull { element ->
                val obj = element as JsonObject
                val id = getStringOrNull(obj, "channelID") ?: return@mapNotNull null
                val name = getStringOrNull(obj, "name") ?: "Canal $id"
                val logo = getStringOrNull(obj, "logo")
                val streamId = getStringOrNull(obj, "stream_id") ?: id
                val catId = getStringOrNull(obj, "category_id") ?: "0"

                Channel(
                    id = id,
                    name = name,
                    logoUrl = logo,
                    streamUrl = "$baseUrl/live/$username/$password/$streamId/index.m3u8",
                    category = Category(catId, "Category $catId", ChannelType.LIVE_TV),
                    type = ChannelType.LIVE_TV,
                    epgId = getStringOrNull(obj, "epg_channel_id")
                )
            }
        } ?: emptyList()
    }

    /**
     * Obtiene todos los contenidos VOD
     */
    fun getVodStreams(): List<Channel> {
        val url = "$baseUrl/player_api.php?username=$username&password=$password&action=get_vod_streams"
        return safeRequest(url) { response ->
            val elements = parseJson(response) as JsonArray
            elements.mapNotNull { element ->
                val obj = element as JsonObject
                val id = getStringOrNull(obj, "vod_id") ?: getStringOrNull(obj, "id") ?: return@mapNotNull null
                val name = getStringOrNull(obj, "name") ?: "VOD $id"
                val logo = getStringOrNull(obj, "cover")
                val streamId = getStringOrNull(obj, "stream_id") ?: id
                val catId = getStringOrNull(obj, "category_id") ?: "0"
                val containerExt = getStringOrNull(obj, "container_extension") ?: "mp4"

                Channel(
                    id = id,
                    name = name,
                    logoUrl = logo,
                    description = getStringOrNull(obj, "description") ?: "",
                    streamUrl = "$baseUrl/movie/$username/$password/$streamId/$containerExt",
                    category = Category(catId, "Movies", ChannelType.VOD),
                    type = ChannelType.VOD,
                    duration = getStringOrNull(obj, "duration"),
                    year = getStringOrNull(obj, "year"),
                    rating = getStringOrNull(obj, "rating")
                )
            }
        } ?: emptyList()
    }

    /**
     * Obtiene todas las series
     */
    fun getSeriesStreams(): List<Channel> {
        val url = "$baseUrl/player_api.php?username=$username&password=$password&action=get_series"
        return safeRequest(url) { response ->
            val elements = parseJson(response) as JsonArray
            elements.mapNotNull { element ->
                val obj = element as JsonObject
                val id = getStringOrNull(obj, "series_id") ?: getStringOrNull(obj, "id") ?: return@mapNotNull null
                val name = getStringOrNull(obj, "name") ?: "Series $id"
                val logo = getStringOrNull(obj, "cover")
                val catId = getStringOrNull(obj, "category_id") ?: "0"

                Channel(
                    id = id,
                    name = name,
                    logoUrl = logo,
                    description = getStringOrNull(obj, "description") ?: "",
                    streamUrl = "",
                    category = Category(catId, "Series", ChannelType.SERIES),
                    type = ChannelType.SERIES,
                    rating = getStringOrNull(obj, "rating")
                )
            }
        } ?: emptyList()
    }

    /**
     * Obtiene las categorías de contenido
     */
    fun getCategories(type: String = "live"): List<Category> {
        val url = "$baseUrl/player_api.php?username=$username&password=$password&action=get_categories&category_type=$type"
        return safeRequest(url) { response ->
            val elements = parseJson(response) as JsonArray
            elements.mapNotNull { element ->
                val obj = element as JsonObject
                val id = getStringOrNull(obj, "category_id") ?: return@mapNotNull null
                val name = getStringOrNull(obj, "category_name") ?: "Categoría $id"
                val catType = when (type) {
                    "live" -> ChannelType.LIVE_TV
                    "vod" -> ChannelType.VOD
                    "series" -> ChannelType.SERIES
                    else -> ChannelType.LIVE_TV
                }
                Category(
                    id = id,
                    name = name,
                    type = catType
                )
            }
        } ?: emptyList()
    }

    /**
     * Obtiene el EPG de un canal
     */
    fun getShortEpg(epgId: String): List<EpgEntry> {
        val url = "$baseUrl/player_api.php?username=$username&password=$password&action=get_short_epg&epg_id=$epgId"
        return safeRequest(url) { response ->
            val obj = parseJson(response) as JsonObject
            val epgList = obj["epg_list"] as? JsonArray
            epgList?.mapNotNull { element ->
                val e = element as JsonObject
                EpgEntry(
                    id = getStringOrNull(e, "id") ?: "",
                    channelId = epgId,
                    title = getStringOrNull(e, "title") ?: "",
                    description = getStringOrNull(e, "description") ?: "",
                    startTime = parseEpoch(getStringOrNull(e, "start_timestamp")),
                    endTime = parseEpoch(getStringOrNull(e, "stop_timestamp")),
                    timezone = getStringOrNull(e, "timezone")
                )
            } ?: emptyList()
        } ?: emptyList()
    }

    /**
     * Obtiene episodios de una serie
     */
    fun getSeriesEpisodes(seriesId: String): List<SeriesSeason> {
        val url = "$baseUrl/serial/series?username=$username&password=$password&action=info&series_id=$seriesId"
        return safeRequest(url) { response ->
            val obj = parseJson(response) as JsonObject
            val episodes = obj["episodes"] as? JsonObject
            episodes?.map { (season, seasonData) ->
                val seasonNumber = season.replace("season_", "").toIntOrNull() ?: 0
                val episodesList = (seasonData as JsonArray).mapIndexed { index, epElement ->
                    val ep = epElement as JsonObject
                    SeriesEpisode(
                        id = getStringOrNull(ep, "id") ?: "",
                        title = getStringOrNull(ep, "title") ?: "Episodio ${index + 1}",
                        overview = getStringOrNull(ep, "overview") ?: "",
                        seasonNumber = seasonNumber,
                        episodeNumber = getIntOrNull(ep, "episode") ?: index + 1,
                        airDate = getStringOrNull(ep, "airstamp") ?: "",
                        streamUrl = getStringOrNull(ep, "stream_id")?.let { id ->
                            "$baseUrl/serial/$username/$password/$seriesId/$id/index.m3u8"
                        } ?: ""
                    )
                }
                SeriesSeason(
                    number = seasonNumber,
                    episodes = episodesList
                )
            }?.toList() ?: emptyList()
        } ?: emptyList()
    }

    private fun parseEpoch(timestamp: String?): Long {
        return try {
            timestamp?.toLong() ?: 0
        } catch (e: NumberFormatException) {
            0
        }
    }

    private inline fun <reified T> safeRequest(
        urlString: String,
        parser: (String) -> T
    ): T? {
        return try {
            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 30000
                setRequestProperty("User-Agent", "IPTV-Family/1.0")
            }
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            parser(response)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

/**
 * Resultado del login a Xtream
 */
sealed class XtreamLoginResult {
    data class Success(
        val status: String,
        val username: String,
        val expDate: String
    ) : XtreamLoginResult()

    data class Error(val message: String) : XtreamLoginResult()
}

/**
 * Temporada de serie
 */
data class SeriesSeason(
    val number: Int,
    val episodes: List<SeriesEpisode>
)

/**
 * Episodio de serie
 */
data class SeriesEpisode(
    val id: String,
    val title: String,
    val overview: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val airDate: String,
    val streamUrl: String
)