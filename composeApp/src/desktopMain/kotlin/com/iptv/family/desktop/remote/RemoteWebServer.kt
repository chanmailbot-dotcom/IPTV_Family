package com.iptv.family.desktop.remote

import com.iptv.family.desktop.player.VlcController
import com.iptv.family.desktop.state.AppState
import com.iptv.family.shared.log.AppLog
import com.iptv.family.shared.model.Channel
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
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
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.queryString
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
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
import kotlinx.serialization.json.Json

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
    private val httpClient = HttpClient(ClientCIO)
    private val streamProxy = StreamProxy(httpClient)

    private fun currentToken(): String? = appState.settings.webServerToken

    fun start(port: Int) {
        if (engine != null) return
        AppLog.d("RemoteServer", "start: puerto=$port")
        val bus = RemoteEventBus(appState, controller, scope)
        eventBus = bus

        engine = embeddedServer(ServerCIO, port = port) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }

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

                post("/login") {
                    val req = runCatching { call.receive<LoginRequest>() }.getOrNull()
                    val token = currentToken()
                    if (req != null && token != null && req.token == token) {
                        call.response.cookies.append(
                            Cookie(
                                name = RemoteAuth.SESSION_COOKIE,
                                value = token,
                                path = "/",
                                maxAge = 60 * 60 * 24 * 30,
                                httpOnly = true,
                            )
                        )
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.Unauthorized)
                    }
                }

                get("/api/state") {
                    if (!requireAuth()) return@get
                    call.respond(buildStateDto(bus))
                }

                post("/api/channel/{id}") {
                    if (!requireAuth()) return@post
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

                post("/api/favorite/{id}") {
                    if (!requireAuth()) return@post
                    val id = call.parameters["id"]
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }
                    val desired = runCatching { call.receive<FavoriteRequest>() }.getOrNull()?.favorite ?: true
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

    private suspend fun PipelineContext<Unit, ApplicationCall>.requireAuth(): Boolean {
        if (RemoteAuth.isAuthenticated(call, currentToken())) return true
        call.respond(HttpStatusCode.Unauthorized)
        return false
    }

    private fun buildStateDto(bus: RemoteEventBus): StateDto {
        val favIds = appState.favorites
            .filter { it.playlistId == appState.selectedPlaylistId }
            .map { it.channelId }
        return StateDto(
            playlistName = appState.selectedPlaylist?.name,
            categories = appState.categories,
            channels = appState.channels,
            favoriteChannelIds = favIds,
            nowPlaying = bus.currentNowPlaying(),
        )
    }
}
