package com.iptv.family.desktop

import androidx.compose.desktop.application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.found.Add
import androidx.compose.material.icons.found.Favorite
import androidx.compose.material.icons.found.Home
import androidx.compose.material.icons.found.PlayArrow
import androidx.compose.material.icons.found.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.singleWindowApplication
import com.iptv.family.desktop.player.ExternalPlayer
import com.iptv.family.desktop.state.AppState
import com.iptv.family.desktop.theme.AppTheme
import com.iptv.family.desktop.theme.AppThemeMode
import com.iptv.family.desktop.ui.screens.AddPlaylistDialog
import com.iptv.family.desktop.ui.screens.ChannelsScreen
import com.iptv.family.desktop.ui.screens.FavoritesScreen
import com.iptv.family.desktop.ui.screens.HomeScreen
import com.iptv.family.desktop.ui.screens.PlayerBanner
import com.iptv.family.desktop.ui.screens.SettingsScreen
import com.iptv.family.shared.data.repository.LibraryRepository
import com.iptv.family.shared.data.store.FileKeyValueStore
import com.iptv.family.shared.model.Channel
import kotlinx.coroutines.launch
import java.io.File

private val tabs = listOf("Inicio", "Canales", "Favoritos", "Ajustes")

fun main() = application {
    val windowState = WindowState(width = 1200.dp, height = 740.dp)
    singleWindowApplication(
        state = windowState,
        title = "IPTV Family",
        onCloseRequest = ::exitApplication,
    ) {
                val appState = remember {
            val dir = File(System.getProperty("user.home"), ".iptv-family").apply { mkdirs() }
            AppState(LibraryRepository(FileKeyValueStore(dir)))
        }
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) { appState.loadAll() }

        var selected by remember { mutableStateOf(0) }
        var currentChannel by remember { mutableStateOf<Channel?>(null) }
        var showAddDialog by remember { mutableStateOf(false) }
        val mode = if (appState.settings.selectedTheme.name == "LIGHT") AppThemeMode.LIGHT else AppThemeMode.DARK

        AppTheme(mode) {
            Scaffold(
                topBar = { CenterAlignedTopAppBar(title = { Text("IPTV Family") }) },
                bottomBar = {
                    PlayerBanner(
                        channel = currentChannel,
                        onStop = { currentChannel = null },
                        onPlay = { currentChannel?.let { ExternalPlayer.play(it.url) } },
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    TabRow(selected = selected, modifier = Modifier.width(240.dp)) {
                        tabs.forEachIndexed { i, title ->
                            Tab(selected = selected == i, onClick = { selected = i }, text = { Text(title) })
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        when (selected) {
                            0 -> HomeScreen(appState, scope, { showAddDialog = true })
                            1 -> ChannelsScreen(appState, scope) { ch ->
                                currentChannel = ch
                                ExternalPlayer.play(ch.url)
                            }
                            2 -> FavoritesScreen(appState) { ch ->
                                currentChannel = ch
                                ExternalPlayer.play(ch.url)
                            }
                            3 -> SettingsScreen(appState, scope)
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddPlaylistDialog(
                onDismiss = { showAddDialog = false },
                onAddM3uUrl = { name, url -> scope.launch { appState.addM3uUrl(name, url) }; showAddDialog = false },
                onAddXtream = { name, url, user, pass -> scope.launch { appState.addXtream(name, url, user, pass) }; showAddDialog = false },
                onAddM3uFile = { name, content -> scope.launch { appState.addM3uFile(name, content) }; showAddDialog = false },
                onLoadFile = { ExternalPlayer.chooseM3uFile() },
            )
        }
    }
}
