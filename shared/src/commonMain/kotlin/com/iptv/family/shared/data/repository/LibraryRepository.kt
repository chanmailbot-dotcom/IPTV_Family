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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import com.iptv.family.shared.data.store.SecretVault
import com.iptv.family.shared.data.store.protectOrPlain
import com.iptv.family.shared.data.store.revealOrPlain

/**
 * Un paso de migracion de datos guardados.
 *
 * @param from version DESDE la que migra. Aplicar la migracion deja los datos
 *   en `from + 1`.
 */
class SchemaMigration(val from: Int, val descripcion: String, val apply: (KeyValueStore) -> Unit)

/**
 * Orquesta el acceso a datos de la biblioteca (playlists, canales,
 * favoritos y ajustes) sobre un KeyValueStore. Compartido por desktop
 * y Android.
 */
class LibraryRepository(
    private val store: KeyValueStore,
    /**
     * Migraciones a aplicar, en orden. Se inyectan para poder probarlas; en
     * produccion es la lista de abajo, hoy vacia porque el formato actual es el
     * primero. Al cambiar el formato de `playlists.json` o `settings.json` se
     * añade aqui un paso y la version sube sola.
     */
    private val migrations: List<SchemaMigration> = DEFAULT_MIGRATIONS,
    /**
     * Protege la contraseña de Xtream en disco. La implementacion la pone cada
     * plataforma (DPAPI en Windows, Keystore en Android); por defecto no cifra,
     * y avisa en el log.
     */
    private val vault: SecretVault = SecretVault.NONE,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val m3uParser = M3UParser()
    private val migrationLock = Mutex()
    private var migrated = false

    /** Version a la que llegan los datos tras aplicar todas las migraciones. */
    private val currentSchemaVersion: Int = (migrations.maxOfOrNull { it.from + 1 } ?: 1)

    @Serializable
    private data class SchemaInfo(val version: Int)

    companion object {
        val DEFAULT_MIGRATIONS: List<SchemaMigration> = emptyList()

        /** Marcador que sustituye a las credenciales en la copia del catalogo. */
        private const val MARCA_SECRETO = "{{secreto:"
    }

    private object Keys {
        const val PLAYLISTS = "playlists.json"
        const val SETTINGS = "settings.json"
        const val FAVORITES = "favorites.json"
        const val SCHEMA = "schema.json"
    }

    /**
     * Lleva los datos guardados al formato actual antes de leerlos.
     *
     * Sin esto, el primer campo que se renombre o se quite rompe las
     * instalaciones existentes, y con la aplicacion publicada eso ya no se
     * arregla borrando un fichero a mano. Se llama sola desde cada lectura, para
     * que ningun sitio pueda olvidarse.
     */
    suspend fun migrateIfNeeded() = migrationLock.withLock {
        if (migrated) return@withLock
        migrated = true

        val instalacionNueva = store.read(Keys.SCHEMA) == null && store.read(Keys.PLAYLISTS) == null
        val guardada = when {
            // Instalacion limpia: nace ya en la version actual.
            instalacionNueva -> currentSchemaVersion
            // Datos anteriores a que existiera el sello: son la version 1.
            store.read(Keys.SCHEMA) == null -> 1
            else -> loadOrRecover<SchemaInfo>(Keys.SCHEMA) { SchemaInfo(1) }.version
        }

        if (guardada > currentSchemaVersion) {
            // Datos escritos por una version mas nueva de la aplicacion. Tocarlos
            // seria peor que no hacer nada.
            AppLog.w(
                "Library",
                "los datos son de un formato mas nuevo (v$guardada > v$currentSchemaVersion); " +
                    "se dejan como estan"
            )
            return@withLock
        }

        val pendientes = migrations.filter { it.from >= guardada }.sortedBy { it.from }
        for (paso in pendientes) {
            AppLog.d("Library", "migrando datos v${paso.from} -> v${paso.from + 1}: ${paso.descripcion}")
            runCatching { paso.apply(store) }.onFailure {
                AppLog.e("Library", "fallo migrando de v${paso.from}", it)
                return@withLock // no se sella una version que no se alcanzo
            }
        }
        if (guardada != currentSchemaVersion || store.read(Keys.SCHEMA) == null) {
            store.write(Keys.SCHEMA, json.encodeToString(SchemaInfo.serializer(), SchemaInfo(currentSchemaVersion)))
        }
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
        migrateIfNeeded()
        val guardadas = loadOrRecover<List<Playlist>>(Keys.PLAYLISTS) { emptyList() }

        // Si alguna contraseña sigue en claro (instalacion anterior) y esta
        // plataforma sabe cifrar, se reescribe AHORA. Esperar al proximo guardado
        // no valdria: las listas solo se guardan al añadir o renombrar una, asi
        // que quien ya tenia la suya se habria quedado en claro para siempre.
        val hayEnClaro = guardadas.any {
            !it.xtreamPass.isNullOrEmpty() && !it.xtreamPass!!.startsWith(SecretVault.PREFIX)
        }
        if (hayEnClaro && vault.protect("x") != null) {
            AppLog.d("Library", "cifrando credenciales que estaban en claro")
            runCatching {
                store.write(
                    Keys.PLAYLISTS,
                    json.encodeToString(serializer<List<Playlist>>(), guardadas.map { pl ->
                        pl.xtreamPass?.let { pl.copy(xtreamPass = vault.protectOrPlain(it)) } ?: pl
                    })
                )
            }.onFailure { AppLog.e("Library", "no se pudieron cifrar las credenciales", it) }
        }

        // Lo guardado puede venir cifrado (lo normal) o en claro (de una
        // instalacion anterior). `revealOrPlain` acepta las dos cosas, asi
        // que la conversion no la nota nadie.
        guardadas.map { pl -> pl.xtreamPass?.let { pl.copy(xtreamPass = vault.revealOrPlain(it)) } ?: pl }
    }

    suspend fun savePlaylists(playlists: List<Playlist>) = withContext(Dispatchers.IO) {
        // La contraseña del panel no vuelve a disco en claro.
        val protegidas = playlists.map { pl ->
            pl.xtreamPass?.let { pl.copy(xtreamPass = vault.protectOrPlain(it)) } ?: pl
        }
        store.write(Keys.PLAYLISTS, json.encodeToString(serializer<List<Playlist>>(), protegidas))
    }

    suspend fun deletePlaylist(id: String): List<Playlist> = withContext(Dispatchers.IO) {
        val remaining = loadPlaylists().filterNot { it.id == id }
        savePlaylists(remaining)
        // Al borrar la lista se borra tambien su copia del catalogo: si no,
        // quedarian en disco miles de canales de una lista que el usuario cree
        // haber eliminado.
        runCatching { store.delete(claveCatalogo(id)) }
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
            is ChannelsResult.Ok -> {
                AppLog.d(
                    "Library",
                    "buildChannels: OK ${result.channels.size} canales, ${result.categories.size} categorías"
                )
                guardarCatalogo(playlist, result)
                result
            }
            is ChannelsResult.Error -> {
                AppLog.w("Library", "buildChannels: ERROR ${result.message}")
                // Sin red (o con el panel caido) se tira de la ultima copia: es
                // preferible una lista de ayer, avisando de que lo es, a una
                // pantalla vacia con un mensaje de error.
                val copia = recuperarCatalogo(playlist)
                if (copia == null) {
                    result
                } else {
                    AppLog.w(
                        "Library",
                        "buildChannels: se usa la copia guardada (${copia.channels.size} canales)",
                    )
                    copia
                }
            }
        }
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
        migrateIfNeeded()
        loadOrRecover<UserSettings>(Keys.SETTINGS) { UserSettings() }
    }

    suspend fun saveSettings(settings: UserSettings) = withContext(Dispatchers.IO) {
        store.write(Keys.SETTINGS, json.encodeToString(UserSettings.serializer(), settings))
    }

    // ------------------------------------------------------------------
    // Favoritos
    // ------------------------------------------------------------------

    suspend fun loadFavorites(): List<FavoriteChannel> = withContext(Dispatchers.IO) {
        migrateIfNeeded()
        loadOrRecover<List<FavoriteChannel>>(Keys.FAVORITES) { emptyList() }
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
        store.write(Keys.FAVORITES, json.encodeToString(serializer<List<FavoriteChannel>>(), favorites))
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
                    ?: error("Redirección ($code) sin destino")
                if (redirectsLeft <= 0) error("Demasiadas redirecciones")
                val next = if (location.startsWith("http")) location else URL(URL(url), location).toString()
                AppLog.d("Library", "fetch: redirige a ${AppLog.redactUrl(next)}")
                return fetch(next, redirectsLeft - 1)
            }
            if (code !in 200..299) {
                error("El servidor respondió $code")
            }
            return connection.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }

    sealed class ChannelsResult {
        data class Ok(
            val channels: List<Channel>,
            val categories: List<Category>,
            /**
             * Cuando el catalogo viene de la copia guardada en disco, el
             * instante en que se guardo. `null` = recien descargado.
             *
             * La UI lo necesita para avisar: enseñar una lista de hace una
             * semana como si fuera la de ahora es peor que no enseñar nada,
             * porque el usuario no entiende por que faltan canales.
             */
            val guardadoEnMs: Long? = null,
        ) : ChannelsResult()

        data class Error(val message: String) : ChannelsResult()
    }

    // ---------------------------------------------------------------------
    // Copia del catalogo en disco: sin ella, un corte de internet deja la
    // aplicacion vacia aunque ayer funcionara, y cada arranque vuelve a
    // descargar y reparsear decenas de miles de canales.
    // ---------------------------------------------------------------------

    @Serializable
    private data class CatalogoGuardado(
        val guardadoEnMs: Long,
        val channels: List<Channel>,
        val categories: List<Category>,
    )

    private fun claveCatalogo(playlistId: String) = "catalogo_$playlistId.json"

    private fun guardarCatalogo(playlist: Playlist, result: ChannelsResult.Ok) {
        runCatching {
            val secretos = secretosDe(playlist)
            val guardado = CatalogoGuardado(
                guardadoEnMs = System.currentTimeMillis(),
                channels = result.channels.map { it.copy(url = ocultarSecretos(it.url, secretos)) },
                categories = result.categories,
            )
            store.write(claveCatalogo(playlist.id), json.encodeToString(guardado))
        }.onFailure {
            // Que no se pueda guardar la copia no es motivo para estropear una
            // carga que ha ido bien: se pierde el modo sin conexion, nada mas.
            AppLog.w("Library", "No se pudo guardar la copia del catálogo: ${it.message}")
        }
    }

    private fun recuperarCatalogo(playlist: Playlist): ChannelsResult.Ok? {
        val crudo = runCatching { store.read(claveCatalogo(playlist.id)) }.getOrNull() ?: return null
        val guardado = runCatching { json.decodeFromString<CatalogoGuardado>(crudo) }.getOrElse {
            AppLog.w("Library", "La copia del catálogo está ilegible: ${it.message}")
            return null
        }
        val secretos = secretosDe(playlist)
        return ChannelsResult.Ok(
            channels = guardado.channels.map { it.copy(url = restaurarSecretos(it.url, secretos)) },
            categories = guardado.categories,
            guardadoEnMs = guardado.guardadoEnMs,
        )
    }

    /**
     * Credenciales que NO pueden acabar escritas en la copia del catalogo.
     *
     * En Xtream la direccion de cada canal lleva dentro el usuario y la
     * contraseña (`.../live/usuario/clave/123.ts`), y muchas listas M3U son un
     * `get.php?username=...&password=...`. Guardar decenas de miles de esas
     * direcciones en claro dejaria la contraseña del proveedor por todo el
     * disco y anularia el cifrado de credenciales.
     *
     * Se ignoran los valores muy cortos: sustituir una cadena de dos o tres
     * caracteres destrozaria direcciones donde aparece por casualidad.
     */
    private fun secretosDe(playlist: Playlist): List<String> = buildList {
        playlist.xtreamUser?.let { add(it) }
        playlist.xtreamPass?.let { add(it) }
        playlist.m3uUrl?.let { url ->
            for (parametro in listOf("username", "password")) {
                Regex("""[?&]$parametro=([^&#]+)""", RegexOption.IGNORE_CASE)
                    .find(url)?.groupValues?.get(1)?.let { add(it) }
            }
        }
    }.map { it.trim() }.filter { it.length >= 4 }.distinct()

    private fun ocultarSecretos(url: String, secretos: List<String>): String {
        var salida = url
        secretos.forEachIndexed { i, secreto -> salida = salida.replace(secreto, "$MARCA_SECRETO$i}}") }
        return salida
    }

    private fun restaurarSecretos(url: String, secretos: List<String>): String {
        var salida = url
        secretos.forEachIndexed { i, secreto -> salida = salida.replace("$MARCA_SECRETO$i}}", secreto) }
        return salida
    }

}
