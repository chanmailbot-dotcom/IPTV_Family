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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.iptv.family.desktop.remote.AudioTranscoder
import com.iptv.family.shared.data.auth.PasswordHasher
import com.iptv.family.shared.model.WebRole
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
            Text("Tema", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Claro, oscuro, o sigue la configuración del sistema.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val themeChoices = listOf(
                ThemeType.SYSTEM to "Sistema",
                ThemeType.LIGHT to "Claro",
                ThemeType.DARK to "Oscuro",
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                themeChoices.forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = settings.selectedTheme == type,
                        onClick = { scope.launch { appState.mutateSettings { copy(selectedTheme = type) } } },
                        shape = SegmentedButtonDefaults.itemShape(index, themeChoices.size),
                        label = { Text(label) },
                    )
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
            ToggleRow(
                title = "Activar servidor web",
                subtitle = "Permite ver y cambiar de canal desde el navegador de cualquier dispositivo " +
                    "de tu red (o de internet, exponiendo el puerto con algo como ngrok).",
                checked = settings.enableWebServer,
            ) { enabled ->
                scope.launch { appState.mutateSettings { copy(enableWebServer = enabled) } }
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

            if (settings.enableWebServer) {
                WebUsersBlock(appState, scope)

                ToggleRow(
                    title = "Convertir el audio para el navegador",
                    subtitle = "Muchos canales emiten el audio en AC-3 o MP2, que ningun navegador " +
                        "puede reproducir (en esta app si se oye). Con esto, ffmpeg lo convierte a AAC " +
                        "al vuelo. Solo recodifica el audio: el video se copia tal cual.",
                    checked = settings.transcodeAudioForWeb,
                ) { enabled ->
                    scope.launch { appState.mutateSettings { copy(transcodeAudioForWeb = enabled) } }
                }
                if (settings.transcodeAudioForWeb) {
                    val ffmpegFound = remember { AudioTranscoder.resolveFfmpeg(settings.ffmpegPath) }
                    Text(
                        if (ffmpegFound != null) "ffmpeg encontrado: $ffmpegFound"
                        else "No se encuentra ffmpeg. Instalalo (winget install Gyan.FFmpeg) o indica su ruta abajo; " +
                            "sin el, esos canales seguiran sin sonido en la web.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ffmpegFound != null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error,
                    )
                    var ffmpegText by remember(settings.ffmpegPath) { mutableStateOf(settings.ffmpegPath.orEmpty()) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = ffmpegText,
                            onValueChange = { ffmpegText = it },
                            label = { Text("Ruta a ffmpeg (opcional)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = {
                            scope.launch {
                                appState.mutateSettings { copy(ffmpegPath = ffmpegText.trim().ifBlank { null }) }
                            }
                        }) { Text("Guardar") }
                    }
                }

                val lanIps = remember { localLanAddresses() }
                Text(
                    "Direcciones para abrir la web (cada persona entra con su usuario y contrasena):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                lanIps.forEach { ip ->
                    Text("http://$ip:${settings.webServerPort}", style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    "Para verlo fuera de casa: ngrok http ${settings.webServerPort}",
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
/**
 * Gestion de las cuentas de acceso a la web. El administrador crea usuarios,
 * les cambia la contrasena o los borra; los invitados solo pueden ver el canal
 * que el administrador haya puesto.
 */
@Composable
private fun WebUsersBlock(appState: AppState, scope: CoroutineScope) {
    val users = appState.settings.webUsers
    var newName by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var newIsAdmin by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var changingFor by remember { mutableStateOf<String?>(null) }
    var changePass by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Usuarios de la web", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

        if (users.isEmpty()) {
            Text(
                "Todavia no hay ninguna cuenta. Crea la primera (sera administradora) aqui o " +
                    "desde la propia web, que al abrirla sin cuentas pide crearla.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        users.forEach { user ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(user.username, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (user.role == WebRole.ADMIN) "Administrador — control total"
                        else "Invitado — solo ve el canal que pongas tu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton({ changingFor = user.username; changePass = "" }) { Text("Contrasena") }
                // No se deja borrar al ultimo administrador: nadie podria volver a
                // gestionar usuarios sin editar el fichero de ajustes a mano.
                val isLastAdmin = user.role == WebRole.ADMIN && users.count { it.role == WebRole.ADMIN } <= 1
                TextButton(
                    enabled = !isLastAdmin,
                    onClick = {
                        scope.launch {
                            appState.mutateSettings { copy(webUsers = webUsers.filterNot { it.username == user.username }) }
                        }
                        RemoteAuth.revokeSessionsOf(user.username)
                        message = "Cuenta '${user.username}' eliminada."
                    },
                ) { Text("Borrar") }
            }

            if (changingFor == user.username) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = changePass,
                        onValueChange = { changePass = it },
                        label = { Text("Contrasena nueva") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = changePass.length >= MIN_PASSWORD_LEN,
                        onClick = {
                            val updated = PasswordHasher.withNewPassword(user, changePass)
                            scope.launch {
                                appState.mutateSettings {
                                    copy(webUsers = webUsers.map { if (it.username == user.username) updated else it })
                                }
                            }
                            // Cambiar la contrasena echa a quien siguiera con la vieja.
                            RemoteAuth.revokeSessionsOf(user.username)
                            changingFor = null
                            message = "Contrasena de '${user.username}' cambiada."
                        },
                    ) { Text("Cambiar") }
                    TextButton({ changingFor = null }) { Text("Cancelar") }
                }
            }
        }

        Text("Anadir cuenta", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Usuario") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = newPass,
                onValueChange = { newPass = it },
                label = { Text("Contrasena") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = newIsAdmin, onCheckedChange = { newIsAdmin = it })
            Text(
                if (newIsAdmin) "Administrador" else "Invitado (solo ver)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = newName.trim().length >= 3 && newPass.length >= MIN_PASSWORD_LEN,
                onClick = {
                    val name = newName.trim()
                    if (users.any { it.username.equals(name, ignoreCase = true) }) {
                        message = "Ya existe una cuenta con ese nombre."
                        return@Button
                    }
                    val user = PasswordHasher.createUser(
                        name, newPass, if (newIsAdmin) WebRole.ADMIN else WebRole.VIEWER,
                    )
                    scope.launch { appState.mutateSettings { copy(webUsers = webUsers + user) } }
                    newName = ""; newPass = ""; newIsAdmin = false
                    message = "Cuenta '$name' creada."
                },
            ) { Text("Crear") }
        }
        Text(
            "La contrasena debe tener al menos $MIN_PASSWORD_LEN caracteres. No se guarda tal cual: " +
                "se guarda su hash, asi que no se puede recuperar (solo cambiar).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private const val MIN_PASSWORD_LEN = 6

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
