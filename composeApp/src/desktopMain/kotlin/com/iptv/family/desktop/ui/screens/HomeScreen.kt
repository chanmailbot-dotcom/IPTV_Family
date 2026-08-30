package com.iptv.family.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iptv.family.desktop.state.AppState
import com.iptv.family.desktop.ui.AppStrings
import com.iptv.family.shared.model.Playlist
import com.iptv.family.shared.model.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    appState: AppState,
    scope: CoroutineScope,
    onAddClick: () -> Unit,
    onOpenChannels: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Playlist?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val estrecha = maxWidth < ANCHO_COMPACTO

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // En una ventana estrecha, titulo y boton no caben en la misma linea: el
        // boton se comia el texto y quedaban encima uno de otro. Ahi el boton
        // baja a su propia fila, a lo ancho, en vez de encogerse hasta ser
        // ilegible.
        if (estrecha) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(AppStrings.Home.TITLE, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    AppStrings.Home.SUBTITLE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onAddClick, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(AppStrings.Home.ADD)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(AppStrings.Home.TITLE, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        AppStrings.Home.SUBTITLE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onAddClick) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(AppStrings.Home.ADD)
                }
            }
        }

        appState.error?.let { message ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton({ scope.launch { appState.refresh() } }) { Text(AppStrings.RETRY) }
            }
        }

        if (appState.playlists.isEmpty()) {
            EmptyState(onAddClick)
            return@Column
        }

        appState.playlists.forEach { playlist ->
            PlaylistCard(
                playlist = playlist,
                isSelected = playlist.id == appState.selectedPlaylistId,
                channelCount = if (playlist.id == appState.selectedPlaylistId) appState.channels.size else null,
                isLoading = appState.isLoading && playlist.id == appState.selectedPlaylistId,
                onSelect = { scope.launch { appState.selectPlaylist(playlist.id) } },
                onOpen = onOpenChannels,
                onRefresh = { scope.launch { appState.refresh() } },
                onDelete = { pendingDelete = playlist },
                compacta = estrecha,
            )
        }
    }
    } // BoxWithConstraints

    pendingDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(AppStrings.Home.DELETE_TITLE) },
            text = { Text(AppStrings.Home.deleteConfirm(playlist.name)) },
            confirmButton = {
                Button({
                    scope.launch { appState.deletePlaylist(playlist.id) }
                    pendingDelete = null
                }) { Text(AppStrings.Home.DELETE) }
            },
            dismissButton = { TextButton({ pendingDelete = null }) { Text(AppStrings.CANCEL) } },
        )
    }
}

@Composable
private fun PlaylistCard(
    /** Ventana estrecha: los botones con texto no caben sin comerse el nombre. */
    compacta: Boolean = false,
    playlist: Playlist,
    isSelected: Boolean,
    channelCount: Int?,
    isLoading: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    hovered -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> MaterialTheme.colorScheme.surfaceContainer
                },
                MaterialTheme.shapes.large,
            )
            .then(
                if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                else Modifier,
            )
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { if (isSelected) onOpen() else onSelect() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when (playlist.type) {
                    SourceType.M3U_URL -> Icons.Rounded.Link
                    SourceType.M3U_FILE -> Icons.Rounded.Folder
                    SourceType.XTREAM -> Icons.Rounded.Person
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                buildString {
                    append(playlist.type.readableName)
                    if (channelCount != null) append(" · $channelCount canales")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        if (isLoading) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        } else if (isSelected) {
            // En estrecho el boton con texto se comia el nombre de la lista
            // (quedaba en «Prue») y ademas se solapaba con el. La tarjeta entera
            // ya abre la lista al pulsarla, asi que aqui basta el icono.
            if (compacta) {
                IconButton(onOpen) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = AppStrings.Home.VIEW_CHANNELS,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Button(onOpen) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(AppStrings.Home.VIEW_CHANNELS)
                }
            }
            IconButton(onRefresh) { Icon(Icons.Rounded.Refresh, contentDescription = AppStrings.Home.REFRESH) }
        }

        IconButton(onDelete) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = AppStrings.Home.DELETE,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(onAddClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Text(AppStrings.Home.EMPTY_TITLE, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            AppStrings.Home.EMPTY_BODY,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Button(onAddClick) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(AppStrings.Home.ADD_FIRST)
        }
    }
}

private val SourceType.readableName: String
    get() = when (this) {
        SourceType.M3U_URL -> "Lista M3U por URL"
        SourceType.M3U_FILE -> "Archivo M3U local"
        SourceType.XTREAM -> "Xtream Codes"
    }

/** Por debajo de este ancho, los botones con texto dejan de caber en la fila. */
private val ANCHO_COMPACTO = 760.dp
