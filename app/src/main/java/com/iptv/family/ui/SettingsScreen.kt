package com.iptv.family.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iptv.family.state.AppState
import com.iptv.family.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.iptv.family.shared.domain.ParentalControl
import com.iptv.family.shared.i18n.T
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog

@Composable
fun SettingsScreen(appState: AppState, scope: CoroutineScope) {
    val settings = appState.settings
    var showPinDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(T.ajustes, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.large).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(T.bloquearAdultos, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        T.bloquearAdultosAyuda,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.isParentalLockEnabled,
                    onCheckedChange = { enabled -> scope.launch { appState.mutateSettings { copy(isParentalLockEnabled = enabled) } } },
                )
            }

            // El PIN. Sin esto el interruptor no protegia nada: no habia forma de
            // definirlo, y sin PIN la comprobacion nunca puede dar bien, asi que
            // las categorias quedaban tapiadas en vez de protegidas.
            if (settings.isParentalLockEnabled) {
                val tienePin = !settings.parentalPin.isNullOrBlank()

                if (!tienePin) {
                    Text(
                        T.faltaDefinirPin,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { showPinDialog = true }) {
                        Text(if (tienePin) "Cambiar PIN" else "Definir PIN")
                    }
                    if (tienePin) {
                        TextButton(onClick = {
                            scope.launch { appState.mutateSettings { copy(parentalPin = null) } }
                        }) { Text(T.quitarPin) }
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.large).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                T.informacion,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                T.versionNumero(BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showPinDialog) {
        SetPinDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = { pin ->
                scope.launch { appState.mutateSettings { copy(parentalPin = ParentalControl.hashPin(pin)) } }
                showPinDialog = false
            },
        )
    }
}

/**
 * Alta o cambio del PIN, con confirmacion.
 *
 * Se pide dos veces a proposito: en una tele se teclea con las flechas del
 * mando y equivocarse es facil. Un PIN mal escrito y confirmado a ciegas deja
 * las categorias inaccesibles hasta quitar el control desde los ajustes.
 */
@Composable
private fun SetPinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var repe by remember { mutableStateOf("") }

    val soloDigitos = pin.all { it.isDigit() }
    val largoOk = pin.length in ParentalControl.MIN_PIN_LENGTH..ParentalControl.MAX_PIN_LENGTH
    val coincide = pin.isNotEmpty() && pin == repe
    val error = when {
        pin.isEmpty() -> null
        !soloDigitos -> T.pinSoloNumeros
        !largoOk -> "Entre ${ParentalControl.MIN_PIN_LENGTH} y ${ParentalControl.MAX_PIN_LENGTH} digitos."
        repe.isNotEmpty() && !coincide -> T.pinNoCoincide
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T.pinDelControlParental) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= ParentalControl.MAX_PIN_LENGTH) pin = it },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                OutlinedTextField(
                    value = repe,
                    onValueChange = { if (it.length <= ParentalControl.MAX_PIN_LENGTH) repe = it },
                    label = { Text(T.repiteElPin) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pin) },
                enabled = soloDigitos && largoOk && coincide,
            ) { Text(T.guardar) }
        },
        dismissButton = { TextButton(onDismiss) { Text(T.cancelar) } },
    )
}
