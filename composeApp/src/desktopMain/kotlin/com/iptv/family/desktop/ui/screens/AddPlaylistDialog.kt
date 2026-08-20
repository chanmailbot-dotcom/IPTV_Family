package com.iptv.family.desktop.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.found.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.io.File

enum class AddMode { URL_M3U, XTREAM, FILE }

private val AddMode.label: String get() = when (this) {
    AddMode.URL_M3U -> "Url"
    AddMode.XTREAM -> "Xtream"
    AddMode.FILE -> "Archivo"
}

@Composable
fun AddPlaylistDialog(
    onDismiss: () -> Unit,
    onAddM3uUrl: (name: String, url: String) -> Unit,
    onAddXtream: (name: String, url: String, user: String, pass: String) -> Unit,
    onAddM3uFile: (name: String, content: String) -> Unit,
    onLoadFile: () -> String?,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(AddMode.URL_M3U) }
    var url by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var fileContent by rememberSaveable { mutableStateOf<String?>(null) }
    var fileName by rememberSaveable { mutableStateOf<String?>(null) }

    val canSave: Boolean get() = name.isNotBlank() && when (mode) {
        AddMode.URL_M3U -> url.isNotBlank()
        AddMode.XTREAM -> url.isNotBlank() && user.isNotBlank()
        AddMode.FILE -> fileContent != null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir lista") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AddMode.entries.forEach { m ->
                        RadioButton(m == mode, { mode = m })
                        Text(m.label)
                    }
                }
                when (mode) {
                    AddMode.URL_M3U -> OutlinedTextField(url, { url = it }, label = { Text("URL .m3u") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    AddMode.XTREAM -> {
                        OutlinedTextField(url, { url = it }, label = { Text("URL del panel") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(user, { user = it }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(pass, { pass = it }, label = { Text("Contraseña") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    }
                    AddMode.FILE -> {
                        Button(onClick = {
                            val p = onLoadFile()
                            if (p != null) {
                                fileName = p
                                fileContent = File(p).readText()
                            }
                        }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Text("  Cargar archivo .m3u")
                        }
                        fileName?.let { Text("Seleccionado: ${File(it).name}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (mode) {
                        AddMode.URL_M3U -> onAddM3uUrl(name, url)
                        AddMode.XTREAM -> onAddXtream(name, url, user, pass)
                        AddMode.FILE -> fileContent?.let { onAddM3uFile(name, it) }
                    }
                    onDismiss()
                },
                enabled = canSave,
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
    )
}
