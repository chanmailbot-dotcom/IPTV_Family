package com.iptv.family.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.iptv.family.desktop.player.FilePicker
import com.iptv.family.desktop.player.VlcController
import com.iptv.family.desktop.remote.RemoteWebServer
import com.iptv.family.desktop.state.AppState
import com.iptv.family.desktop.theme.AppTheme
import com.iptv.family.desktop.ui.AppStrings
import com.iptv.family.desktop.ui.screens.AddPlaylistDialog
import com.iptv.family.desktop.ui.screens.ChannelsScreen
import com.iptv.family.desktop.ui.screens.FavoritesScreen
import com.iptv.family.desktop.ui.screens.HomeScreen
import com.iptv.family.desktop.ui.screens.PlayerScreen
import com.iptv.family.desktop.ui.screens.SettingsScreen
import com.iptv.family.shared.data.repository.LibraryRepository
import com.iptv.family.shared.data.store.FileKeyValueStore
import com.iptv.family.shared.log.AppLog
import com.iptv.family.shared.model.Channel
import kotlinx.coroutines.launch
import java.io.File
import com.iptv.family.desktop.security.DesktopVault

private enum class Destination(val label: String, val icon: ImageVector) {
    HOME(AppStrings.Nav.HOME, Icons.Rounded.VideoLibrary),
    CHANNELS(AppStrings.Nav.CHANNELS, Icons.Rounded.LiveTv),
    FAVORITES(AppStrings.Nav.FAVORITES, Icons.Rounded.Favorite),
    PLAYER(AppStrings.Nav.PLAYER, Icons.Rounded.PlayCircle),
    SETTINGS(AppStrings.Nav.SETTINGS, Icons.Rounded.Settings),
}

fun main() {
    // El video de libvlc va en un SwingPanel, que es un componente AWT "pesado":
    // una ventana nativa hija que se pinta por encima de todo lo que dibuje
    // Compose en el mismo lienzo. Por eso el desplegable de audio se abria
    // "detras" de la imagen.
    //
    // Con layers.type=WINDOW los popups de Compose (menus, tooltips, dialogos)
    // pasan a ser ventanas reales del sistema en vez de capas dentro del lienzo,
    // y una ventana propia SI queda por encima del video. Es el mismo truco que
    // usa Swing con sus menus "heavyweight".
    //
    // Hay que fijarlo antes de crear la ventana. Se puede sobreescribir desde
    // fuera con -Dcompose.layers.type=... para comparar comportamientos.
    if (System.getProperty("compose.layers.type") == null) {
        System.setProperty("compose.layers.type", "WINDOW")
    }
    mainWindow()
}

