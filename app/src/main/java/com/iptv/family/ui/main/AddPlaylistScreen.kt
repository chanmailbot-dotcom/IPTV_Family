package com.iptv.family.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iptv.family.R
import com.iptv.family.ui.theme.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlaylistScreen(
    onPlaylistAdded: () -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel: MainViewModel = hiltViewModel()
    var playlistType by remember { mutableStateOf(PlaylistType.M3U_URL) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var xtreamUrl by remember { mutableStateOf("") }
    var xtreamUser by remember { mutableStateOf("") }
    var xtreamPass by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TopAppBarSmall(
            title = { Text("Añadir lista") },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                }
            }
        )

        Text(
            text = "Tipo de lista",
            style = MaterialTheme.typography.titleMedium,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaylistTypeChip(
                type = PlaylistType.M3U_URL,
                isSelected = playlistType == PlaylistType.M3U_URL,
                onClick = { playlistType = PlaylistType.M3U_URL },
            )
            PlaylistTypeChip(
                type = PlaylistType.M3U_FILE,
                isSelected = playlistType == PlaylistType.M3U_FILE,
                onClick = { playlistType = PlaylistType.M3U_FILE },
            )
            PlaylistTypeChip(
                type = PlaylistType.XTREAM_CODES,
                isSelected = playlistType == PlaylistType.XTREAM_CODES,
                onClick = { playlistType = PlaylistType.XTREAM_CODES },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre de la lista") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        when (playlistType) {
            PlaylistType.M3U_URL -> {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL de la lista M3U") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Url,
                    ),
                )
            }
            PlaylistType.M3U_FILE -> {
                FilePickerButton(
                    onFileSelected = { /* handle file pick */ },
                )
            }
            PlaylistType.XTREAM_CODES -> {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = xtreamUrl,
                        onValueChange = { xtreamUrl = it },
                        label = { Text("URL del panel Xtream") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Url,
                        ),
                    )
                    OutlinedTextField(
                        value = xtreamUser,
                        onValueChange = { xtreamUser = it },
                        label = { Text("Usuario") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = xtreamPass,
                        onValueChange = { xtreamPass = it },
                        label = { Text("Contraseña") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    )
                }
            }
        }

        error?.let { err ->
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text("Cancelar")
            }
            FilledButton(
                onClick = {
                    isLoading = true
                    error = null
                    when (playlistType) {
                        PlaylistType.M3U_URL -> {
                            if (name.isBlank() || url.isBlank()) {
                                error = "Nombre y URL son requeridos"
                                isLoading = false
                            } else {
                                viewModel.addM3UPlaylist(name, url)
                                isLoading = false
                                onPlaylistAdded()
                            }
                        }
                        PlaylistType.M3U_FILE -> {
                            // Handle file
                            error = "Selección de archivo no implementada aún"
                            isLoading = false
                        }
                        PlaylistType.XTREAM_CODES -> {
                            if (name.isBlank() || xtreamUrl.isBlank() || xtreamUser.isBlank() || xtreamPass.isBlank()) {
                                error = "Todos los campos son requeridos"
                                isLoading = false
                            } else {
                                viewModel.addXtreamPlaylist(name, xtreamUrl, xtreamUser, xtreamPass)
                                isLoading = false
                                onPlaylistAdded()
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Guardar")
                }
            }
        }
    }
}

enum class PlaylistType {
    M3U_URL("M3U URL", Icons.Default.Link),
    M3U_FILE("Archivo M3U", Icons.Default.FileDownload),
    XTREAM_CODES("Xtream Codes", Icons.Default.CheckCircle),
}

@Composable
fun PlaylistTypeChip(
    type: PlaylistType,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(type.name) },
        leadingIcon = { Icon(type.icon, contentDescription = null) },
        modifier = Modifier.weight(1f),
        colors = if (isSelected) {
            FilterChipDefaults.selectedChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            FilterChipDefaults.unselectedChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
fun FilePickerButton(onFileSelected: (String) -> Unit) {
    OutlinedButton(
        onClick = { /* launch file picker */ },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Seleccionar archivo M3U")
        }
    }
}