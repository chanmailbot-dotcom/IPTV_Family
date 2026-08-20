package com.iptv.family.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.found.Close
import androidx.compose.material.icons.found.PlayArrow
import androidx.compose.material.icons.found.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iptv.family.desktop.state.AppState
import com.iptv.family.shared.model.ThemeType
import kotlinx.coroutines.launch

@Composable
fun PlayerBanner(
    channel: com.iptv.family.shared.model.Channel?,
    onStop: () -> Unit,
    onPlay: () -> Unit,
) {
    if (channel == null) return
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(channel.name, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("Reproduciendo en reproductor externo", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
        }
        IconButton(onPlay) { Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir", tint = Color.Green) }
        IconButton(onStop) { Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun SettingsScreen(appState: AppState, scope: kotlinx.coroutines.CoroutineScope) {
    val s = appState.settings
    val scroll = rememberScrollState()
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        row("Tema oscuro", checked = s.selectedTheme != ThemeType.LIGHT) {
            scope.launch { appState.mutateSettings { copy(selectedTheme = if (it) ThemeType.DARK else ThemeType.LIGHT) } }
        }
        row("Control parental", checked = s.isParentalLockEnabled) {
            scope.launch { appState.mutateSettings { copy(isParentalLockEnabled = it) } }
        }

        var pin by remember { mutableStateOf(s.parentalPin ?: "") }
        OutlinedTextField(pin, { if (it.length <= 6) pin = it }, label = { Text("PIN parental") },
            singleLine = true,
            enabled = s.isParentalLockEnabled,
            modifier = Modifier.fillMaxWidth())
        Button({ scope.launch { appState.mutateSettings { copy(parentalPin = pin.ifEmpty { null }) } } }) { Text("Guardar PIN") }

        OutlinedTextField(s.locale, { scope.launch { appState.mutateSettings { copy(locale = it) } } },
            label = { Text("Idioma (ej. es, en)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Column(Modifier.fillMaxWidth()) {
            Text("Buffer: ${s.bufferMs} ms", style = MaterialTheme.typography.bodySmall)
            Slider(s.bufferMs / 1000f, { scope.launch { appState.mutateSettings { copy(bufferMs = it.toInt() * 1000) } } },
                valueRange = 5f..60f)
        }

        row("Hardware decoding", checked = s.enableHardwareDecoding) {
            scope.launch { appState.mutateSettings { copy(enableHardwareDecoding = it) } }
        }
        row("Subtítulos", checked = s.enableSubtitles) {
            scope.launch { appState.mutateSettings { copy(enableSubtitles = it) } }
        }
        row("Reproducir siguiente", checked = s.autoPlayNext) {
            scope.launch { appState.mutateSettings { copy(autoPlayNext = it) } }
        }
        row("Chromecast", checked = s.enableChromecast) {
            scope.launch { appState.mutateSettings { copy(enableChromecast = it) } }
        }
    }
}

@Composable
private fun row(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked, onChecked)
    }
}
