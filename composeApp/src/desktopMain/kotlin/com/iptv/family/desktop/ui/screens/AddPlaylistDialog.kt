package com.iptv.family.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import com.iptv.family.shared.i18n.T
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class AddMode(val label: String) {
    URL_M3U("URL M3U"),
    XTREAM("Xtream Codes"),
    FILE("Archivo"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlaylistDialog(
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    onAddM3uUrl: (name: String, url: String, epgUrl: String?) -> Unit,
    onAddXtream: (name: String, url: String, user: String, pass: String, epgUrl: String?) -> Unit,
    onAddM3uFile: (name: String, content: String) -> Unit,
    onChooseFile: () -> String?,
) {
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(AddMode.URL_M3U) }
    var url by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var filePath by remember { mutableStateOf<String?>(null) }
    var fileContent by remember { mutableStateOf<String?>(null) }
    var fileError by remember { mutableStateOf<String?>(null) }

    val canSave = name.isNotBlank() && when (mode) {
        AddMode.URL_M3U -> url.isNotBlank()
        AddMode.XTREAM -> url.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
        AddMode.FILE -> fileContent != null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T.añadirLista) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(T.nombreDeLaLista) },
                    placeholder = { Text(T.ejemploNombreLista) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                when (mode) {
                    AddMode.URL_M3U -> OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(T.urlDeLaLista) },
                        placeholder = { Text("http://servidor.com/lista.m3u") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    AddMode.XTREAM -> {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(T.urlDelPanel) },
                            placeholder = { Text("http://servidor.com:8080") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = user,
                            onValueChange = { user = it },
                            label = { Text(T.usuario) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it },
                            label = { Text(T.contrasena) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    AddMode.FILE -> {
                        OutlinedButton(
                            onClick = {
                                val path = onChooseFile() ?: return@OutlinedButton
                                filePath = path
                                fileError = null
                                fileContent = null
                                scope.launch {
                                    // Las listas locales llegan a decenas de MB: leer fuera del hilo de UI.
                                    val result = withContext(Dispatchers.IO) { runCatching { File(path).readText() } }
                                    result.fold(
                                        onSuccess = { text ->
                                            fileContent = text
                                            if (name.isBlank()) name = File(path).nameWithoutExtension
                                        },
                                        onFailure = { fileError = T.noSePudoLeerElArchivo(it.message.orEmpty()) },
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(T.elegirArchivoM3u)
                        }
                        filePath?.let { path ->
                            // El contenido se captura en un local: es estado de
                            // Compose y puede cambiar entre comprobarlo y usarlo,
                            // asi que forzarlo con `!!` era un fallo esperando.
                            val contenido = fileContent
                            Text(
                                when {
                                    fileError != null -> fileError.orEmpty()
                                    contenido == null -> "Leyendo ${File(path).name}…"
                                    else -> "${File(path).name} · ${contenido.length / 1024} KB"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (fileError != null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // UN solo campo de guia, y solo donde tiene efecto. Antes habia dos
                // ligados a la misma variable -- se escribia en uno y cambiaba el
                // otro -- y ademas aparecia uno en modo Archivo, donde
                // `onAddM3uFile` ni siquiera recibe la URL: lo que se escribiera
                // alli se tiraba sin avisar.
                if (mode != AddMode.FILE) {
                    Spacer(Modifier.height(2.dp))
                    OutlinedTextField(
                        value = epgUrl,
                        onValueChange = { epgUrl = it },
                        label = { Text(T.guiaEpgOpcional) },
                        placeholder = {
                            Text(
                                if (mode == AddMode.XTREAM) T.urlVacioSeToma
                                else "http://…/xmltv.php"
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    when (mode) {
                        AddMode.URL_M3U -> T.ayudaM3uUrl
                        AddMode.XTREAM -> T.ayudaXtream
                        AddMode.FILE -> T.ayudaM3uArchivo
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (mode) {
                        AddMode.URL_M3U -> onAddM3uUrl(name.trim(), url.trim(), epgUrl.trim())
                        AddMode.XTREAM -> onAddXtream(name.trim(), url.trim(), user.trim(), pass, epgUrl.trim())
                        AddMode.FILE -> fileContent?.let { onAddM3uFile(name.trim(), it) }
                    }
                    onDismiss()
                },
                enabled = canSave,
            ) { Text(T.guardarYCargar) }
        },
        dismissButton = { TextButton(onDismiss) { Text(T.cancelar) } },
    )
}
