package com.iptv.family.data.xtream

import com.iptv.family.domain.model.Channel
import com.iptv.family.domain.model.Category
import com.iptv.family.domain.model.Playlist
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface XtreamApiService {
    @GET("player_api.php")
    suspend fun getLiveCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories"
    ): Response<XtreamResponse<List<XtreamCategory>>>

    @GET("player_api.php")
    suspend fun getVodCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_categories"
    ): Response<XtreamResponse<List<XtreamCategory>>>

    @GET("player_api.php")
    suspend fun getSeriesCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_categories"
    ): Response<XtreamResponse<List<XtreamCategory>>>

    @GET("player_api.php")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams",
        @Query("category_id") categoryId: String? = null
    ): Response<XtreamResponse<List<XtreamStream>>>

    @GET("player_api.php")
    suspend fun getVodStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_streams",
        @Query("category_id") categoryId: String? = null
    ): Response<XtreamResponse<List<XtreamStream>>>

    @GET("player_api.php")
    suspend fun getSeriesStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_streams",
        @Query("category_id") categoryId: String? = null
    ): Response<XtreamResponse<List<XtreamStream>>>

    @GET("player_api.php")
    suspend fun getServerInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_server_info"
    ): Response<XtreamResponse<XtreamServerInfo>>

    @GET("player_api.php")
    suspend fun getEpg(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_simple_data_table",
        @Query("stream_id") streamId: String
    ): Response<XtreamResponse<XtreamEpgData>>
}

@Serializable
data class XtreamResponse<T>(
    val success: Boolean = true,
    val data: T? = null,
    val message: String? = null,
    val error: String? = null,
)

@Serializable
data class XtreamCategory(
    val category_id: String,
    val category_name: String,
    val parent_id: Int = 0,
)

@Serializable
data class XtreamStream(
    val stream_id: String,
    val name: String,
    val stream_icon: String? = null,
    val epg_channel_id: String? = null,
    val category_id: String? = null,
    val added: String? = null,
    val is_adult: String? = null,
    val stream_type: String? = null,
    val num: Int = 0,
    val stream_source: String? = null,
    val stream_source_type: String? = null,
    val container_extension: String? = null,
    val rating: String? = null,
    val rating_5based: Float = 0f,
    val release_date: String? = null,
    val director: String? = null,
    val cast: String? = null,
    val description: String? = null,
    val plot: String? = null,
    val genre: String? = null,
    val duration: String? = null,
    val duration_secs: Int = 0,
    val season: String? = null,
    val episode: String? = null,
    val series_id: String? = null,
    val series_cat: String? = null,
    val season_id: String? = null,
    val cover: String? = null,
    val backdrop: String? = null,
    val youtube_trailer: String? = null,
    val last_modified: String? = null,
    val tmdb_id: String? = null,
    val tvdb_id: String? = null,
    val country: String? = null,
    val language: String? = null,
    val imdb_rating: String? = null,
    val is_serie: String? = null,
    val is_movie: String? = null,
    val is_live: String? = null,
    val has_epg: String? = null,
    val season_name: String? = null,
    val episode_num: String? = null,
    val season_cover: String? = null,
    val episode_info: String? = null,
)

@Serializable
data class XtreamServerInfo(
    val url: String,
    val port: String,
    val version: String,
    val time_zone: String,
    val timestamp_now: Long,
    val timestamp_timezone_offset: Int,
)

@Serializable
data class XtreamEpgData(
    val epg_listings: Map<String, List<XtreamEpgEntry>>,
) {
    @Serializable
    data class XtreamEpgEntry(
        val start: Long,
        val end: Long,
        val title: String,
        val description: String? = null,
    )
}

