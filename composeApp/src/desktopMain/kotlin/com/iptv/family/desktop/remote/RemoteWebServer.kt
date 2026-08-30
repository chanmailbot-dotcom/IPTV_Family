package com.iptv.family.desktop.remote

import com.iptv.family.desktop.player.VlcController
import com.iptv.family.desktop.state.AppState
import com.iptv.family.shared.data.auth.PasswordHasher
import com.iptv.family.shared.log.AppLog
import com.iptv.family.shared.model.Channel
import com.iptv.family.shared.model.WebRole
import com.iptv.family.shared.model.WebUser
import java.io.File
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.Cookie
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.resources
import io.ktor.server.http.content.static
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.excludeContentType
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.queryString
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.iptv.family.shared.model.CategoryType

/**
 * Parseo manual del body JSON: `receive<T>()` depende del plugin
 * ContentNegotiation y ante un body no-JSON deja la conexion a medias.
 * receiveText + Json responde siempre y devuelve null ante cualquier
 * cuerpo invalido, vacio o mal formado.
 */
private suspend inline fun <reified T : Any> ApplicationCall.receiveJson(): T? {
    val text = runCatching { receiveText() }.getOrDefault("")
    if (text.isBlank()) return null
    return runCatching { Json.decodeFromString<T>(text) }.getOrNull()
}

/**
 * Servidor de control remoto + visionado embebido en la app de escritorio.
 * Se activa/desactiva desde el interruptor "Servidor web" en Ajustes; corre
 * solo mientras la ventana de la app esta abierta.
 *
 * No tiene estado propio: lee directamente [appState]/[controller] (los mismos
 * que usa la UI de escritorio) y delega el cambio de canal en
 * [onRemotePlayRequest], que Main.kt implementa reusando la misma funcion que
 * ya usa la UI al pulsar un canal.
 */
