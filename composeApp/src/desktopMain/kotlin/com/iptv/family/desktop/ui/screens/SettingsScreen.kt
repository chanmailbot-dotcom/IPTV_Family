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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.iptv.family.desktop.player.VlcNative
import com.iptv.family.desktop.remote.RemoteAuth
import com.iptv.family.desktop.state.AppState
import com.iptv.family.shared.model.ThemeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

@Composable
fun SettingsScreen(appState: AppState, scope: CoroutineScope) {
    val settings = appState.settings

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Section("Aspecto") {
            ToggleRow(
                title = "Tema oscuro",
                subtitle = "Recomendado para ver la tele de noche",
                checked = settings.selectedTheme != ThemeType.LIGHT,
            ) { dark ->
                scope.launch {
                    appState.mutateSettings { copy(selectedTheme = if (dark) ThemeType.DARK else ThemeType.LIGHT) }
                }
            }
        }

        Section("Reproducción") {
            var buffer by remember(settings.bufferMs) { mutableStateOf(settings.bufferMs) }
            Text(
                "Buffer de red: ${buffer / 1000} s",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Súbelo si la imagen se corta a menudo; bájalo si tarda mucho en arrancar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = (buffer / 1000).toFloat(),
                onValueChange = { buffer = it.toInt() * 1000 },
                onValueChangeFinished = { scope.launch { appState.mutateSettings { copy(bufferMs = buffer) } } },
                valueRange = 1f..60f,
            )

            ToggleRow(
                title = "Modo compatibilidad de vídeo",
                subtitle = "Actívalo solo si oyes el canal pero la imagen sale en negro. " +
                    "Consume algo más de procesador.",
                checked = settings.videoCompatibilityMode,
            ) { enabled ->
                scope.launch { appState.mutateSettings { copy(videoCompatibilityMode = enabled) } }
            }

            ToggleRow(
                title = "Aceleración por hardware",
                subtitle = "Descarga la decodificación en la gráfica. Desactívala si ves la imagen corrupta.",
                checked = settings.enableHardwareDecoding,
            ) { enabled ->
                scope.launch { appState.mutateSettings { copy(enableHardwareDecoding = enabled) } }
            }
        }

        Section("Control parental") {
            ToggleRow(
                title = "Bloquear categorías de adultos",
                subtitle = "Pide un PIN para abrir categorías con nombres como «adult», «18+» o «xxx»",
                checked = settings.isParentalLockEnabled,
            ) { enabled ->
                scope.launch { appState.mutateSettings { copy(isParentalLockEnabled = enabled) } }
            }

            var pin by remember(settings.parentalPin) { mutableStateOf(settings.parentalPin.orEmpty()) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                    label = { Text("PIN (hasta 6 dígitos)") },
                    singleLine = true,
                    enabled = settings.isParentalLockEnabled,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.width(260.dp),
                )
                Button(
                    onClick = { scope.launch { appState.mutateSettings { copy(parentalPin = pin.ifBlank { null }) } } },
                    enabled = settings.isParentalLockEnabled,
                ) { Text("Guardar PIN") }
            }
            if (settings.isParentalLockEnabled && settings.parentalPin.isNullOrBlank()) {
                Text(
                    "Sin PIN guardado el bloqueo no se puede abrir. Guarda uno.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Section("Servidor web") {
            val clipboard = LocalClipboardManager.current
            ToggleRow(
                title = "Activar servidor web",
                subtitle = "Permite ver y cambiar de canal desde el navegador de cualquier dispositivo " +
                    "de tu red (o de internet, exponiendo el puerto con algo como ngrok).",
                checked = settings.enableWebServer,
            ) { enabled ->
                scope.launch {
                    if (enabled && settings.webServerToken.isNullOrBlank()) {
                        appState.mutateSettings {
                            copy(enableWebServer = true, webServerToken = RemoteAuth.generateToken())
                        }
                    } else {
                        appState.mutateSettings { copy(enableWebServer = enabled) }
                    }
                }
            }

            var portText by remember(settings.webServerPort) { mutableStateOf(settings.webServerPort.toString()) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) portText = it },
                    label = { Text("Puerto") },
                    singleLine = true,
                    modifier = Modifier.width(140.dp),
                )
                Button(onClick = {
                    val port = portText.toIntOrNull()?.coerceIn(1024, 65535) ?: settings.webServerPort
                    scope.launch { appState.mutateSettings { copy(webServerPort = port) } }
                }) { Text("Guardar puerto") }
            }

            if (settings.enableWebServer && !settings.webServerToken.isNullOrBlank()) {
                Text(
                    "Token de acceso (pídelo en el navegador remoto la primera vez):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(settings.webServerToken.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    TextButton({ clipboard.setText(AnnotatedString(settings.webServerToken.orEmpty())) }) { Text("Copiar") }
                    TextButton({
                        scope.launch { appState.mutateSettings { copy(webServerToken = RemoteAuth.generateToken()) } }
                    }) { Text("Regenerar") }
                }

                Text(
                    "Enlaces con el token ya incluido — ábrelos directamente en el móvil " +
                        "(por WhatsApp, notas, etc.) y entras sin teclear nada:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                localLanAddresses().forEach { ip ->
                    val link = "http://$ip:${settings.webServerPort}/?token=${settings.webServerToken}"
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(link, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton({ clipboard.setText(AnnotatedString(link)) }) { Text("Copiar enlace") }
                    }
                }
                Text(
                    "Para verlo fuera de casa, lanza ngrok apuntando a este puerto (ngrok http ${settings.webServerPort}) " +
                        "y añade \"/?token=${settings.webServerToken}\" a la URL que te dé.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Section("Información") {
            InfoRow("Motor de vídeo", if (VlcNative.isAvailable) "libvlc detectado" else "no encontrado")
            InfoRow("Datos guardados en", "${System.getProperty("user.home")}/.iptv-family")
            InfoRow("Versión", "1.0.0")
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun localLanAddresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .map { it.hostAddress }
        .toList()
}.getOrDefault(emptyList())

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(160.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(2.dp))
}