private fun mainWindow() = application {
    val windowState = rememberWindowState(width = 1360.dp, height = 820.dp)

    val appState = remember {
        val dir = File(System.getProperty("user.home"), ".iptv-family").apply { mkdirs() }
        AppLog.init(File(dir, "logs"))
        AppState(LibraryRepository(FileKeyValueStore(dir), vault = DesktopVault.create()))
    }
    // Un unico reproductor por modo de video: crear uno por canal filtra memoria nativa.
    // Cambiar el modo en Ajustes obliga a construir otro, y a soltar el viejo.
    val compatibilityMode = appState.settings.videoCompatibilityMode
    val controller = remember(compatibilityMode) {
        runCatching { VlcController(compatibilityMode) }
            .onFailure { AppLog.e("Main", "No se pudo crear VlcController", it) }
            .getOrNull()
    }

    val remoteScope = rememberCoroutineScope()
    // Clave `controller`: al cambiar el modo compatibilidad se construye otro
    // VlcController y se libera el viejo. Sin esta clave el servidor web seguia
    // apuntando al controller ya liberado y la web se quedaba muda/negra hasta
    // reiniciar la app.
    val remoteServer = remember(controller) { RemoteWebServer(appState, controller, remoteScope) }
    val webServerEnabled = appState.settings.enableWebServer
    val webServerPort = appState.settings.webServerPort
    // `remoteServer` va en las claves: cuando se sustituye la instancia hay que
    // parar la anterior (onDispose) antes de levantar la nueva, o el puerto
    // seguiria ocupado por el engine viejo y la nueva no podria escuchar.
    DisposableEffect(remoteServer, webServerEnabled, webServerPort) {
        if (webServerEnabled) {
            runCatching { remoteServer.start(webServerPort) }
                .onFailure { AppLog.e("Main", "No se pudo iniciar el servidor web", it) }
        }
        onDispose { remoteServer.stop() }
    }

    var destination by remember { mutableStateOf(Destination.HOME) }
    var playing by remember { mutableStateOf<Channel?>(null) }
    var zapList by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        onDispose { controller?.release() }
    }

    LaunchedEffect(isFullscreen) {
        windowState.placement = if (isFullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
    }

    // Carga la guia EPG (XMLTV) cuando cambia la lista activa; en Xtream usa
    // la url estandar del panel si el usuario no puso una.
    LaunchedEffect(appState.selectedPlaylistId) {
        appState.loadEpg()
    }

    // Solo navega: PlayerScreen arranca el stream cuando su superficie existe.
    fun play(channel: Channel, list: List<Channel>) {
        AppLog.d("Main", "Usuario pide reproducir '${channel.name}' (${AppLog.redactUrl(channel.url)})")
        playing = channel
        zapList = list
        destination = Destination.PLAYER
    }

    // Zapeo circular sobre la lista con la que se empezo a reproducir.
    fun zap(delta: Int) {
        val current = playing ?: return
        if (zapList.isEmpty()) return
        val index = zapList.indexOfFirst { it.id == current.id }
        play(zapList[(index + delta).mod(zapList.size)], zapList)
    }

    Window(
        onCloseRequest = {
            // El servidor web tiene el puerto abierto y un pool de corrutinas: si no
            // se para aqui, cerrar la ventana deja el proceso vivo en segundo plano.
            remoteServer.stop()
            controller?.release()
            exitApplication()
        },
        state = windowState,
        title = playing?.let { "${it.name} ${AppStrings.WINDOW_TITLE_SUFFIX}" } ?: AppStrings.APP_TITLE,
        // Atajos del reproductor. SOLO valen estando en la pantalla del
        // reproductor, que es la unica sin campos de texto.
        //
        // Antes bastaba con que hubiera un canal cargado, asi que escribir en el
        // buscador de Canales era imposible: la "n" saltaba al canal siguiente,
        // la "p" al anterior y ambas te expulsaban a la pantalla del reproductor
        // (`play()` cambia de destino). Comprobado escribiendo "span news p":
        // dos cambios de canal no pedidos con 9 ms de diferencia.
        onKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) return@Window false
            // Salir de pantalla completa se atiende siempre: es la via de escape.
            if (event.key == Key.Escape && isFullscreen) {
                isFullscreen = false
                return@Window true
            }
            if (destination != Destination.PLAYER || playing == null) return@Window false
            when (event.key) {
                Key.F -> { isFullscreen = !isFullscreen; true }
                Key.Spacebar -> { controller?.togglePlayPause(); true }
                Key.M -> { controller?.changeMuted(controller?.isMuted != true); true }
                Key.DirectionUp -> { controller?.changeVolume((controller?.volume ?: 80) + 5); true }
                Key.DirectionDown -> { controller?.changeVolume((controller?.volume ?: 80) - 5); true }
                Key.N -> { zap(+1); true }
                Key.P -> { zap(-1); true }
                else -> false
            }
        },
    ) {
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) { appState.loadAll() }

        // El servidor remoto reusa siempre la misma funcion play() que la UI local,
        // asi que un cambio de canal desde el navegador se refleja igual que uno local.
        // En SideEffect y no en el cuerpo: asignar estado externo durante la
        // composicion se ejecuta tambien en composiciones descartadas.
        SideEffect {
            remoteServer.onRemotePlayRequest = { channel -> play(channel, appState.channels) }
        }

        // El tema Sistema se resuelve dentro de AppTheme (isSystemInDarkTheme),
        // asi que aqui solo pasamos la preferencia del usuario.
        AppTheme(appState.settings.selectedTheme) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Row(Modifier.fillMaxSize()) {
                    if (!isFullscreen) {
                        NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                            Spacer(Modifier.height(8.dp))
                            Destination.entries.forEach { entry ->
                                // "Reproduciendo" solo tiene sentido cuando hay algo abierto.
                                if (entry == Destination.PLAYER && playing == null) return@forEach
                                NavigationRailItem(
                                    selected = destination == entry,
                                    onClick = { destination = entry },
                                    icon = { Icon(entry.icon, contentDescription = entry.label) },
                                    label = { Text(entry.label, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }

                    Box(Modifier.weight(1f).fillMaxSize()) {
                        when (destination) {
                            Destination.HOME -> HomeScreen(
                                appState = appState,
                                scope = scope,
                                onAddClick = { showAddDialog = true },
                                onOpenChannels = { destination = Destination.CHANNELS },
                            )

                            Destination.CHANNELS -> ChannelsScreen(
                                appState = appState,
                                scope = scope,
                                onPlay = ::play,
                                onGoHome = { destination = Destination.HOME },
                            )

                            Destination.FAVORITES -> FavoritesScreen(
                                appState = appState,
                                scope = scope,
                                onPlay = ::play,
                            )

                            Destination.SETTINGS -> SettingsScreen(appState, scope)

                            Destination.PLAYER -> {
                                val channel = playing
                                if (channel == null || controller == null) {
                                    NoPlayerAvailable(hasChannel = channel != null)
                                } else {
                                    PlayerScreen(
                                        controller = controller,
                                        channel = channel,
                                        zapList = zapList,
                                        appState = appState,
                                        scope = scope,
                                        bufferMs = appState.settings.bufferMs,
                                        hardwareDecoding = appState.settings.enableHardwareDecoding,
                                        isFullscreen = isFullscreen,
                                        onToggleFullscreen = { isFullscreen = !isFullscreen },
                                        onSelectChannel = { play(it, zapList) },
                                        onBack = {
                                            isFullscreen = false
                                            destination = Destination.CHANNELS
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showAddDialog) {
                AddPlaylistDialog(
                    scope = scope,
                    onDismiss = { showAddDialog = false },
                    onAddM3uUrl = { name, url, epg -> scope.launch { appState.addM3uUrl(name, url, epg) } },
                    onAddXtream = { name, url, user, pass, epg -> scope.launch { appState.addXtream(name, url, user, pass, epg) } },
                    onAddM3uFile = { name, content -> scope.launch { appState.addM3uFile(name, content) } },
                    onChooseFile = FilePicker::chooseM3uFile,
                )
            }
        }
    }
}

@Composable
private fun NoPlayerAvailable(hasChannel: Boolean) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (hasChannel) "No se encontró el motor de vídeo (libvlc)" else "No hay ningún canal abierto",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (hasChannel) {
                    "El instalador de IPTV Family incluye el motor. Si ejecutas desde el código " +
                        "fuente, instala VLC en el sistema y reinicia."
                } else {
                    "Elige un canal en «Canales» o «Favoritos»."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
