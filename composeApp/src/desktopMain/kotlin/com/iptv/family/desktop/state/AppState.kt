package com.iptv.family.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iptv.family.shared.data.repository.LibraryRepository
import com.iptv.family.shared.data.store.KeyValueStore
import com.iptv.family.shared.data.xmltv.CommonEpgCache
import com.iptv.family.shared.log.AppLog
import com.iptv.family.shared.model.Category
import com.iptv.family.shared.model.CategoryType
import com.iptv.family.shared.model.Channel
import com.iptv.family.shared.model.EPGProgram
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

    /** Guia EPG de la playlist activa (XMLTV), cacheada en shared. */
    private val epgCache = CommonEpgCache()

    /** Counter para recomponer la UI cuando la guia se (re)carga o pasa el tiempo. */
    var epgTick by mutableStateOf(0L)
        private set

    val isEpgLoaded: Boolean get() = epgCache.isLoaded

    val selectedPlaylist: Playlist? get() = playlists.find { it.id == selectedPlaylistId }

    suspend fun loadAll() {
        withContext(Dispatchers.IO) {
            playlists = repository.loadPlaylists()
            settings = repository.loadSettings()
            favorites = repository.loadFavorites()
        }
        // Reabrir la lista que estaba en uso; si no hay, la primera disponible.
        val restore = settings.selectedPlaylistId?.takeIf { id -> playlists.any { it.id == id } }
            ?: playlists.firstOrNull()?.id
        if (restore != null) selectPlaylist(restore)
    }

    private fun newId(): String =
        "pl-${System.currentTimeMillis()}-${Random.nextInt(1000)}"

    // ------------------------------------------------------------------
    // Alta de playlists
    // ------------------------------------------------------------------

    suspend fun addM3uUrl(name: String, url: String, epgUrl: String? = null) {
        AppLog.d("AppState", "addM3uUrl: name='$name' url=${AppLog.redactUrl(url)}")
        require(name.isNotBlank()) { "El nombre es obligatorio" }
        val pl = Playlist(
            id = newId(),
            name = name,
            type = SourceType.M3U_URL,
            m3uUrl = url,
            epgUrl = epgUrl?.trim()?.takeIf { it.isNotEmpty() },
            isActive = true,
            lastUpdated = System.currentTimeMillis(),
        )
        playlists = playlists + pl
        repository.savePlaylists(playlists)
        selectPlaylist(pl.id)
    }

    suspend fun addXtream(name: String, url: String, user: String, pass: String, epgUrl: String? = null) {
        AppLog.d("AppState", "addXtream: name='$name' url=${AppLog.redactUrl(url)} user='$user'")
        require(name.isNotBlank()) { "El nombre es obligatorio" }
        require(url.isNotBlank() && user.isNotBlank()) { "URL y usuario son obligatorios" }
        val pl = Playlist(
            id = newId(),
            name = name,
            type = SourceType.XTREAM,
            xtreamUrl = url,
            epgUrl = epgUrl?.trim()?.takeIf { it.isNotEmpty() },
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
        AppLog.d("AppState", "addM3uFile: name='$name' ${content.length} bytes")
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
        if (settings.selectedPlaylistId != id) {
            mutateSettings { copy(selectedPlaylistId = id) }
        }
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

    // ------------------------------------------------------------------
    // EPG (guia de programas)
    // ------------------------------------------------------------------

    /**
     * URL de la guia XMLTV de la playlist: la que puso el usuario o, en
     * Xtream sin URL explicita, la estandar del panel (`xmltv.php`), que
     * comparte host/credenciales con la propia lista.
     */
    fun epgUrlFor(playlist: Playlist?): String? {
        playlist ?: return null
        playlist.epgUrl?.trim().takeUnless { it.isNullOrEmpty() }?.let { return it }
        if (playlist.type == SourceType.XTREAM) {
            val base = playlist.xtreamUrl?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
            val scheme = if (base.startsWith("https://", true)) "https" else "http"
            val host = base.removePrefix("https://").removePrefix("http://").trimEnd('/')
            return "$scheme://$host/xmltv.php?username=${playlist.xtreamUser}&password=${playlist.xtreamPass}"
        }
        return null
    }

    /** Descarga la guia si toca (TTL interno). Si falla, se sigue sin EPG. */
    suspend fun loadEpg(forceRefresh: Boolean = false) {
        epgCache.ensureLoaded(epgUrlFor(selectedPlaylist), forceRefresh)
        epgTick = System.currentTimeMillis()
    }

    /** Fuerza a recomponer las filas con EPG (refresco periodico de "Ahora"). */
    fun bumpEpgTick() {
        epgTick = System.currentTimeMillis()
    }

    fun currentProgram(channel: Channel?): EPGProgram? =
        channel?.let { epgCache.currentFor(it.epgChannelId) }

    fun nextProgram(channel: Channel?): EPGProgram? =
        channel?.let { epgCache.nextFor(it.epgChannelId) }

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
                AppLog.d("AppState", "applyResult: ${result.channels.size} canales cargados")
                channels = result.channels.map { ch ->
                    if (isFavorite(ch.id)) ch.copy(isFavorite = true) else ch
                }
                categories = result.categories
                normalizeCategories()
            }
            is LibraryRepository.ChannelsResult.Error -> {
                AppLog.w("AppState", "applyResult: ${result.message}")
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
        // Invalidar la cache de nombres: se reconstruye perezosamente al pedirla.
        groupNamesById = emptyMap()
    }

    fun channelsFor(categoryId: String): List<Channel> =
        if (categoryId == "all") channels else channels.filter { it.group == categoryId }

    /**
     * Nombre legible del grupo de un canal.
     *
     * `Channel.group` es el ID de la categoria: en Xtream es un numero ("142"),
     * asi que pintarlo tal cual mostraba ese numero donde deberia ir "Deportes".
     * El mapa se recalcula solo cuando cambian las categorias, no por fila.
     */
    private var groupNamesById: Map<String, String> = emptyMap()

    fun groupName(channel: Channel): String? {
        val gid = channel.group ?: return null
        if (groupNamesById.isEmpty() && categories.isNotEmpty()) {
            groupNamesById = categories.associate { it.id to it.name }
        }
        return groupNamesById[gid] ?: gid
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

