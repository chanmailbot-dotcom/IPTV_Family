package com.iptv.family.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iptv.family.shared.data.repository.LibraryRepository
import com.iptv.family.shared.data.store.KeyValueStore
import com.iptv.family.shared.model.Category
import com.iptv.family.shared.model.CategoryType
import com.iptv.family.shared.model.Channel
import com.iptv.family.shared.model.FavoriteChannel
import com.iptv.family.shared.model.Playlist
import com.iptv.family.shared.model.SourceType
import com.iptv.family.shared.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Estado de la aplicacion de escritorio.
 * Se sustenta sobre [LibraryRepository] del modulo shared (KMP), por lo que la
 * logica de parseo M3U/Xtream, persistencia y EPG se comparte con la app Android.
 *
 * NO usa Room (no disponible en desktop), sino el FileKeyValueStore del shared.
 */
class AppState(
    private val repository: LibraryRepository,
) {
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var settings by mutableStateOf(UserSettings())
        private set
    var favorites by mutableStateOf<List<FavoriteChannel>>(emptyList())
        private set
    var channels by mutableStateOf<List<Channel>>(emptyList())
        private set
    var categories by mutableStateOf<List<Category>>(emptyList())
        private set
    var selectedPlaylistId by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val selectedPlaylist: Playlist? get() = playlists.find { it.id == selectedPlaylistId }

    suspend fun loadAll() = withContext(Dispatchers.IO) {
        playlists = repository.loadPlaylists()
        settings = repository.loadSettings()
        favorites = repository.loadFavorites()
    }

    private fun newId(): String =
        "pl-${System.currentTimeMillis()}-${Random.nextInt(1000)}"

    // ------------------------------------------------------------------
    // Alta de playlists
    // ------------------------------------------------------------------

    suspend fun addM3uUrl(name: String, url: String) {
        require(name.isNotBlank()) { "El nombre es obligatorio" }
        val pl = Playlist(
            id = newId(),
            name = name,
            type = SourceType.M3U_URL,
            m3uUrl = url,
            isActive = true,
            lastUpdated = System.currentTimeMillis(),
        )
        playlists = playlists + pl
        repository.savePlaylists(playlists)
        selectPlaylist(pl.id)
    }

    suspend fun addXtream(name: String, url: String, user: String, pass: String) {
        require(name.isNotBlank()) { "El nombre es obligatorio" }
        require(url.isNotBlank() && user.isNotBlank()) { "URL y usuario son obligatorios" }
        val pl = Playlist(
            id = newId(),
            name = name,
            type = SourceType.XTREAM,
            xtreamUrl = url,
            xtreamUser = user,
            xtreamPass = pass,
            isActive = true,
            lastUpdated = System.currentTimeMillis(),
        )
        playlists = playlists + pl
        repository.savePlaylists(playlists)
        selectPlaylist(pl.id)
    }

    suspend fun addM3uFile(name: String, content: String) {
        val id = newId()
        repository.storeFileContent(id, content)
        val pl = Playlist(
            id = id,
            name = name,
            type = SourceType.M3U_FILE,
            isActive = true,
            lastUpdated = System.currentTimeMillis(),
        )
        playlists = playlists + pl
        repository.savePlaylists(playlists)
        selectPlaylist(pl.id)
    }

    // ------------------------------------------------------------------
    // Seleccion / carga de canales
    // ------------------------------------------------------------------

    suspend fun selectPlaylist(id: String?) {
        selectedPlaylistId = id
        error = null
        val pl = playlists.find { it.id == id }
        if (pl == null) {
            channels = emptyList()
            categories = emptyList()
            return
        }
        loadChannels(pl)
    }

    /** Variante offline: carga canales a partir del contenido crudo de un M3U. */
    suspend fun selectPlaylistContent(id: String, content: String) {
        selectedPlaylistId = id
        error = null
        isLoading = true
        val result = repository.loadChannelsFromContent(content)
        applyResult(result)
        isLoading = false
    }

    suspend fun refresh() {
        val id = selectedPlaylistId
        if (id != null) {
            val pl = playlists.find { it.id == id }
            if (pl != null) loadChannels(pl)
        }
    }

    suspend fun deletePlaylist(id: String) {
        playlists = repository.deletePlaylist(id)
        if (selectedPlaylistId == id) {
            selectedPlaylistId = null
            channels = emptyList()
            categories = emptyList()
        }
    }

    private suspend fun loadChannels(playlist: Playlist) = withContext(Dispatchers.IO) {
        isLoading = true
        error = null
        val result = repository.buildChannels(playlist)
        applyResult(result)
        isLoading = false
    }

    private fun applyResult(result: LibraryRepository.ChannelsResult) {
        when (result) {
            is LibraryRepository.ChannelsResult.Ok -> {
                channels = result.channels.map { ch ->
                    if (isFavorite(ch.id)) ch.copy(isFavorite = true) else ch
                }
                categories = result.categories
                normalizeCategories()
            }
            is LibraryRepository.ChannelsResult.Error -> {
                error = result.message
                channels = emptyList()
                categories = emptyList()
            }
        }
    }

    private fun normalizeCategories() {
        val cur = categories.toMutableList()
        val all = Category(
            id = "all",
            name = "Todas",
            type = CategoryType.LIVE,
            channels = channels.map { it.id },
        )
        if (cur.none { it.id == "all" }) cur.add(0, all) else {
            cur.replaceAll { if (it.id == "all") all else it }
        }
        categories = cur
    }

    fun channelsFor(categoryId: String): List<Channel> =
        when (categoryId) {
            null, "all" -> channels
            else -> channels.filter { it.group == categoryId }
        }

    fun isFavorite(channelId: String): Boolean =
        favorites.any { it.channelId == channelId && it.playlistId == selectedPlaylistId }

    suspend fun toggleFavorite(channelId: String) {
        val current = isFavorite(channelId)
        val updated = repository.toggleFavorite(channelId, selectedPlaylistId.orEmpty(), !current)
        favorites = updated
        channels = channels.map { ch ->
            if (ch.id == channelId) ch.copy(isFavorite = !current) else ch
        }
    }

    suspend fun mutateSettings(block: UserSettings.() -> UserSettings) {
        settings = settings.block()
        repository.saveSettings(settings)
    }

    suspend fun renamePlaylist(id: String, newName: String) {
        val pl = playlists.find { it.id == id } ?: return
        playlists = playlists.map { if (it.id == id) pl.copy(name = newName) else it }
        repository.savePlaylists(playlists)
    }
}

fun newInMemoryAppState(): AppState {
    val store = object : KeyValueStore {
        private val map = mutableMapOf<String, String>()
        override fun write(key: String, value: String) { map[key] = value }
        override fun read(key: String): String? = map[key]
        override fun delete(key: String) { map.remove(key) }
    }
    return AppState(LibraryRepository(store))
}