class RemoteWebServer(
    private val appState: AppState,
    private val controller: VlcController?,
    private val scope: CoroutineScope,
) {
    /** Main.kt la actualiza en cada recomposicion para apuntar siempre a la funcion `play` vigente. */
    var onRemotePlayRequest: (Channel) -> Unit = {}

    private var engine: ApplicationEngine? = null
    private var eventBus: RemoteEventBus? = null
    private val httpClient = HttpClient(ClientCIO) {
        // Sin esto CIO aplica su requestTimeout por defecto (~15 s) y corta a mitad
        // la descarga de segmentos grandes: era el HttpRequestTimeoutException del log.
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
    }
    private val streamProxy = StreamProxy(httpClient)

    /** Puerto en el que escucha, para poder apuntar ffmpeg al mux local. */
    private var listeningPort: Int = 0

    /**
     * Convierte a AAC el audio que el navegador no puede reproducir. Se crea solo
     * si hay ffmpeg disponible; sin el, la web sigue funcionando (sin sonido en
     * los canales con AC-3/MP2, avisando de por que).
     */
    private var transcoder: AudioTranscoder? = null
    private var ffmpeg: String? = null

    /** Corrutina que mata ffmpeg cuando nadie lo esta usando. */
    private var transcoderJanitor: kotlinx.coroutines.Job? = null

    /**
     * Episodios de serie ya desplegados, por id.
     *
     * No estan en `appState.channels` -- se piden al panel bajo demanda -- asi
     * que sin esto `/api/channel/{id}` respondia 404 al intentar reproducir uno.
     * Se limita el tamaño para que navegar por muchas series no acumule memoria
     * sin freno.
     */
    private val episodesSeen = object : LinkedHashMap<String, Channel>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Channel>?) = size > 500
    }

    /** Cache del sondeo de audio por canal, para no lanzar un ffprobe por peticion. */
    private val audioInfoByChannel =
        java.util.concurrent.ConcurrentHashMap<String, AudioTranscoder.Companion.AudioInfo>()

    private fun users(): List<WebUser> = appState.settings.webUsers

    fun start(port: Int) {
        if (engine != null) return
        AppLog.d("RemoteServer", "start: puerto=$port")
        listeningPort = port
        val bus = RemoteEventBus(appState, controller, scope)
        eventBus = bus

        if (appState.settings.transcodeAudioForWeb) {
            ffmpeg = AudioTranscoder.resolveFfmpeg(appState.settings.ffmpegPath)
            if (ffmpeg == null) {
                AppLog.w(
                    "RemoteServer",
                    "conversion de audio activada pero no se encontro ffmpeg: los canales con AC-3/MP2 seguiran sin sonido en la web"
                )
            } else {
                val dir = File(System.getProperty("java.io.tmpdir"), "iptv-family-transcode")
                val tc = AudioTranscoder(ffmpeg!!, dir)
                transcoder = tc
                AppLog.d("RemoteServer", "conversion de audio lista (ffmpeg: $ffmpeg)")
                // Vigilante de inactividad: sin esto, ffmpeg seguiria vivo para
                // siempre despues de que el navegador se fuera, tirando del stream
                // del proveedor sin que nadie lo mire (gasto de CPU, de datos y una
                // conexion abierta contra el panel a cuenta de nada).
                transcoderJanitor = scope.launch {
                    while (true) {
                        delay(15_000)
                        tc.stopIfIdle()
                    }
                }
            }
        }

        engine = embeddedServer(ServerCIO, port = port) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }

            // El estado con una lista de 40.000 canales son ~7 MB de JSON: por
            // ngrok o por 4G eso es inaceptable, y comprimido baja a una fraccion.
            // Se excluyen a proposito el video (ya viene comprimido; recomprimir
            // solo gasta CPU) y los eventos SSE, que con compresion se quedarian
            // en el buffer del compresor y dejarian de llegar en tiempo real.
            install(Compression) {
                gzip { priority = 1.0 }
                deflate { priority = 0.9 }
                excludeContentType(
                    ContentType.Text.EventStream,
                    ContentType.Video.Any,
                    ContentType.Application.OctetStream,
                    ContentType.parse("application/vnd.apple.mpegurl"),
                )
            }

            // El navegador no debe guardar la SPA en cache: cada vez que se despliega una
            // version nueva del jar, index.html/app.js deben servirse frescos, o el
            // navegador se queda pegado al login/JS viejo sin avisar de nada.
            intercept(ApplicationCallPipeline.Plugins) {
                call.response.header(HttpHeaders.CacheControl, "no-cache, no-store, must-revalidate")
            }

            routing {
                get("/") {
                    // El enlace copiado en Ajustes lleva ?token=... en la raiz: hay que
                    // conservarlo al redirigir o el auto-login de app.js nunca lo ve.
                    val query = call.request.queryString()
                    val target = if (query.isNotBlank()) "/index.html?$query" else "/index.html"
                    call.respondRedirect(target)
                }

                static("/") {
                    resources("webui")
                }

                // El manifest se sirve con su Content-Type correcto para que los
                // navegadores lo reconozcan como PWA (theme-color, add-to-home-screen).
                get("/manifest.webmanifest") {
                    call.respondText(
                        WEB_MANIFEST,
                        ContentType.parse("application/manifest+json"),
                    )
                }

                // Icono de marca (SVG con el degradado indigo->teal del resto de la app),
                // servido con el tipo correcto para el <link rel="icon"> y las mascotas.
                get("/favicon.svg") {
                    call.respondText(
                        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 64 64\">" +
                            "<defs><linearGradient id=\"g\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">" +
                            "<stop offset=\"0\" stop-color=\"#6C8CFF\"/><stop offset=\"1\" stop-color=\"#00D0B0\"/>" +
                            "</linearGradient></defs>" +
                            "<rect width=\"64\" height=\"64\" rx=\"14\" fill=\"url(#g)\"/>" +
                            "<path d=\"M26 20L46 32L26 44z\" fill=\"#0B0E14\"/>" +
                            "</svg>",
                        ContentType.parse("image/svg+xml"),
                    )
                }

                /**
                 * Estado del login ANTES de identificarse: le dice a la web si hay
                 * que crear la primera cuenta de administrador o si ya se puede
                 * iniciar sesion. Es el unico endpoint publico a proposito.
                 */
                get("/api/auth") {
                    call.respond(
                        AuthInfoDto(
                            needsSetup = users().isEmpty(),
                            session = RemoteAuth.sessionFor(call, users())?.let {
                                SessionDto(it.username, it.role.name.lowercase())
                            },
                        )
                    )
                }

                /**
                 * Crea la PRIMERA cuenta de administrador. Solo funciona mientras no
                 * haya ningun usuario: en cuanto existe uno, las cuentas nuevas las
                 * crea el administrador ya identificado (ver /api/users).
                 */
                post("/api/setup") {
                    if (users().isNotEmpty()) {
                        call.respond(HttpStatusCode.Conflict, mapOf("error" to "already_configured"))
                        return@post
                    }
                    val req = call.receiveJson<LoginRequest>()
                    val error = validateCredentials(req?.username, req?.password)
                    if (error != null) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to error))
                        return@post
                    }
                    val user = PasswordHasher.createUser(req!!.username, req.password, WebRole.ADMIN)
                    // .join(): hay que ESPERAR a que la cuenta este guardada antes de
                    // contestar. Sin esto la web hacia login inmediatamente despues y
                    // se encontraba con que todavia no habia usuarios.
                    scope.launch { appState.mutateSettings { copy(webUsers = listOf(user)) } }.join()
                    AppLog.d("RemoteServer", "creada la cuenta de administrador '${user.username}'")
                    call.respond(mapOf("ok" to true))
                }

                post("/login") {
                    // receiveJson (receiveText + Json): responde siempre, nunca deja
                    // la conexion a medias ante un cuerpo invalido.
                    val req = call.receiveJson<LoginRequest>()
                    val session = req?.let { RemoteAuth.login(users(), it.username, it.password) }
                    if (session == null) {
                        AppLog.w("RemoteServer", "login fallido para '${req?.username}'")
                        // Mismo mensaje si el usuario no existe o la contraseña falla:
                        // no se confirma que cuentas existen.
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid_credentials"))
                        return@post
                    }
                    call.response.cookies.append(
                        Cookie(
                            name = RemoteAuth.SESSION_COOKIE,
                            value = session.token,
                            path = "/",
                            maxAge = 60 * 60 * 24 * 30,
                            httpOnly = true,
                        )
                    )
                    AppLog.d("RemoteServer", "login de '${session.username}' (${session.role})")
                    call.respond(
                        LoginResponseDto(
                            username = session.username,
                            role = session.role.name.lowercase(),
                            streamKey = session.token,
                        )
                    )
                }

                post("/logout") {
                    RemoteAuth.logout(call)
                    call.response.cookies.append(
                        Cookie(name = RemoteAuth.SESSION_COOKIE, value = "", path = "/", maxAge = 0)
                    )
                    call.respond(mapOf("ok" to true))
                }

                get("/api/state") {
                    val session = sessionOf(call)
                    if (session == null) {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized"))
                        return@get
                    }
                    call.respond(buildStateDto(bus, session))
                }

                // ---- Gestion de usuarios (solo administrador) ----

                get("/api/users") {
                    if (!requireAdmin()) return@get
                    call.respond(users().map { UserDto(it.username, it.role.name.lowercase(), it.createdAt) })
                }

                post("/api/users") {
                    if (!requireAdmin()) return@post
                    val req = call.receiveJson<CreateUserRequest>()
                    val error = validateCredentials(req?.username, req?.password)
                    if (error != null) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to error))
                        return@post
                    }
                    if (users().any { it.username.equals(req!!.username.trim(), ignoreCase = true) }) {
                        call.respond(HttpStatusCode.Conflict, mapOf("error" to "username_taken"))
                        return@post
                    }
                    val role = if (req!!.role.equals("admin", ignoreCase = true)) WebRole.ADMIN else WebRole.VIEWER
                    val user = PasswordHasher.createUser(req.username, req.password, role)
                    scope.launch { appState.mutateSettings { copy(webUsers = webUsers + user) } }.join()
                    AppLog.d("RemoteServer", "creado el usuario '${user.username}' ($role)")
                    call.respond(mapOf("ok" to true))
                }

                post("/api/users/{username}/password") {
                    if (!requireAdmin()) return@post
                    val username = call.parameters["username"].orEmpty()
                    val password = call.receiveJson<PasswordRequest>()?.password
                    if (password == null || password.length < MIN_PASSWORD_LENGTH) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "weak_password"))
                        return@post
                    }
                    val existing = users().firstOrNull { it.username.equals(username, ignoreCase = true) }
                    if (existing == null) {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "no_such_user"))
                        return@post
                    }
                    val updated = PasswordHasher.withNewPassword(existing, password)
                    scope.launch {
                        appState.mutateSettings {
                            copy(webUsers = webUsers.map { if (it.username == existing.username) updated else it })
                        }
                    }.join()
                    // Cambiar la contraseña echa a quien estuviera usando la antigua.
                    RemoteAuth.revokeSessionsOf(existing.username)
                    call.respond(mapOf("ok" to true))
                }

                post("/api/users/{username}/delete") {
                    if (!requireAdmin()) return@post
                    val username = call.parameters["username"].orEmpty()
                    val existing = users().firstOrNull { it.username.equals(username, ignoreCase = true) }
                    if (existing == null) {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "no_such_user"))
                        return@post
                    }
                    // No dejar el sistema sin ningun administrador: nadie podria volver
                    // a gestionar usuarios sin editar settings.json a mano.
                    val admins = users().count { it.role == WebRole.ADMIN }
                    if (existing.role == WebRole.ADMIN && admins <= 1) {
                        call.respond(HttpStatusCode.Conflict, mapOf("error" to "last_admin"))
                        return@post
                    }
                    scope.launch {
                        appState.mutateSettings { copy(webUsers = webUsers.filterNot { it.username == existing.username }) }
                    }.join()
                    RemoteAuth.revokeSessionsOf(existing.username)
                    AppLog.d("RemoteServer", "borrado el usuario '${existing.username}'")
                    call.respond(mapOf("ok" to true))
                }

                post("/api/channel/{id}") {
                    if (!requireAdmin()) return@post
                    val id = call.parameters["id"]
                    // Los episodios no estan en la lista principal (se piden al panel
                    // bajo demanda), asi que tambien se busca entre los ya desplegados.
                    val channel = id?.let { cid ->
                        appState.channels.find { it.id == cid } ?: episodesSeen[cid]
                    }
                    if (channel == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@post
                    }
                    // Una serie no es reproducible: `get_series` devuelve el
                    // contenedor, no un flujo. Antes se intentaba abrir igualmente
                    // y fallaba siempre. Se rechaza aqui para que ningun cliente
                    // pueda colarlo.
                    if (channel.categoryType == CategoryType.SERIES) {
                        AppLog.d("RemoteServer", "petición remota rechazada: '${channel.name}' es una serie")
                        call.respond(HttpStatusCode.Conflict, ErrorDto("es_una_serie"))
                        return@post
                    }
                    AppLog.d("RemoteServer", "petición remota: reproducir '${channel.name}'")
                    onRemotePlayRequest(channel)
                    call.respond(bus.currentNowPlaying())
                }

                /** Episodios de una serie, para desplegarlos en la web. */
                get("/api/series/{id}/episodes") {
                    if (!requireAdmin()) return@get
                    val id = call.parameters["id"].orEmpty()
                    val series = appState.channels.find { it.id == id }
                    if (series == null || series.categoryType != CategoryType.SERIES) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    val episodes = runCatching { appState.loadSeriesEpisodes(id) }
                        .onFailure { AppLog.e("RemoteServer", "episodios de '${series.name}'", it) }
                        .getOrDefault(emptyList())
                    episodes.forEach { episodesSeen[it.id] = it }
                    AppLog.d("RemoteServer", "episodios de '${series.name}': ${episodes.size}")
                    call.respond(
                        EpisodesDto(
                            seriesName = series.name,
                            episodes = episodes.map {
                                ChannelDto(
                                    id = it.id,
                                    name = it.name,
                                    number = it.number,
                                    logoUrl = it.logoUrl,
                                    group = it.group,
                                    kind = kindOf(it.categoryType),
                                )
                            },
                        )
                    )
                }

                // ---- Control remoto del reproductor (volumen, silencio, pausa, zapeo) ----

                get("/api/player") {
                    if (!requireAuth()) return@get
                    call.respond(bus.currentNowPlaying())
                }

                post("/api/player/volume") {
                    if (!requireAdmin()) return@post
                    val req = call.receiveJson<VolumeRequest>()
                    if (req == null || controller == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }
                    controller?.changeVolume(req.volume)
                    call.respond(bus.currentNowPlaying())
                }

                post("/api/player/mute") {
                    if (!requireAdmin()) return@post
                    val req = call.receiveJson<MuteRequest>()
                    if (req == null || controller == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }
                    controller?.changeMuted(req.muted)
                    call.respond(bus.currentNowPlaying())
                }

                post("/api/player/playpause") {
                    if (!requireAdmin()) return@post
                    if (controller == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }
                    controller?.togglePlayPause()
                    call.respond(bus.currentNowPlaying())
                }

                post("/api/player/stop") {
                    if (!requireAdmin()) return@post
                    controller?.stop()
                    call.respond(bus.currentNowPlaying())
                }

                post("/api/player/next") {
                    if (!requireAdmin()) return@post
                    val next = zap(1)
                    if (next == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@post
                    }
                    AppLog.d("RemoteServer", "petición remota: canal siguiente '${next.name}'")
                    onRemotePlayRequest(next)
                    call.respond(bus.currentNowPlaying())
                }

                post("/api/player/prev") {
                    if (!requireAdmin()) return@post
                    val prev = zap(-1)
                    if (prev == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@post
                    }
                    AppLog.d("RemoteServer", "petición remota: canal anterior '${prev.name}'")
                    onRemotePlayRequest(prev)
                    call.respond(bus.currentNowPlaying())
                }

                post("/api/favorite/{id}") {
                    if (!requireAdmin()) return@post
                    val id = call.parameters["id"]
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }
                    val desired = call.receiveJson<FavoriteRequest>()?.favorite ?: true
                    if (appState.isFavorite(id) != desired) {
                        scope.launch { appState.toggleFavorite(id) }
                    }
                    call.respond(HttpStatusCode.OK)
                }

                get("/api/events") {
                    if (!requireAuth()) return@get
                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                        write("retry: 2000\n\n")
                        flush()
                        // Un latido cada 15s si no hay eventos reales: sin esto, una conexion
                        // cuyo cliente ya desaparecio (pestaña cerrada, movil bloqueado) se queda
                        // colgada esperando el siguiente evento para siempre -- el intento de
                        // escritura periodico es lo que detecta el socket muerto y libera el hilo.
                        coroutineScope {
                            val channel = bus.events.produceIn(this)
                            try {
                                while (true) {
                                    val event = withTimeoutOrNull(15_000) { channel.receive() }
                                    if (event == null) {
                                        write(": ping\n\n")
                                    } else {
                                        val (type, payload) = when (event) {
                                            is RemoteEvent.NowPlaying ->
                                                "now-playing" to Json.encodeToString(NowPlayingDto.serializer(), event.dto)
                                            RemoteEvent.ChannelsChanged -> "channels" to "{}"
                                        }
                                        write("event: $type\ndata: $payload\n\n")
                                    }
                                    flush()
                                }
                            } finally {
                                channel.cancel()
                            }
                        }
                    }
                }

                // El parametro `ch` NO se usa para resolver el stream (siempre se
                // sirve el canal que suena ahora en el escritorio): esta para que la
                // URL sea distinta en cada canal. Con una URL fija, hls.js veia el
                // mismo recurso servir de golpe una playlist completamente distinta
                // y lo trataba como un salto del directo: tardaba una eternidad en
                // resincronizar. Con `ch` en la query, cada cambio de canal es un
                // recurso nuevo y la recarga es limpia e inmediata.
                get("/stream/current.m3u8") {
                    if (!requireStreamAccess()) return@get
                    val url = controller?.currentUrl
                    if (url == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }

                    // Si el canal trae audio que el navegador no sabe decodificar
                    // (AC-3, MP2...), se sirve la version con el audio convertido a
                    // AAC por ffmpeg en vez del stream original.
                    //
                    // `nt=1` lo pone ffmpeg/ffprobe al leer de aqui: para ellos hay
                    // que servir SIEMPRE el stream original, o se les devolveria su
                    // propia salida y se quedarian dando vueltas sobre si mismos.
                    val noTranscode = call.request.queryParameters["nt"] == "1"
                    if (!noTranscode) {
                        val transcoded = transcodedPlaylistOrNull()
                        if (transcoded != null) {
                            respondTranscodedPlaylist(call, transcoded)
                            return@get
                        }
                    }

                    if (StreamProxy.looksLikeManifest(url)) streamProxy.proxyManifest(call, url)
                    else streamProxy.proxySegment(call, url)
                }

                /** Segmentos que produce ffmpeg (audio ya convertido a AAC). */
                get("/stream/aac/{name}") {
                    if (!requireStreamAccess()) return@get
                    val name = call.parameters["name"].orEmpty()
                    val file = transcoder?.segmentFile(name)
                    if (file == null || !file.isFile) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                    call.respondBytes(file.readBytes(), ContentType.parse("video/mp2t"))
                }

                get("/stream/segment") {
                    if (!requireStreamAccess()) return@get
                    val src = call.request.queryParameters["src"]
                    if (src == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@get
                    }
                    val url = StreamProxy.decodeSrc(src)
                    if (StreamProxy.looksLikeManifest(url)) streamProxy.proxyManifest(call, url)
                    else streamProxy.proxySegment(call, url)
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        eventBus?.stop()
        eventBus = null
        // Antes que el engine: hay que matar ffmpeg o quedaria un proceso huerfano
        // consumiendo CPU y red despues de apagar el servidor.
        transcoderJanitor?.cancel()
        transcoderJanitor = null
        transcoder?.stop()
        transcoder = null
        audioInfoByChannel.clear()
        engine?.stop(1000, 2000)
        engine = null
        AppLog.d("RemoteServer", "stop")
    }

    private fun sessionOf(call: ApplicationCall): RemoteSession? =
        RemoteAuth.sessionFor(call, users())

    /** Cualquier sesion valida (administrador o invitado). */
    private suspend fun PipelineContext<Unit, ApplicationCall>.requireAuth(): Boolean {
        if (sessionOf(call) != null) return true
        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized"))
        return false
    }

    /**
     * Como [requireAuth], pero acepta tambien la clave interna del mux: es la que
     * usan el VLC del escritorio y ffmpeg, que no tienen cuenta de usuario (ver
     * [LocalMuxKey]).
     */
    private suspend fun PipelineContext<Unit, ApplicationCall>.requireStreamAccess(): Boolean {
        if (call.request.queryParameters[LocalMuxKey.PARAM] == LocalMuxKey.value) return true
        return requireAuth()
    }

    /**
     * Solo administrador. Todo lo que cambia algo (canal, favoritos, volumen,
     * pausa, usuarios) pasa por aqui: un invitado ve, pero no toca.
     */
    private suspend fun PipelineContext<Unit, ApplicationCall>.requireAdmin(): Boolean {
        val session = sessionOf(call)
        if (session == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized"))
            return false
        }
        if (!session.isAdmin) {
            AppLog.w(
                "RemoteServer",
                "'${session.username}' (invitado) intento una accion de administrador: ${call.request.local.uri}"
            )
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "viewer_read_only"))
            return false
        }
        return true
    }

    /**
     * Lista HLS con el audio ya convertido a AAC para el canal en curso, o null
     * si no hace falta (el canal ya trae audio que el navegador entiende, o no
     * hay ffmpeg, o la conversion esta desactivada).
     *
     * El codec se averigua una sola vez por canal con ffprobe y se recuerda: es
     * una operacion de varios segundos y no puede hacerse en cada peticion de
     * manifest (que llegan cada pocos segundos).
     */
    private fun transcodedPlaylistOrNull(): File? {
        val tc = transcoder ?: return null
        val ff = ffmpeg ?: return null
        val channelId = currentChannelId() ?: return null

        val info = audioInfoByChannel.getOrPut(channelId) {
            // ffprobe apunta al mux local, no al panel: asi no abre una conexion
            // propia contra el proveedor (ver comentario en AudioTranscoder).
            val probeUrl = localMuxUrl() ?: return null
            val detected = AudioTranscoder.probeAudio(ff, probeUrl)
            AppLog.d(
                "RemoteServer",
                "canal $channelId: audio detectado = ${detected?.codec ?: "desconocido"}" +
                    (detected?.let { " (pista ${it.trackIndex})" } ?: "")
            )
            detected ?: UNKNOWN_AUDIO
        }
        if (info.codec == null) return null

        // Dos motivos distintos para pasar por ffmpeg:
        val recode = AudioTranscoder.needsTranscode(info.codec)  // el navegador no sabe el codec
        val wrongTrack = info.trackIndex > 0                     // la pista buena no es la primera
        if (!recode && !wrongTrack) return null

        // Lo segundo hacia falta y no estaba: en estos canales el audio va MUXEADO
        // dentro de los segmentos TS, sin renditions `#EXT-X-MEDIA` separadas. Al
        // demultiplexar, el navegador se queda con la primera pista de audio y no
        // hay forma de pedirle otra desde JavaScript. Y la primera suele ser la
        // audiodescripcion (o un idioma que no es el nuestro), asi que la web
        // sonaba distinto de la app de escritorio, donde VLC si puede elegir.
        // Cuando solo se trata de eso, ffmpeg copia el audio sin recodificar.
        if (!recode) {
            AppLog.d(
                "RemoteServer",
                "canal $channelId: audio ${info.codec} vale para el navegador, pero la pista " +
                    "preferida es la ${info.trackIndex}; se remuxea con ffmpeg (sin recodificar)"
            )
        }

        val source = localMuxUrl() ?: return null
        return tc.playlistFor(channelId, source, info.trackIndex, recodeAudio = recode)
    }

    /**
     * Sirve la lista que genera ffmpeg, reescribiendo los nombres de segmento a
     * `/stream/aac/...` para que el navegador los pida por HTTP (en el fichero
     * son rutas de disco locales).
     */
    private suspend fun respondTranscodedPlaylist(call: ApplicationCall, playlist: File) {
        val session = RemoteAuth.sessionToken(call)
        val text = runCatching { playlist.readText() }.getOrNull()
        if (text.isNullOrBlank()) {
            call.respond(HttpStatusCode.ServiceUnavailable)
            return
        }
        val rewritten = text.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) line
            else {
                val name = File(trimmed).name
                val suffix = if (session.isNullOrBlank()) "" else "?s=" + java.net.URLEncoder.encode(session, "UTF-8")
                "/stream/aac/$name$suffix"
            }
        }
        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        call.respondText(rewritten, ContentType.parse("application/vnd.apple.mpegurl"))
    }

    /** ID del canal que suena ahora en el escritorio. */
    private fun currentChannelId(): String? {
        val url = controller?.currentUrl ?: return null
        return appState.channels.firstOrNull { it.url == url }?.id
    }

    /**
     * URL del mux local para que ffmpeg/ffprobe lean de aqui y no del panel.
     * Lleva `nt=1` ("no transcode") para que esa peticion sirva el stream
     * original: sin esa marca, el mux le devolveria a ffmpeg su propia salida y
     * se quedaria dando vueltas sobre si mismo.
     */
    private fun localMuxUrl(): String? {
        val ch = currentChannelId() ?: return null
        return "http://127.0.0.1:$listeningPort/stream/current.m3u8" +
            "?nt=1&ch=" + java.net.URLEncoder.encode(ch, "UTF-8") +
            "&${LocalMuxKey.PARAM}=" + java.net.URLEncoder.encode(LocalMuxKey.value, "UTF-8")
    }

    /** Reglas minimas de usuario y contraseña; devuelve el codigo de error o null. */
    private fun validateCredentials(username: String?, password: String?): String? = when {
        username.isNullOrBlank() -> "username_required"
        username.trim().length < 3 -> "username_too_short"
        password.isNullOrEmpty() -> "password_required"
        password.length < MIN_PASSWORD_LENGTH -> "weak_password"
        else -> null
    }

    /** Canal vecino en el orden de la lista activa, con salto circular (±1). */
    private fun zap(delta: Int): Channel? {
        val channels = appState.channels
        if (channels.isEmpty()) return null
        val currentUrl = controller?.currentUrl
        val idx = currentUrl?.let { u -> channels.indexOfFirst { it.url == u } } ?: -1
        return when {
            idx < 0 -> if (delta >= 0) channels.first() else channels.last()
            else -> {
                val next = (idx + delta).mod(channels.size)
                channels[next]
            }
        }
    }

    private fun buildStateDto(bus: RemoteEventBus, session: RemoteSession): StateDto {
        val now = bus.currentNowPlaying()
        // Un invitado solo ve el canal que ha puesto el administrador: no se le
        // manda la lista ni los grupos (ademas de no poder cambiar nada, no tiene
        // por que conocer el resto del catalogo).
        if (!session.isAdmin) {
            return StateDto(
                playlistName = appState.selectedPlaylist?.name,
                role = "viewer",
                username = session.username,
                nowPlaying = now,
            )
        }

        val favIds = appState.favorites
            .filter { it.playlistId == appState.selectedPlaylistId }
            .map { it.channelId }

        // Nombre legible por id de categoria: en Xtream `Channel.group` es un
        // numero, y sin esta traduccion la web mostraba "142" como si fuera el
        // nombre del grupo.
        val nameById = appState.categories.associate { it.id to it.name }
        val kindById = appState.categories.associate { it.id to kindOf(it.type) }
        val counts = HashMap<String, Int>()
        for (ch in appState.channels) {
            val gid = ch.group ?: continue
            counts[gid] = (counts[gid] ?: 0) + 1
        }
        val groups = counts.entries
            .map { (id, count) ->
                GroupDto(
                    id = id,
                    name = nameById[id] ?: id,
                    count = count,
                    kind = kindById[id] ?: KIND_LIVE,
                )
            }
            .sortedBy { it.name.lowercase() }

        // Orden que el usuario espera: por numero de dial cuando el proveedor lo
        // publica, y los que no lo traen al final por nombre (en vez del orden
        // crudo en que vino el JSON del panel).
        // Ya vienen ordenados del repositorio (tipo → numero de dial → nombre),
        // asi que aqui solo se convierten al DTO.
        val channels = appState.channels
            .map { ch ->
                ChannelDto(
                    id = ch.id,
                    name = ch.name,
                    number = ch.number,
                    logoUrl = ch.logoUrl,
                    group = ch.group,
                    kind = kindOf(ch.categoryType),
                )
            }

        return StateDto(
            playlistName = appState.selectedPlaylist?.name,
            role = "admin",
            username = session.username,
            groups = groups,
            channels = channels,
            favoriteChannelIds = favIds,
            nowPlaying = now,
        )
    }

    private companion object {
        /** Minimo de la contraseña: corto pero no ridiculo, es una red domestica. */
        const val MIN_PASSWORD_LENGTH = 6

        /** Marca en la cache para "ffprobe no supo decirlo": no transcodificar. */
        val UNKNOWN_AUDIO = AudioTranscoder.Companion.AudioInfo(codec = null, trackIndex = 0)

        /** Manifest PWA-lite: permite "añadir a pantalla de inicio" con el icono y color de marca. */
        const val WEB_MANIFEST = """{
            "name": "IPTV Family",
            "short_name": "IPTV Family",
            "description": "Control remoto y visionado de IPTV Family",
            "start_url": "/",
            "display": "standalone",
            "background_color": "#101318",
            "theme_color": "#101318",
            "icons": [
                { "src": "/favicon.svg", "sizes": "any", "type": "image/svg+xml", "purpose": "any" }
            ]
        }"""
    }
}
