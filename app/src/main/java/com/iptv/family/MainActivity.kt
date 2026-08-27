package com.iptv.family

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.iptv.family.player.ExoPlayerController
import com.iptv.family.shared.data.repository.LibraryRepository
import com.iptv.family.shared.data.store.FileKeyValueStore
import com.iptv.family.shared.log.AppLog
import com.iptv.family.shared.model.CategoryType
import com.iptv.family.shared.model.Channel
import com.iptv.family.state.AppState
import com.iptv.family.theme.IptvFamilyTheme
import com.iptv.family.ui.ChannelsScreen
import com.iptv.family.ui.HomeScreen
import com.iptv.family.ui.MiniPlayerPreview
import com.iptv.family.ui.PlayerScreen
import com.iptv.family.ui.SettingsScreen
import java.io.File

private enum class Destination(val label: String, val icon: ImageVector) {
    HOME("Mis listas", Icons.Rounded.VideoLibrary),
    LIVE("TV en vivo", Icons.Rounded.LiveTv),
    MOVIES("Películas", Icons.Rounded.Movie),
    SERIES("Series", Icons.Rounded.Tv),
    FAVORITES("Favoritos", Icons.Rounded.Favorite),
    PLAYER("Reproduciendo", Icons.Rounded.LiveTv),
    SETTINGS("Ajustes", Icons.Rounded.Settings),
}

class MainActivity : ComponentActivity() {

    private lateinit var appState: AppState
    private var controller: ExoPlayerController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppLog.init(File(filesDir, "logs"))
        appState = AppState(LibraryRepository(FileKeyValueStore(filesDir)))
        controller = runCatching { ExoPlayerController(this) }
            .onFailure { AppLog.e("Main", "No se pudo crear ExoPlayerController", it) }
            .getOrNull()

        setContent {
            var destination by remember { mutableStateOf(Destination.HOME) }
            var playing by remember { mutableStateOf<Channel?>(null) }
            var zapList by remember { mutableStateOf<List<Channel>>(emptyList()) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) { appState.loadAll() }

            // Carga el canal sin navegar a pantalla completa: la usa la previsualizacion
            // por foco en "TV en vivo" y la miniatura persistente en el resto de secciones.
            fun preview(channel: Channel, list: List<Channel>) {
                playing = channel
                zapList = list
                controller?.play(channel.url)
            }

            // Confirmar (OK): si ya se estaba previsualizando el mismo canal, no reinicia
            // la reproduccion (ver guarda en ExoPlayerController.play), solo navega.
            fun play(channel: Channel, list: List<Channel>) {
                AppLog.d("Main", "Usuario pide reproducir '${channel.name}' (${AppLog.redactUrl(channel.url)})")
                preview(channel, list)
                destination = Destination.PLAYER
            }

            IptvFamilyTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Row(Modifier.fillMaxSize()) {
                        if (destination != Destination.PLAYER) {
                            NavigationRail {
                                Destination.entries.forEach { entry ->
                                    if (entry == Destination.PLAYER) return@forEach
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
                                    onOpenChannels = { destination = Destination.LIVE },
                                )
                                Destination.LIVE -> ChannelsScreen(
                                    appState = appState,
                                    scope = scope,
                                    mediaType = CategoryType.LIVE,
                                    onPlay = ::play,
                                    onPreview = ::preview,
                                    previewController = controller,
                                    restoreFocusChannelId = playing?.id,
                                )
                                Destination.MOVIES -> ChannelsScreen(
                                    appState = appState,
                                    scope = scope,
                                    mediaType = CategoryType.VOD,
                                    onPlay = ::play,
                                )
                                Destination.SERIES -> ChannelsScreen(
                                    appState = appState,
                                    scope = scope,
                                    mediaType = CategoryType.SERIES,
                                    onPlay = ::play,
                                )
                                Destination.FAVORITES -> ChannelsScreen(
                                    appState = appState,
                                    scope = scope,
                                    mediaType = null,
                                    onPlay = ::play,
                                )
                                Destination.SETTINGS -> SettingsScreen(appState, scope)
                                Destination.PLAYER -> {
                                    val channel = playing
                                    val ctrl = controller
                                    if (channel != null && ctrl != null) {
                                        PlayerScreen(
                                            controller = ctrl,
                                            channel = channel,
                                            zapList = zapList,
                                            onSelectChannel = { ch -> preview(ch, zapList) },
                                            onBack = { destination = Destination.LIVE },
                                        )
                                    }
                                }
                            }

                            // El canal sigue reproduciendose en una miniatura mientras se navega
                            // por el resto de la app (igual que TiviMate); tocarla vuelve a
                            // pantalla completa.
                            val current = playing
                            val ctrl = controller
                            if (destination != Destination.PLAYER && current != null && ctrl != null) {
                                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
                                    MiniPlayerPreview(
                                        controller = ctrl,
                                        channelName = current.name,
                                        onExpand = { destination = Destination.PLAYER },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        controller?.release()
        super.onDestroy()
    }
}
