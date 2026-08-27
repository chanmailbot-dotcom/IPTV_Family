package com.iptv.family.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iptv.family.state.AppState
import com.iptv.family.shared.model.Playlist
import com.iptv.family.shared.model.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    appState: AppState,
    scope: CoroutineScope,
    onOpenChannels: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Playlist?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Mis listas",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Button({ showAddDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Añadir lista")
            }
        }

        appState.error?.let { message ->
            Row(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                    .padding(14.dp),
            ) {
                Text(message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                TextButton({ scope.launch { appState.refresh() } }) { Text("Reintentar") }
            }
        }

        if (appState.playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Todavía no hay ninguna lista. Añade una URL M3U o tus datos de Xtream Codes.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(appState.playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        isSelected = playlist.id == appState.selectedPlaylistId,
                        channelCount = if (playlist.id == appState.selectedPlaylistId) appState.channels.size else null,
                        isLoading = appState.isLoading && playlist.id == appState.selectedPlaylistId,
                        onSelect = { scope.launch { appState.selectPlaylist(playlist.id) } },
                        onOpen = onOpenChannels,
                        onDelete = { pendingDelete = playlist },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddPlaylistDialog(
            onDismiss = { showAddDialog = false },
            onAddM3uUrl = { name, url -> scope.launch { appState.addM3uUrl(name, url) } },
            onAddXtream = { name, url, user, pass -> scope.launch { appState.addXtream(name, url, user, pass) } },
        )
    }

    pendingDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar lista") },
            text = { Text("¿Seguro que quieres eliminar «${playlist.name}»?") },
            confirmButton = {
                Button({ scope.launch { appState.deletePlaylist(playlist.id) }; pendingDelete = null }) { Text("Eliminar") }
            },
            dismissButton = { TextButton({ pendingDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    isSelected: Boolean,
    channelCount: Int?,
    isLoading: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    // Un unico foco para "abrir esta lista" (toda la tarjeta) y otro aparte para
    // "eliminar", en vez de anidar un boton focosable dentro de una fila que
    // tambien lo era: con el mando, saltar de un foco al siguiente en linea es
    // predecible; entrar y salir de focos anidados no lo es.
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .tvFocusable(MaterialTheme.shapes.large)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.shapes.large,
                )
                .clickable(onClick = if (isSelected) onOpen else onSelect)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                if (playlist.type == SourceType.XTREAM) Icons.Rounded.Person else Icons.Rounded.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    buildString {
                        append(if (playlist.type == SourceType.XTREAM) "Xtream Codes" else "Lista M3U")
                        if (channelCount != null) append(" · $channelCount canales")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else if (isSelected) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Ver canales", tint = MaterialTheme.colorScheme.primary)
            }
        }
        Box(
            Modifier
                .tvFocusable(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.large)
                .clickable(onClick = onDelete)
                .padding(14.dp),
        ) {
            Icon(Icons.Rounded.Delete, contentDescription = "Eliminar")
        }
    }
}

private enum class AddMode(val label: String) { URL_M3U("URL M3U"), XTREAM("Xtream Codes") }

@Composable
private fun AddPlaylistDialog(
    onDismiss: () -> Unit,
    onAddM3uUrl: (name: String, url: String) -> Unit,
    onAddXtream: (name: String, url: String, user: String, pass: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(AddMode.URL_M3U) }
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    val canSave = name.isNotBlank() && when (mode) {
        AddMode.URL_M3U -> url.isNotBlank()
        AddMode.XTREAM -> url.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir lista") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AddMode.entries.forEachIndexed { index, entry ->
                        SegmentedButton(
                            selected = mode == entry,
                            onClick = { mode = entry },
                            shape = SegmentedButtonDefaults.itemShape(index, AddMode.entries.size),
                            label = { Text(entry.label) },
                        )
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Nombre de la lista") }, singleLine = true)
                OutlinedTextField(
                    url, { url = it },
                    label = { Text(if (mode == AddMode.XTREAM) "URL del panel" else "URL de la lista") },
                    singleLine = true,
                )
                if (mode == AddMode.XTREAM) {
                    OutlinedTextField(user, { user = it }, label = { Text("Usuario") }, singleLine = true)
                    OutlinedTextField(pass, { pass = it }, label = { Text("Contraseña") }, singleLine = true)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (mode) {
                        AddMode.URL_M3U -> onAddM3uUrl(name.trim(), url.trim())
                        AddMode.XTREAM -> onAddXtream(name.trim(), url.trim(), user.trim(), pass)
                    }
                    onDismiss()
                },
                enabled = canSave,
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
    )
}
