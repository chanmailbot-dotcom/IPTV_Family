package com.iptv.family.shared.data.repository

import com.iptv.family.shared.data.m3u.M3UParser
import com.iptv.family.shared.data.store.KeyValueStore
import com.iptv.family.shared.data.xtream.XtreamApiClient
import com.iptv.family.shared.model.Category
import com.iptv.family.shared.model.Channel
import com.iptv.family.shared.model.FavoriteChannel
import com.iptv.family.shared.model.Playlist
import com.iptv.family.shared.model.SourceType
import com.iptv.family.shared.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.net.HttpURLConnection
import java.net.URL

/**
 * Orquesta el acceso a datos de la biblioteca (playlists, canales,
 * favoritos y ajustes) sobre un KeyValueStore. Compartido por desktop
 * y Android.
 */
class LibraryRepository(private val store: KeyValueStore) {

    private val json = Json { ignoreUnknownKeys = true }
    private val m3uParser = M3UParser()

    private companion object {
        const val KEY_PLAYLISTS = "playlists.json"
        const val KEY_SETTINGS = "settings.json"
        const val KEY_FAVORITES = "favorites.json"
    }

    // ------------------------------------------------------------------
    // Playlists
    // ------------------------------------------------------------------

    suspend fun loadPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val raw = store.read(KEY_PLAYLISTS) ?: return@withContext emptyList()
        try {
            json.decodeFromString<List<Playlist>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun savePlaylists(playlists: List<Playlist>) = withContext(Dispatchers.IO) {
        store.write(KEY_PLAYLISTS, json.encodeToString(serializer<List<Playlist>>(), playlists))
    }

    suspend fun deletePlaylist(id: String): List<Playlist> = withContext(Dispatchers.IO) {
        val remaining = loadPlaylists().filterNot { it.id == id }
        savePlaylists(remaining)
        remaining
    }

    /** Guarda el contenido de un archivo M3U bajado a local (para M3U_FILE). */
    suspend fun storeFileContent(id: String, content: String) = withContext(Dispatchers.IO) {
        store.write("file_$id.m3u", content)
    }

    /** Carga canales de un M3U (URL o contenido) o de Xtream Codes. */
    suspend fun buildChannels(playlist: Playlist): ChannelsResult = withContext(Dispatchers.IO) {
        when (playlist.type) {
            SourceType.M3U_URL -> {
                try {
                    val content = fetch(playlist.m3uUrl.orEmpty())
                    m3uToResult(content)
                } catch (e: Exception) {
                    ChannelsResult.Error("No se pudo descargar la lista: ${e.message}")
                }
            }

            SourceType.M3U_FILE -> {
                try {
                    val content = store.read("file_${playlist.id}.m3u").orEmpty()
                    m3uToResult(content)
                } catch (e: Exception) {
                    ChannelsResult.Error("No se pudo leer el archivo: ${e.message}")
                }
            }

            SourceType.XTREAM -> {
                val client = XtreamApiClient(
                    baseUrl = playlist.xtreamUrl.orEmpty(),
                    username = playlist.xtreamUser.orEmpty(),
                    password = playlist.xtreamPass.orEmpty()
                )
                val login = client.login()
                if (!login.success) {
                    ChannelsResult.Error(login.error ?: "Login Xtream fallido")
                } else {
                    try {
                        val groups = mutableMapOf<String, String>()
                        (client.getLiveCategories() + client.getVodCategories() + client.getSeriesCategories())
                            .forEach { groups[it.id] = it.name }

                        val channels = client.getLiveStreams() + client.getVodStreams() + client.getSeriesStreams()
                        ChannelsResult.Ok(
                            channels = channels,
                            categories = channels.map { ch ->
                                Category(
                                    id = ch.group ?: "general",
                                    name = groups[ch.group.orEmpty()] ?: "General",
                                    type = ch.categoryType,
                                    channels = emptyList()
                                )
                            }.distinct()
                        )
                    } catch (e: Exception) {
                        ChannelsResult.Error("Error cargando Xtream: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Parsea contenido M3U sin persistir (uso puntual).
     */
    fun loadChannelsFromContent(content: String): ChannelsResult = try {
        m3uToResult(content)
    } catch (e: Exception) {
        ChannelsResult.Error("No se pudo parsear el contenido: ${e.message}")
    }

    // ------------------------------------------------------------------
    // Ajustes
    // ------------------------------------------------------------------

    suspend fun loadSettings(): UserSettings = withContext(Dispatchers.IO) {
        val raw = store.read(KEY_SETTINGS) ?: return@withContext UserSettings()
        try {
            json.decodeFromString<UserSettings>(raw)
        } catch (e: Exception) {
            UserSettings()
        }
    }

    suspend fun saveSettings(settings: UserSettings) = withContext(Dispatchers.IO) {
        store.write(KEY_SETTINGS, json.encodeToString(UserSettings.serializer(), settings))
    }

    // ------------------------------------------------------------------
    // Favoritos
    // ------------------------------------------------------------------

    suspend fun loadFavorites(): List<FavoriteChannel> = withContext(Dispatchers.IO) {
        val raw = store.read(KEY_FAVORITES) ?: return@withContext emptyList()
        try {
            json.decodeFromString<List<FavoriteChannel>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun toggleFavorite(channelId: String, playlistId: String, isFavorite: Boolean): List<FavoriteChannel>
        = withContext(Dispatchers.IO) {
        var favorites = loadFavorites()
        val exists = favorites.any { it.channelId == channelId && it.playlistId == playlistId }

        if (isFavorite && !exists) {
            favorites = favorites + FavoriteChannel(channelId = channelId, playlistId = playlistId)
        } else if (!isFavorite) {
            favorites = favorites.filterNot { it.channelId == channelId && it.playlistId == playlistId }
        }
        store.write(KEY_FAVORITES, json.encodeToString(serializer<List<FavoriteChannel>>(), favorites))
        favorites
    }

    // ------------------------------------------------------------------
    // Privado
    // ------------------------------------------------------------------

    private fun m3uToResult(content: String): ChannelsResult {
        val parsed = m3uParser.parse(content)
        return ChannelsResult.Ok(
            channels = parsed.channels,
            categories = parsed.categories
        )
    }

    private fun fetch(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "IPTV-Family/1.0")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            stream?.bufferedReader()?.use { it.readText() } ?: ""
        } finally {
            connection.disconnect()
        }
    }

    sealed class ChannelsResult {
        data class Ok(
            val channels: List<Channel>,
            val categories: List<Category>
        ) : ChannelsResult()

        data class Error(val message: String) : ChannelsResult()
    }
}