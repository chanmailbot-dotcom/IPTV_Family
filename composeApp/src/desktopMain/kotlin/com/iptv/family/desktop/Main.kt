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
import com.iptv.family.desktop.state.AppState
import com.iptv.family.desktop.theme.AppTheme
import com.iptv.family.desktop.theme.AppThemeMode
import com.iptv.family.desktop.ui.screens.AddPlaylistDialog
import com.iptv.family.desktop.ui.screens.ChannelsScreen
import com.iptv.family.desktop.ui.screens.FavoritesScreen
import com.iptv.family.desktop.ui.screens.HomeScreen
import com.iptv.family.desktop.ui.screens.PlayerScreen
import com.iptv.family.desktop.ui.screens.SettingsScreen
import com.iptv.family.shared.data.repository.LibraryRepository
import com.iptv.family.shared.data.store.FileKeyValueStore
import com.iptv.family.shared.model.Channel
import com.iptv.family.shared.model.ThemeType
import kotlinx.coroutines.launch
import java.io.File

private enum class Destination(val label: String, val icon: ImageVector) {
    HOME("Mis listas", Icons.Rounded.VideoLibrary),
    CHANNELS("Canales", Icons.Rounded.LiveTv),
    FAVORITES("Favoritos", Icons.Rounded.Favorite),
    PLAYER("Reproduciendo", Icons.Rounded.PlayCircle),
    SETTINGS("Ajustes", Icons.Rounded.Settings),
}

fun main() = application {
    val windowState = rememberWindowState(width = 1360.dp, height = 820.dp)

    val appState = remember {
        val dir = File(System.getProperty("user.home"), ".iptv-family").apply { mkdirs() }
        AppState(LibraryRepository(FileKeyValueStore(dir)))
    }
    // Un unico reproductor por modo de video: crear uno por canal filtra memoria nativa.
    // Cambiar el modo en Ajustes obliga a construir otro, y a soltar el viejo.
    val compatibilityMode = appState.settings.videoCompatibilityMode
    val controller = remember(compatibilityMode) {
        runCatching { VlcController(compatibilityMode) }.getOrNull()
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

    Window(
        onCloseRequest = {
            controller?.release()
            exitApplication()
        },
        state = windowState,
        title = playing?.let { "${it.name} · IPTV Family" } ?: "IPTV Family",
        onKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) return@Window false
            when {
                event.key == Key.Escape && isFullscreen -> { isFullscreen = false; true }
                event.key == Key.F && playing != null -> { isFullscreen = !isFullscreen; true }
                event.key == Key.Spacebar && playing != null -> { controller?.togglePlayPause(); true }
                else -> false
            }
        },
    ) {
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) { appState.loadAll() }

        val mode = if (appState.settings.selectedTheme == ThemeType.LIGHT) AppThemeMode.LIGHT else AppThemeMode.DARK

        // Solo navega: PlayerScreen arranca el stream cuando su superficie existe.
        fun play(channel: Channel, list: List<Channel>) {
            playing = channel
            zapList = list
            destination = Destination.PLAYER
        }

        AppTheme(mode) {
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
                                    icon = { Icon(entry.icon, contentDescription = null) },
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
                    onAddM3uUrl = { name, url -> scope.launch { appState.addM3uUrl(name, url) } },
                    onAddXtream = { name, url, user, pass -> scope.launch { appState.addXtream(name, url, user, pass) } },
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
