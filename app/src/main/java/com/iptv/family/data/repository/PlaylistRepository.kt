package com.iptv.family.data.repository

import com.iptv.family.data.m3u.M3UParser
import com.iptv.family.data.xtream.XtreamApiClient
import com.iptv.family.domain.model.Channel
import com.iptv.family.domain.model.Category
import com.iptv.family.domain.model.Playlist
import com.iptv.family.domain.model.PlaylistType
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

class PlaylistRepository(
    private val m3uParser: M3UParser,
    private val xtreamApiClient: XtreamApiClient?
) {

    /**
     * Parsea una lista M3U desde InputStream
     */
    suspend fun parseM3u(input: InputStream, playlist: Playlist): ParseResult {
        return try {
            val result = m3uParser.parse(input)
            ParseResult.Success(
                channels = result.channels,
                categories = result.categories
            )
        } catch (e: Exception) {
            ParseResult.Error(e.message ?: "Unknown error parsing M3U")
        }
    }

    /**
     * Obtiene canales de Xtream API
     */
    suspend fun getXtreamChannels(playlist: Playlist): ParseResult {
        val client = xtreamApiClient ?: return ParseResult.Error(
            "Xtream client not initialized. Check credentials."
        )
        return try {
            val liveChannels = client.getLiveStreams()
            val vodChannels = client.getVodStreams()
            val seriesChannels = client.getSeriesStreams()
            val categories = client.getCategories("live") +
                    client.getCategories("vod") +
                    client.getCategories("series")

            ParseResult.Success(
                channels = liveChannels + vodChannels + seriesChannels,
                categories = categories
            )
        } catch (e: Exception) {
            ParseResult.Error(e.message ?: "Unknown error fetching Xtream data")
        }
    }
}

sealed class ParseResult {
    data class Success(
        val channels: List<Channel>,
        val categories: List<Category>
    ) : ParseResult()

    data class Error(val message: String) : ParseResult()
}