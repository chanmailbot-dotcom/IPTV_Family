package com.iptv.family.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iptv.family.shared.data.repository.LibraryRepository
import com.iptv.family.shared.log.AppLog
import com.iptv.family.shared.util.textoAntiguedad
import com.iptv.family.shared.data.xmltv.CommonEpgCache
import com.iptv.family.shared.model.Category
import com.iptv.family.shared.model.CategoryType
import com.iptv.family.shared.model.Channel
import com.iptv.family.shared.model.EPGProgram
import com.iptv.family.shared.model.FavoriteChannel
import com.iptv.family.shared.model.Playlist
import com.iptv.family.shared.model.SourceType
import com.iptv.family.shared.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Estado de la aplicacion Android/Fire TV, sobre [LibraryRepository] del modulo
 * shared (KMP) -- misma logica de parseo M3U/Xtream, persistencia y EPG que la
 * app de escritorio. Es deliberadamente una copia de
 * `composeApp/.../desktop/state/AppState.kt`: no depende de nada especifico de
 * escritorio, asi que duplicarla aqui evita acoplar `shared` a Compose runtime.
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

    /**
     * Aviso de que el catalogo que se esta viendo es la copia guardada, porque
     * no se pudo hablar con el proveedor. Enseñar una lista de hace dias como
     * si fuera la de ahora es peor que un error: el usuario no entiende por que
     * faltan canales ni por que alguno no abre.
     */
    var avisoSinConexion by mutableStateOf<String?>(null)
        private set

    // EPG (guia XMLTV) cacheada en shared; se comparte con el escritorio.
    private val epgCache = CommonEpgCache()
    /** Marca de refresco: las filas que muestran "Ahora" dependen de ella. */
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
        val restore = settings.selectedPlaylistId?.takeIf { id -> playlists.any { it.id == id } }
            ?: playlists.firstOrNull()?.id
        if (restore != null) selectPlaylist(restore)
    }

    private fun newId(): String =
        "pl-${System.currentTimeMillis()}-${Random.nextInt(1000)}"

    suspend fun addM3uUrl(name: String, url: String, epgUrl: String? = null) {
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
        require(name.isNotBlank()) { "El nombre es obligatorio" }
        require(url.isNotBlank() && user.isNotBlank()) { "URL y usuario son obligatorios" }
        val pl = Playlist(
            id = newId(),
            name = name,
            type = SourceType.XTREAM,
            xtreamUrl = url,
            xtreamUser = user,
            xtreamPass = pass,
            epgUrl = epgUrl?.trim()?.takeIf { it.isNotEmpty() },
            isActive = true,
            lastUpdated = System.currentTimeMillis(),
        )
        playlists = playlists + pl
        repository.savePlaylists(playlists)
        selectPlaylist(pl.id)
    }

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

    /**
     * Carga en marcha, para no repetirla. Al entrar en una seccion se dispara la
     * carga de la lista activa desde mas de un sitio a la vez (el arranque y la
     * propia pantalla), y sin este candado eso son dos descargas y dos parseos
     * de decenas de miles de canales compitiendo -- ademas de dos escrituras
     * simultaneas del catalogo en disco.
     */
    private val cargaLock = Mutex()
    private var cargandoPlaylistId: String? = null

    private suspend fun loadChannels(playlist: Playlist) = withContext(Dispatchers.IO) {
        val yaEnMarcha = cargaLock.withLock {
            if (cargandoPlaylistId == playlist.id) true else { cargandoPlaylistId = playlist.id; false }
        }
        if (yaEnMarcha) {
            AppLog.d("AppState", "carga de '${playlist.name}' ya en marcha: no se repite")
            return@withContext
        }
        try {
            isLoading = true
            error = null
            val result = repository.buildChannels(playlist)
            applyResult(result)
        } finally {
            isLoading = false
            cargaLock.withLock { cargandoPlaylistId = null }
        }
    }

    private fun applyResult(result: LibraryRepository.ChannelsResult) {
        when (result) {
            is LibraryRepository.ChannelsResult.Ok -> {
                avisoSinConexion = result.guardadoEnMs?.let {
                    "Sin conexión con el proveedor: se muestra la lista guardada " +
                        "${textoAntiguedad(it)}."
                }
                error = null
                channels = result.channels.map { ch ->
                    if (isFavorite(ch.id)) ch.copy(isFavorite = true) else ch
                }
                categories = result.categories
                normalizeCategories()
            }
            is LibraryRepository.ChannelsResult.Error -> {
                avisoSinConexion = null
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
        if (categoryId == "all") channels else channels.filter { it.group == categoryId }

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

    /** Episodios reproducibles de una serie Xtream (vacio si la playlist es M3U). */
    suspend fun loadSeriesEpisodes(seriesId: String): List<Channel> {
        val playlist = selectedPlaylist ?: return emptyList()
        return repository.getSeriesEpisodes(playlist, seriesId)
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

    suspend fun mutateSettings(block: UserSettings.() -> UserSettings) {
        settings = settings.block()
        repository.saveSettings(settings)
    }
}
