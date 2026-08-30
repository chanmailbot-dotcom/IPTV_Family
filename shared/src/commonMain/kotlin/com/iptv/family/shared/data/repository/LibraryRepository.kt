package com.iptv.family.shared.data.repository

import com.iptv.family.shared.data.m3u.M3UParser
import com.iptv.family.shared.data.store.KeyValueStore
import com.iptv.family.shared.data.xtream.XtreamApiClient
import com.iptv.family.shared.log.AppLog
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

    /**
     * Lee y deserializa, y si el contenido esta corrupto INTENTA RECUPERARLO en
     * vez de devolver el valor por defecto en silencio.
     *
     * Antes, un JSON truncado se convertia en lista vacia sin decir nada, y el
     * siguiente guardado lo sobrescribia: adios listas y credenciales. Ahora:
     *   1. si el fichero no parsea, se prueba con la copia de la escritura anterior;
     *   2. si esa vale, se restaura y se sigue como si nada;
     *   3. si tampoco, el fichero ilegible se aparta (no se pisa) y queda en el log.
     */
    private inline fun <reified T> loadOrRecover(key: String, defecto: () -> T): T {
        val raw = store.read(key) ?: return defecto()

        runCatching { json.decodeFromString<T>(raw) }
            .onSuccess { return it }
            .onFailure { AppLog.e("Library", "'$key' no se puede leer: ${it.message}") }

        val copia = store.readBackup(key)
        if (copia != null) {
            runCatching { json.decodeFromString<T>(copia) }.onSuccess {
                AppLog.w("Library", "'$key' recuperado desde la copia anterior")
                runCatching { store.write(key, copia) }
                return it
            }
        }

        // Sin copia utilizable: se aparta para que el proximo guardado no lo pise.
        store.quarantine(key)
        AppLog.e("Library", "'$key' se ha perdido; se arranca con los valores por defecto")
        return defecto()
    }

    // ------------------------------------------------------------------
    // Playlists
    // ------------------------------------------------------------------

    suspend fun loadPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        loadOrRecover<List<Playlist>>(KEY_PLAYLISTS) { emptyList() }
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
        AppLog.d("Library", "buildChannels: playlist='${playlist.name}' type=${playlist.type}")
        val result = when (playlist.type) {
            SourceType.M3U_URL -> {
                try {
                    val raw = playlist.m3uUrl.orEmpty().trim()
                    val url = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else "http://$raw"
                    AppLog.d("Library", "M3U_URL: descargando ${AppLog.redactUrl(url)}")
                    val content = fetch(url)
                    AppLog.d("Library", "M3U_URL: ${content.length} bytes descargados")
                    m3uToResult(content)
                } catch (e: Exception) {
                    AppLog.e("Library", "M3U_URL: fallo al descargar/parsear", e)
                    ChannelsResult.Error("No se pudo descargar la lista: ${e.message}")
                }
            }

            SourceType.M3U_FILE -> {
                try {
                    val content = store.read("file_${playlist.id}.m3u").orEmpty()
                    AppLog.d("Library", "M3U_FILE: ${content.length} bytes leídos de disco")
                    m3uToResult(content)
                } catch (e: Exception) {
                    AppLog.e("Library", "M3U_FILE: fallo al leer/parsear", e)
                    ChannelsResult.Error("No se pudo leer el archivo: ${e.message}")
                }
            }

            SourceType.XTREAM -> {
                val client = XtreamApiClient(
                    baseUrl = playlist.xtreamUrl.orEmpty(),
                    username = playlist.xtreamUser.orEmpty(),
                    password = playlist.xtreamPass.orEmpty()
                )
                AppLog.d("Library", "XTREAM: login en ${AppLog.redactUrl(playlist.xtreamUrl.orEmpty())}")
                val login = client.login()
                if (!login.success) {
                    AppLog.w("Library", "XTREAM: login fallido - ${login.error}")
                    ChannelsResult.Error(login.error ?: "Login Xtream fallido")
                } else {
                    AppLog.d("Library", "XTREAM: login ok, status=${login.status} exp=${login.expDate}")
                    try {
                        val groups = mutableMapOf<String, String>()
                        (client.getLiveCategories() + client.getVodCategories() + client.getSeriesCategories())
                            .forEach { groups[it.id] = it.name }

                        val live = client.getLiveStreams()
                        val vod = client.getVodStreams()
                        val series = client.getSeriesStreams()
                        AppLog.d("Library", "XTREAM: live=${live.size} vod=${vod.size} series=${series.size}")
                        val channels = sortByChannelNumber(live + vod + series)

                        // Antes se construia un Category POR CANAL (40.000 objetos) y
                        // luego se hacia .distinct() para tirar casi todos. Aqui se
                        // agrupa una sola vez, y cada categoria lleva ya sus canales
                        // (la UI de escritorio usa ese tamaño para el contador, que
                        // con la version anterior salia siempre vacio en Xtream).
                        val byGroup = channels.groupBy { it.group ?: "general" }
                        ChannelsResult.Ok(
                            channels = channels,
                            categories = byGroup.map { (groupId, groupChannels) ->
                                Category(
                                    id = groupId,
                                    name = groups[groupId] ?: "General",
                                    type = groupChannels.first().categoryType,
                                    channels = groupChannels.map { it.id },
                                )
                            }
                        )
                    } catch (e: Exception) {
                        AppLog.e("Library", "XTREAM: fallo cargando streams", e)
                        ChannelsResult.Error("Error cargando Xtream: ${e.message}")
                    }
                }
            }
        }
        when (result) {
            is ChannelsResult.Ok -> AppLog.d(
                "Library",
                "buildChannels: OK ${result.channels.size} canales, ${result.categories.size} categorías"
            )
            is ChannelsResult.Error -> AppLog.w("Library", "buildChannels: ERROR ${result.message}")
        }
        result
    }

    /**
     * Episodios reproducibles de una serie Xtream (ver comentario en
     * [XtreamApiClient.getSeriesEpisodes]: el `series_id` de la lista de
     * series NO es reproducible por si mismo, hace falta esta llamada aparte).
     * Solo tiene sentido para playlists Xtream; para M3U devuelve vacio.
     */
    suspend fun getSeriesEpisodes(playlist: Playlist, seriesId: String): List<Channel> {
        if (playlist.type != SourceType.XTREAM) return emptyList()
        val client = XtreamApiClient(
            baseUrl = playlist.xtreamUrl.orEmpty(),
            username = playlist.xtreamUser.orEmpty(),
            password = playlist.xtreamPass.orEmpty()
        )
        return client.getSeriesEpisodes(seriesId)
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
        loadOrRecover<UserSettings>(KEY_SETTINGS) { UserSettings() }
    }

    suspend fun saveSettings(settings: UserSettings) = withContext(Dispatchers.IO) {
        store.write(KEY_SETTINGS, json.encodeToString(UserSettings.serializer(), settings))
    }

    // ------------------------------------------------------------------
    // Favoritos
    // ------------------------------------------------------------------

    suspend fun loadFavorites(): List<FavoriteChannel> = withContext(Dispatchers.IO) {
        loadOrRecover<List<FavoriteChannel>>(KEY_FAVORITES) { emptyList() }
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
        if (!content.contains("#EXTM3U") && !content.contains("#EXTINF")) {
            return ChannelsResult.Error(
                "La URL no devolvió una lista M3U (¿la contraseña o el enlace caducó?)."
            )
        }
        val parsed = m3uParser.parse(content)
        return ChannelsResult.Ok(
            channels = sortByChannelNumber(parsed.channels),
            categories = parsed.categories
        )
    }

    /**
     * Orden que el usuario espera ver: por el numero de dial que publica el
     * proveedor (`num` en Xtream, `tvg-chno` en M3U) y, para los que no lo
     * traen, alfabetico al final. Sin esto la lista sale en el orden crudo de
     * la respuesta del panel, que no sigue ningun criterio.
     *
     * Primero por tipo: en Xtream la numeracion se reinicia en cada seccion
     * (hay un canal 1 de TV, una pelicula 1 y una serie 1), asi que ordenar
     * solo por numero intercalaria las tres cosas en la misma lista.
     */
    private fun sortByChannelNumber(channels: List<Channel>): List<Channel> =
        channels.sortedWith(
            compareBy(
                { it.categoryType.ordinal },
                { it.number ?: Int.MAX_VALUE },
                { it.name.lowercase() },
            )
        )

    /**
     * HttpURLConnection sigue redirecciones automaticamente solo si el protocolo no
     * cambia: muchos paneles M3U dan una URL http que en realidad redirige a https
     * (o a otro host), y sin seguirla a mano el body es el 302 en si mismo (vacio),
     * lo que antes se colaba como "lista con 0 canales" sin ningun error visible.
     */
    private fun fetch(url: String, redirectsLeft: Int = 5): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "IPTV-Family/1.0")
        }
        try {
            val code = connection.responseCode
            AppLog.d("Library", "fetch: ${AppLog.redactUrl(url)} -> HTTP $code")
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: throw IllegalStateException("Redirección ($code) sin destino")
                if (redirectsLeft <= 0) throw IllegalStateException("Demasiadas redirecciones")
                val next = if (location.startsWith("http")) location else URL(URL(url), location).toString()
                AppLog.d("Library", "fetch: redirige a ${AppLog.redactUrl(next)}")
                return fetch(next, redirectsLeft - 1)
            }
            if (code !in 200..299) {
                throw IllegalStateException("El servidor respondió $code")
            }
            return connection.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
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