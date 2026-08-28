package com.iptv.family.desktop.remote

import com.iptv.family.desktop.player.VlcController
import com.iptv.family.desktop.state.AppState
import com.iptv.family.shared.log.AppLog
import com.iptv.family.shared.model.Channel
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
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

    private fun adminToken(): String? = appState.settings.webServerToken
    private fun viewerToken(): String? = appState.settings.webViewerToken

    fun start(port: Int) {
        if (engine != null) return
        AppLog.d("RemoteServer", "start: puerto=$port")
        val bus = RemoteEventBus(appState, controller, scope)
        eventBus = bus

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

                post("/login") {
                    // receiveJson (receiveText + Json): responde siempre
                    // (200 con cookie o 401 JSON), nunca deja la conexion a medias.
                    val reqToken = call.receiveJson<LoginRequest>()?.token?.trim()
                    val role = when {
                        reqToken.isNullOrBlank() -> null
                        reqToken == adminToken() -> RemoteRole.ADMIN
                        reqToken == viewerToken() -> RemoteRole.VIEWER
                        else -> null
                    }
                    if (role != null) {
                        call.response.cookies.append(
                            Cookie(
                                name = RemoteAuth.SESSION_COOKIE,
                                value = reqToken!!,
                                path = "/",
                                maxAge = 60 * 60 * 24 * 30,
                                httpOnly = true,
                            )
                        )
                        call.respond(mapOf("ok" to true, "role" to role.name.lowercase()))
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid_token"))
                    }
                }

                get("/api/state") {
                    val role = roleOf(call)
                    if (role == null) {
                        call.response.header(HttpHeaders.WWWAuthenticate, "Bearer")
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized"))
                        return@get
                    }
                    call.respond(buildStateDto(bus, role))
                }

                post("/api/channel/{id}") {
                    if (!requireAdmin()) return@post
                    val id = call.parameters["id"]
                    val channel = id?.let { cid -> appState.channels.find { it.id == cid } }
                    if (channel == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@post
                    }
                    AppLog.d("RemoteServer", "petición remota: reproducir '${channel.name}'")
                    onRemotePlayRequest(channel)
                    call.respond(bus.currentNowPlaying())
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
                    if (!requireAuth()) return@get
                    val url = controller?.currentUrl
                    if (url == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    if (StreamProxy.looksLikeManifest(url)) streamProxy.proxyManifest(call, url)
                    else streamProxy.proxySegment(call, url)
                }

                get("/stream/segment") {
                    if (!requireAuth()) return@get
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
        engine?.stop(1000, 2000)
        engine = null
        AppLog.d("RemoteServer", "stop")
    }

    private fun roleOf(call: ApplicationCall): RemoteRole? =
        RemoteAuth.roleFor(call, adminToken(), viewerToken())

    /** Cualquier rol valido (administrador o invitado). */
    private suspend fun PipelineContext<Unit, ApplicationCall>.requireAuth(): Boolean {
        if (roleOf(call) != null) return true
        call.response.header(HttpHeaders.WWWAuthenticate, "Bearer")
        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized"))
        return false
    }

    /**
     * Solo administrador. Todo lo que cambia algo (canal, favoritos, volumen,
     * pausa) pasa por aqui: un invitado ve, pero no toca.
     */
    private suspend fun PipelineContext<Unit, ApplicationCall>.requireAdmin(): Boolean {
        val role = roleOf(call)
        if (role == null) {
            call.response.header(HttpHeaders.WWWAuthenticate, "Bearer")
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized"))
            return false
        }
        if (!role.isAdmin) {
            AppLog.w("RemoteServer", "invitado intento una accion de administrador: ${call.request.local.uri}")
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "viewer_read_only"))
            return false
        }
        return true
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

    private fun buildStateDto(bus: RemoteEventBus, role: RemoteRole): StateDto {
        val now = bus.currentNowPlaying()
        // Un invitado solo ve el canal que ha puesto el administrador: no se le
        // manda la lista ni los grupos (ademas de no poder cambiar nada, no tiene
        // por que conocer el resto del catalogo).
        if (!role.isAdmin) {
            return StateDto(
                playlistName = appState.selectedPlaylist?.name,
                role = "viewer",
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
        val counts = HashMap<String, Int>()
        for (ch in appState.channels) {
            val gid = ch.group ?: continue
            counts[gid] = (counts[gid] ?: 0) + 1
        }
        val groups = counts.entries
            .map { (id, count) -> GroupDto(id = id, name = nameById[id] ?: id, count = count) }
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
                )
            }

        return StateDto(
            playlistName = appState.selectedPlaylist?.name,
            role = "admin",
            groups = groups,
            channels = channels,
            favoriteChannelIds = favIds,
            nowPlaying = now,
        )
    }

    private companion object {
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
