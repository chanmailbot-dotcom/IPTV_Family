package com.iptv.family.shared.data.xtream

import com.iptv.family.shared.domain.model.Channel
import com.iptv.family.shared.domain.model.Category
import com.iptv.family.shared.domain.model.CategoryType
import com.iptv.family.shared.domain.model.EPGProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import java.net.HttpURLConnection
import java.net.URL

class XtreamApiClient(
    private val baseUrl: String,
    private val username: String,
    private val password: String
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val baseUrlClean: String
        get() = baseUrl.removeTrailingSlash()

    suspend fun login(): LoginResult {
        return withContext(Dispatchers.IO) {
            val url = URL("$baseUrlClean/player_api.php?username=$username&password=$password")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                return@withContext LoginResult(success = false, error = "HTTP $responseCode")
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            
            try {
                val jsonObj = json.decodeFromString<JsonObject>(response)
                val userInfo = jsonObj["user_info"] as? JsonObject
                val auth = userInfo?.get("auth")?.content ?: "0"
                
                if (auth == "1") {
                    LoginResult(success = true, userInfo = userInfo)
                } else {
                    LoginResult(success = false, error = "Credenciales inválidas")
                }
            } catch (e: Exception) {
                LoginResult(success = false, error = "Error parseando respuesta: ${e.message}")
            }
        }
    }

    suspend fun getLiveCategories(): List<Category> = getCategories("live")
    suspend fun getVodCategories(): List<Category> = getCategories("vod")
    suspend fun getSeriesCategories(): List<Category> = getCategories("series")

    private suspend fun getCategories(type: String): List<Category> {
        return withContext(Dispatchers.IO) {
            val url = URL("$baseUrlClean/player_api.php?username=$username&password=$password&action=get_${type}_categories")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            
            try {
                val jsonArray = json.decodeFromString<JsonArray>(response)
                jsonArray.map { obj ->
                    val catObj = obj as JsonObject
                    Category(
                        id = catObj["category_id"].content,
                        name = catObj["category_name"].content,
                        type = when (type) {
                            "live" -> CategoryType.LIVE
                            "vod" -> CategoryType.VOD
                            "series" -> CategoryType.SERIES
                            else -> CategoryType.LIVE
                        }
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun getLiveStreams(categoryId: String? = null): List<Channel> = getStreams("live", categoryId)
    suspend fun getVodStreams(categoryId: String? = null): List<Channel> = getStreams("vod", categoryId)
    suspend fun getSeriesStreams(categoryId: String? = null): List<Channel> = getStreams("series", categoryId)

    private suspend fun getStreams(type: String, categoryId: String?): List<Channel> {
        return withContext(Dispatchers.IO) {
            var urlStr = "$baseUrlClean/player_api.php?username=$username&password=$password&action=get_${type}_streams"
            categoryId?.let { urlStr += "&category_id=$it" }
            
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            
            try {
                val jsonArray = json.decodeFromString<JsonArray>(response)
                jsonArray.map { obj ->
                    val streamObj = obj as JsonObject
                    val streamId = streamObj["stream_id"]?.content ?: ""
                    val name = streamObj["name"]?.content ?: "Sin nombre"
                    val icon = streamObj["stream_icon"]?.content
                    val categoryIdStr = streamObj["category_id"]?.content
                    
                    Channel(
                        id = streamId,
                        name = name,
                        url = getStreamUrl(type, streamId),
                        logoUrl = icon,
                        group = categoryIdStr,
                        epgChannelId = streamId,
                        categoryType = when (type) {
                            "live" -> CategoryType.LIVE
                            "vod" -> CategoryType.VOD
                            "series" -> CategoryType.SERIES
                            else -> CategoryType.LIVE
                        }
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun getEPG(limit: Int = 1000): List<EPGProgram> {
        return withContext(Dispatchers.IO) {
            val url = URL("$baseUrlClean/player_api.php?username=$username&password=$password&action=get_epg&limit=$limit")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            
            try {
                val jsonObj = json.decodeFromString<JsonObject>(response)
                val epgArray = jsonObj["epg"] as? JsonArray
                
                epgArray?.map { obj ->
                    val epgObj = obj as JsonObject
                    EPGProgram(
                        id = epgObj["id"]?.content ?: "",
                        channelId = epgObj["channel_id"]?.content ?: "",
                        title = epgObj["title"]?.content ?: "",
                        description = epgObj["description"]?.content,
                        startTime = epgObj["start"]?.content?.toLong() ?: 0,
                        endTime = epgObj["end"]?.content?.toLong() ?: 0,
                        category = epgObj["category"]?.content,
                        iconUrl = epgObj["icon"]?.content
                    )
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun getStreamUrl(type: String, streamId: String): String {
        val extension = when (type) {
            "live" -> "m3u8"
            "vod" -> "mp4"
            "series" -> "mp4"
            else -> "m3u8"
        }
        return "$baseUrlClean/$type/$username/$password/$streamId.$extension"
    }

    data class LoginResult(
        val success: Boolean,
        val error: String? = null,
        val userInfo: JsonObject? = null
    )
}