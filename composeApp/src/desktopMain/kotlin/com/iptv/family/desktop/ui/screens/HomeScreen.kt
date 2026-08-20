package com.iptv.family.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iptv.family.desktop.state.AppState
import com.iptv.family.shared.model.SourceType
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(appState: AppState, scope: kotlinx.coroutines.CoroutineScope, onAddClick: () -> Unit) {
    val snack = remember { mutableStateOf<SnackbarHostState?>(null) }
    val host = snack.value ?: rememberSnackbarHostState().also { snack.value = it }
    val scaffoldState = host
    val playlists = appState.playlists
    Box(Modifier.fillMaxSize()) {
        if (playlists.isEmpty()) {
            EmptyHome(onAddClick)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                items(playlists, key = { it.id }) { pl ->
                    PlaylistRow(
                        playlist = pl,
                        isSelected = appState.selectedPlaylistId == pl.id,
                        onSelect = { scope.launch { appState.selectPlaylist(pl.id) } },
                        onRefresh = { scope.launch { appState.refresh() } },
                        onDelete = { scope.launch { appState.deletePlaylist(pl.id) } },
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = onAddClick,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Default.Add, contentDescription = "Añadir", tint = Color.White) }
    }
}

@Composable
private fun EmptyHome(onAddClick: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.align(Alignment.Center).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Aún no hay listas.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Añade una lista M3U o conéctate a un panel Xtream Codes para empezar.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            TextButton(onClick = onAddClick) { Text("Añadir lista") }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: com.iptv.family.shared.model.Playlist,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    val iconTint = when (playlist.type) {
        SourceType.M3U_URL, SourceType.M3U_FILE -> MaterialTheme.colorScheme.primary
        SourceType.XTREAM -> MaterialTheme.colorScheme.secondary
    }
    ListItem(
        headlineText = { Text(playlist.name) },
        supportingText = { Text(playlist.type.name + if (playlist.isActive) " • activa" else "") },
        leadingContent = {
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = iconTint, modifier = Modifier.size(32.dp))
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") }
                IconButton(onSelect) { Icon(Icons.Default.Select, contentDescription = "Seleccionar") }
                IconButton(onDelete) { Icon(Icons.Default.Delete, contentDescription = "Eliminar") }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(0.12f) else Color.Transparent)
            .clickable(onSelect),
    )
}
