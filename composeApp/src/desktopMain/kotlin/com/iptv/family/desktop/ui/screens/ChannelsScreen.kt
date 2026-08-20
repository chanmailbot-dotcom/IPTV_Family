package com.iptv.family.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.iptv.family.desktop.state.AppState
import com.iptv.family.shared.model.Category
import com.iptv.family.shared.model.CategoryType
import com.iptv.family.shared.model.Channel
import kotlinx.coroutines.CoroutineScope

private enum class TypeFilter(val label: String, val type: CategoryType?) {
    ALL("Todo", null),
    LIVE("TV en directo", CategoryType.LIVE),
    VOD("Películas", CategoryType.VOD),
    SERIES("Series", CategoryType.SERIES),
}

@Composable
fun ChannelsScreen(
    appState: AppState,
    scope: CoroutineScope,
    onPlay: (Channel, List<Channel>) -> Unit,
    onGoHome: () -> Unit,
) {
    var selectedCategory by remember { mutableStateOf("all") }
    var typeFilter by remember { mutableStateOf(TypeFilter.ALL) }
    var search by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(setOf<String>()) }
    var pendingPin by remember { mutableStateOf<Category?>(null) }

    fun isAdult(name: String): Boolean =
        appState.settings.isParentalLockEnabled &&
            appState.settings.adultCategoryNames.any { name.contains(it, ignoreCase = true) }

    // En M3U el grupo del canal ES el nombre de la categoria, pero en Xtream es un id
    // numerico. Sin traducir id -> nombre, el control parental no filtraria nada en Xtream.
    val categoryNames = remember(appState.categories) {
        appState.categories.associate { it.id to it.name }
    }

    fun isAdultGroup(groupId: String?): Boolean {
        val id = groupId ?: return false
        return isAdult(categoryNames[id] ?: id)
    }

    if (appState.selectedPlaylist == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("No hay ninguna lista abierta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Ve a «Mis listas» y añade o selecciona una.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onGoHome) { Text("Ir a mis listas") }
            }
        }
        return
    }

    appState.error?.let { message ->
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("No se pudo cargar la lista", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Button(onGoHome) { Text("Volver a mis listas") }
            }
        }
        return
    }

    if (appState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Cargando canales…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val visibleCategories = appState.categories.filter {
        it.id == "all" || typeFilter.type == null || it.type == typeFilter.type
    }

    // Una categoria que ya no encaja con el filtro deja de ser una seleccion valida.
    if (visibleCategories.none { it.id == selectedCategory }) selectedCategory = "all"

    val channels = appState.channelsFor(selectedCategory)
        .filter { typeFilter.type == null || it.categoryType == typeFilter.type }
        .filter { search.isBlank() || it.name.contains(search.trim(), ignoreCase = true) }
        .filterNot { channel -> isAdultGroup(channel.group) && channel.group !in unlocked }

    Row(Modifier.fillMaxSize()) {
        CategorySidebar(
            categories = visibleCategories,
            selectedId = selectedCategory,
            isLocked = { category -> isAdult(category.name) && category.id !in unlocked },
            onSelect = { category ->
                if (isAdult(category.name) && category.id !in unlocked) pendingPin = category
                else selectedCategory = category.id
            },
        )

        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 16.dp, vertical = 12.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Buscar canal…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton({ search = "" }) { Icon(Icons.Rounded.Clear, contentDescription = "Limpiar búsqueda") }
                    }
                },
            )

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = typeFilter == filter,
                        onClick = { typeFilter = filter },
                        label = { Text(filter.label) },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "${channels.size} canales",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            if (channels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (search.isBlank()) "Esta categoría está vacía." else "Ningún canal coincide con «$search».",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(channels, key = { it.id }) { channel ->
                        ChannelRow(
                            channel = channel,
                            onChannelClick = { onPlay(it, channels) },
                            scope = scope,
                            appState = appState,
                        )
                    }
                }
            }
        }
    }

    pendingPin?.let { category ->
        PinDialog(
            expectedPin = appState.settings.parentalPin,
            onDismiss = { pendingPin = null },
            onUnlocked = {
                unlocked = unlocked + category.id
                selectedCategory = category.id
                pendingPin = null
            },
        )
    }
}

@Composable
private fun CategorySidebar(
    categories: List<Category>,
    selectedId: String,
    isLocked: (Category) -> Boolean,
    onSelect: (Category) -> Unit,
) {
    Column(
        Modifier
            .width(250.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
    ) {
        Text(
            "Categorías",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(categories, key = { it.id }) { category ->
                val selected = category.id == selectedId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            MaterialTheme.shapes.medium,
                        )
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onSelect(category) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        category.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isLocked(category)) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = "Bloqueada por control parental",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    } else if (category.channels.isNotEmpty()) {
                        Text(
                            category.channels.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinDialog(expectedPin: String?, onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Control parental") },
        text = {
            Column {
                Text("Introduce el PIN para ver esta categoría.")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6) { pin = it; wrong = false } },
                    label = { Text("PIN") },
                    singleLine = true,
                    isError = wrong,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (wrong) {
                    Text(
                        "PIN incorrecto.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button({ if (pin == expectedPin && !expectedPin.isNullOrBlank()) onUnlocked() else wrong = true }) {
                Text("Desbloquear")
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
    )
}