class XtreamApiClient private constructor(
    private val service: XtreamApiService,
    private val baseUrl: String,
    private val username: String,
    private val password: String,
) {
    companion object {
        fun create(baseUrl: String, username: String, password: String): XtreamApiClient {
            val normalizedUrl = baseUrl.trim().removeSuffix("/")
            val retrofit = Retrofit.Builder()
                .baseUrl("$normalizedUrl/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return XtreamApiClient(retrofit.create(XtreamApiService::class.java), normalizedUrl, username, password)
        }
    }

    suspend fun testConnection(): XtreamConnectionResult {
        return try {
            val response = service.getServerInfo(username, password)
            if (response.isSuccessful && response.body()?.success == true) {
                val serverInfo = response.body()?.data
                XtreamConnectionResult.Success(serverInfo)
            } else {
                XtreamConnectionResult.Failure(response.body()?.message ?: "Error de conexión")
            }
        } catch (e: Exception) {
            XtreamConnectionResult.Failure(e.message ?: "Error de red")
        }
    }

    suspend fun loadAllContent(): XtreamLoadResult {
        return try {
            val serverInfoResponse = service.getServerInfo(username, password)
            if (!serverInfoResponse.isSuccessful || serverInfoResponse.body()?.success != true) {
                return XtreamLoadResult.Failure("No se pudo obtener información del servidor")
            }
            val serverInfo = serverInfoResponse.body()!!.data!!

            // Load categories in parallel
            val liveCategoriesResponse = service.getLiveCategories(username, password)
            val vodCategoriesResponse = service.getVodCategories(username, password)
            val seriesCategoriesResponse = service.getSeriesCategories(username, password)

            val allCategories = mutableListOf<XtreamCategory>()
            liveCategoriesResponse.body()?.data?.forEach { allCategories.add(it.copy(category_id = "live_${it.category_id}")) }
            vodCategoriesResponse.body()?.data?.forEach { allCategories.add(it.copy(category_id = "vod_${it.category_id}")) }
            seriesCategoriesResponse.body()?.data?.forEach { allCategories.add(it.copy(category_id = "series_${it.category_id}")) }

            // Load streams for each category
            val allStreams = mutableListOf<XtreamStream>()

            // Live streams
            val liveStreamsResponse = service.getLiveStreams(username, password)
            liveStreamsResponse.body()?.data?.forEach { allStreams.add(it) }

            // VOD streams
            val vodStreamsResponse = service.getVodStreams(username, password)
            vodStreamsResponse.body()?.data?.forEach { allStreams.add(it) }

            // Series streams
            val seriesStreamsResponse = service.getSeriesStreams(username, password)
            seriesStreamsResponse.body()?.data?.forEach { allStreams.add(it) }

            XtreamLoadResult.Success(
                serverInfo = serverInfo,
                categories = allCategories,
                streams = allStreams,
            )
        } catch (e: Exception) {
            XtreamLoadResult.Failure(e.message ?: "Error cargando contenido")
        }
    }

    fun toDomainModels(
        playlistId: String,
        playlistName: String,
        loadResult: XtreamLoadResult.Success,
    ): Pair<List<Channel>, List<Category>> {
        val streams = loadResult.streams
        val categories = loadResult.categories

        val categoryMap = categories.associateBy { it.category_id } { it.category_name }

        val channels = streams.map { stream ->
            val streamUrl = buildStreamUrl(stream)
            val catName = categoryMap[stream.category_id ?: ""] ?: "General"
            Channel(
                id = "xtream_${stream.stream_id}",
                name = stream.name,
                url = streamUrl,
                logo = stream.stream_icon,
                group = catName,
                tvgId = stream.epg_channel_id,
                tvgName = stream.name,
                tvgLogo = stream.stream_icon,
                isRadio = stream.stream_type == "radio",
                isLive = stream.is_live?.toBoolean() ?: (stream.stream_type == "live"),
                categories = listOf(catName),
            )
        }

        val domainCategories = categories.map { cat ->
            val catStreams = streams.filter { categoryMap[it.category_id ?: ""] == cat.category_name }
            Category(
                id = cat.category_id,
                name = cat.category_name,
                order = catStreams.size,
                channelIds = catStreams.map { "xtream_${it.stream_id}" },
                isLiveTv = cat.category_id.startsWith("live_"),
                isVod = cat.category_id.startsWith("vod_"),
                isSeries = cat.category_id.startsWith("series_"),
            )
        }

        return channels to domainCategories
    }

    private fun buildStreamUrl(stream: XtreamStream): String {
        val extension = stream.container_extension ?: "m3u8"
        return "$baseUrl/$username/$password/${stream.stream_id}.$extension"
    }
}

sealed class XtreamConnectionResult {
    data class Success(val serverInfo: XtreamServerInfo?) : XtreamConnectionResult()
    data class Failure(val message: String) : XtreamConnectionResult()
}

sealed class XtreamLoadResult {
    data class Success(
        val serverInfo: XtreamServerInfo,
        val categories: List<XtreamCategory>,
        val streams: List<XtreamStream>,
    ) : XtreamLoadResult()
    data class Failure(val message: String) : XtreamLoadResult()
}

// Factory class for DI
class XtreamApiClientFactory {
    fun create(baseUrl: String, username: String, password: String): XtreamApiClient {
        return XtreamApiClient.create(baseUrl, username, password)
    }
}