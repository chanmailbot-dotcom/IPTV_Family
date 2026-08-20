package com.iptv.family.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.iptv.family.desktop.player.ExternalPlayer
import com.iptv.family.desktop.state.AppState
import com.iptv.family.shared.model.Category
import com.iptv.family.shared.model.Channel

@Composable
fun ChannelsScreen(
    appState: AppState,
    scope: kotlinx.coroutines.CoroutineScope,
    onChannelClick: (Channel) -> Unit,
) {
    val selected = rememberSaveable { mutableStateOf("all") }
    var search by remember { mutableStateOf("") }
    val unlocked = remember { mutableStateOf(mutableSetOf<String>()) }
    val pendingPin = remember { mutableStateOf<Category?>(null) }

    fun isAdult(name: String): Boolean =
        appState.settings.isParentalLockEnabled &&
            appState.settings.adultCategoryNames.any { name.contains(it, ignoreCase = true) }

    val playlist = appState.selectedPlaylist
    if (playlist == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Selecciona o añade una lista en 'Inicio'.")
        }
        return
    }

    val list = appState.channelsFor(selected.value).filter {
        search.isEmpty() || it.name.contains(search, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        OutlinedTextField(
            value = search, onValueChange = { search = it },
            label = { Text("Buscar canal") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
        )
        Text("${list.size} canales", modifier = Modifier.padding(vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(0.6f))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            appState.categories.forEach { cat ->
                val adult = isAdult(cat.name)
                val locked = adult && !unlocked.value.contains(cat.id)
                AssistChip(
                    onClick = {
                        if (locked) pendingPin.value = cat
                        else {
                            selected.value = cat.id
                            if (adult) unlocked.value = unlocked.value + cat.id
                        }
                    },
                    label = { Text(cat.name) },
                    colors = if (selected.value == cat.id) AssistChipDefaults.elevatedChipColors()
                    else AssistChipDefaults.assistChipColors(),
                    modifier = Modifier.padding(vertical = 4.dp).height(32.dp),
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(list, key = { it.id }) { ch ->
                ChannelRow(ch, onChannelClick, scope, appState)
            }
        }
    }

    val cat = pendingPin.value
    if (cat != null) {
        var pin by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingPin.value = null },
            title = { Text("Control parental") },
            text = { OutlinedTextField(pin, { if (it.length <= 6) pin = it }, label = { Text("PIN") }, visualTransformation = PasswordVisualTransformation()) },
            confirmButton = {
                TextButton(onClick = {
                    if (pin == appState.settings.parentalPin) {
                        unlocked.value = unlocked.value + cat.id
                        selected.value = cat.id
                    }
                    pendingPin.value = null
                }) { Text("Ok") }
            },
        )
    }
}
