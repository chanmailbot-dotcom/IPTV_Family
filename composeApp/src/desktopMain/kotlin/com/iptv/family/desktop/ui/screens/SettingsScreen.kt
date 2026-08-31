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
import com.iptv.family.shared.domain.ParentalControl
import com.iptv.family.shared.i18n.T

@Composable
fun SettingsScreen(appState: AppState, scope: CoroutineScope) {
    val settings = appState.settings

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(T.ajustes, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Section(T.aspecto) {
            Text(T.tema, style = MaterialTheme.typography.bodyLarge)
            Text(
                T.temaAyuda,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val themeChoices = listOf(
                ThemeType.SYSTEM to T.temaSistema,
                ThemeType.LIGHT to T.temaClaro,
                ThemeType.DARK to T.temaOscuro,
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

        Section(T.reproduccion) {
            var buffer by remember(settings.bufferMs) { mutableStateOf(settings.bufferMs) }
            Text(
                T.bufferDeRed(buffer / 1000),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                T.bufferAyuda,
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
                title = T.modoCompatibilidad,
                subtitle = T.modoCompatibilidadAyuda,
                checked = settings.videoCompatibilityMode,
            ) { enabled ->
                scope.launch { appState.mutateSettings { copy(videoCompatibilityMode = enabled) } }
            }

            ToggleRow(
                title = T.aceleracionHardware,
                subtitle = T.aceleracionAyuda,
                checked = settings.enableHardwareDecoding,
            ) { enabled ->
                scope.launch { appState.mutateSettings { copy(enableHardwareDecoding = enabled) } }
            }
        }

        Section(T.controlParental) {
            ToggleRow(
                title = T.bloquearAdultos,
                subtitle = T.bloquearAdultosAyuda,
                checked = settings.isParentalLockEnabled,
            ) { enabled ->
                scope.launch { appState.mutateSettings { copy(isParentalLockEnabled = enabled) } }
            }

            // El campo arranca VACIO: el PIN ya no se guarda en claro, asi que
            // precargarlo mostraria el hash. Se escribe uno nuevo o no se toca.
            var pin by remember { mutableStateOf("") }
            val pinValido = pin.length in ParentalControl.MIN_PIN_LENGTH..ParentalControl.MAX_PIN_LENGTH
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= ParentalControl.MAX_PIN_LENGTH && it.all(Char::isDigit)) pin = it },
                    label = {
                        Text(
                            if (settings.parentalPin.isNullOrBlank()) T.pinNuevo(ParentalControl.MIN_PIN_LENGTH, ParentalControl.MAX_PIN_LENGTH)
                            else T.cambiarPin
                        )
                    },
                    singleLine = true,
                    enabled = settings.isParentalLockEnabled,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.width(300.dp),
                )
                Button(
                    onClick = {
                        scope.launch {
                            appState.mutateSettings { copy(parentalPin = ParentalControl.hashPin(pin)) }
                        }
                        pin = ""
                    },
                    enabled = settings.isParentalLockEnabled && pinValido,
                ) { Text(T.guardarPin) }
                if (!settings.parentalPin.isNullOrBlank()) {
                    TextButton(
                        onClick = { scope.launch { appState.mutateSettings { copy(parentalPin = null) } } },
                    ) { Text(T.quitar) }
                }
            }
            if (settings.isParentalLockEnabled && settings.parentalPin.isNullOrBlank()) {
                Text(
                    T.sinPinGuardado,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Section(T.servidorWeb) {
            ToggleRow(
                title = T.activarServidorWeb,
                subtitle = T.servidorWebAyuda,
                checked = settings.enableWebServer,
            ) { enabled ->
                scope.launch { appState.mutateSettings { copy(enableWebServer = enabled) } }
            }

            var portText by remember(settings.webServerPort) { mutableStateOf(settings.webServerPort.toString()) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) portText = it },
                    label = { Text(T.puerto) },
                    singleLine = true,
                    modifier = Modifier.width(140.dp),
                )
                Button(onClick = {
                    val port = portText.toIntOrNull()?.coerceIn(1024, 65535) ?: settings.webServerPort
                    scope.launch { appState.mutateSettings { copy(webServerPort = port) } }
                }) { Text(T.guardarPuerto) }
            }

            if (settings.enableWebServer) {
                WebUsersBlock(appState, scope)

                ToggleRow(
                    title = T.arreglarAudioNavegador,
                    subtitle = T.audioNavegadorAyuda,
                    checked = settings.transcodeAudioForWeb,
                ) { enabled ->
                    scope.launch { appState.mutateSettings { copy(transcodeAudioForWeb = enabled) } }
                }
                if (settings.transcodeAudioForWeb) {
                    val ffmpegFound = remember { AudioTranscoder.resolveFfmpeg(settings.ffmpegPath) }
                    Text(
                        if (ffmpegFound != null) T.ffmpegEncontrado(ffmpegFound)
                        else T.ffmpegNoEncontrado,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ffmpegFound != null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error,
                    )
                    var ffmpegText by remember(settings.ffmpegPath) { mutableStateOf(settings.ffmpegPath.orEmpty()) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = ffmpegText,
                            onValueChange = { ffmpegText = it },
                            label = { Text(T.rutaFfmpeg) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = {
                            scope.launch {
                                appState.mutateSettings { copy(ffmpegPath = ffmpegText.trim().ifBlank { null }) }
                            }
                        }) { Text(T.guardar) }
                    }
                }

                val lanIps = remember { localLanAddresses() }
                Text(
                    T.direccionesWeb,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                lanIps.forEach { ip ->
                    Text("http://$ip:${settings.webServerPort}", style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    T.paraVerloFuera(settings.webServerPort),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Section(T.informacion) {
            InfoRow(T.motorDeVideo, if (VlcNative.isAvailable) T.libvlcDetectado else T.noEncontrado)
            InfoRow(T.datosGuardadosEn, "${System.getProperty("user.home")}/.iptv-family")
            InfoRow(T.version, "1.0.0")
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
        Text(T.usuariosDeLaWeb, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

        if (users.isEmpty()) {
            Text(
                T.sinCuentasWeb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        users.forEach { user ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(user.username, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (user.role == WebRole.ADMIN) T.rolAdministrador
                        else T.rolInvitado,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton({ changingFor = user.username; changePass = "" }) { Text(T.contrasena) }
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
                        message = T.cuentaEliminada(user.username)
                    },
                ) { Text(T.borrar) }
            }

            if (changingFor == user.username) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = changePass,
                        onValueChange = { changePass = it },
                        label = { Text(T.contrasenaNueva) },
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
                            message = T.contrasenaCambiada(user.username)
                        },
                    ) { Text(T.cambiar) }
                    TextButton({ changingFor = null }) { Text(T.cancelar) }
                }
            }
        }

        Text(T.anadirCuenta, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(T.usuario) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = newPass,
                onValueChange = { newPass = it },
                label = { Text(T.contrasena) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = newIsAdmin, onCheckedChange = { newIsAdmin = it })
            Text(
                if (newIsAdmin) T.rolAdministradorCorto else T.rolInvitadoCorto,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = newName.trim().length >= 3 && newPass.length >= MIN_PASSWORD_LEN,
                onClick = {
                    val name = newName.trim()
                    if (users.any { it.username.equals(name, ignoreCase = true) }) {
                        message = T.nombreYaExiste
                        return@Button
                    }
                    val user = PasswordHasher.createUser(
                        name, newPass, if (newIsAdmin) WebRole.ADMIN else WebRole.VIEWER,
                    )
                    scope.launch { appState.mutateSettings { copy(webUsers = webUsers + user) } }
                    newName = ""; newPass = ""; newIsAdmin = false
                    message = T.cuentaCreada(name)
                },
            ) { Text(T.crear) }
        }
        Text(
            T.contrasenaMinima(MIN_PASSWORD_LEN),
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
