package com.iptv.family.ui.main

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ParentControl
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.rxp22
import androidx.hilt.navigation.compose.hiltViewModel
import com.iptv.family.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    // Settings state - in real app this would come from DataStore/SharedPreferences
    var parentalControlEnabled by remember { mutableStateOf(false) }
    var parentalPin by remember { mutableStateOf("") }
    var autoPlayNext by remember { mutableStateOf(true) }
    var rememberPosition by remember { mutableStateOf(true) }
    var bufferSize by remember { mutableStateOf("large") }
    var preferredLanguage by remember { mutableStateOf("es") }
    var themeMode by remember { mutableStateOf("system") }
    var syncEnabled by remember { mutableStateOf(false) }
    var syncAccount by remember { mutableStateOf("") }

    val sections = remember {
        listOf(
            SettingsSection(
                title = "General",
                items = listOf(
                    SettingItem(
                        title = "Idioma",
                        subtitle = "Español",
                        icon = Icons.Default.Language,
                        action = { /* show language picker */ }
                    ),
                    SettingItem(
                        title = "Tema",
                        subtitle = "Sistema (Oscuro)",
                        icon = Icons.Default.Settings,
                        action = { /* show theme picker */ }
                    ),
                    SettingItem(
                        title = "Reproducción automática",
                        subtitle = "Reproducir siguiente canal al terminar",
                        icon = Icons.Default.VolumeUp,
                        trailing = { Switch(checked = autoPlayNext, onCheckedChange = { autoPlayNext = it }) }
                    ),
                    SettingItem(
                        title = "Recordar posición",
                        subtitle = "Continuar desde donde te quedaste",
                        icon = Icons.Default.Restore,
                        trailing = { Switch(checked = rememberPosition, onCheckedChange = { rememberPosition = it }) }
                    ),
                    SettingItem(
                        title = "Tamaño de buffer",
                        subtitle = "Grande (recomendado para directo)",
                        icon = Icons.Default.Folder,
                        action = { /* show buffer size picker */ }
                    ),
                )
            ),
            SettingsSection(
                title = "Reproductor",
                items = listOf(
                    SettingItem(
                        title = "Controles parentales",
                        subtitle = "Proteger contenido adulto con PIN",
                        icon = Icons.Default.ParentControl,
                        trailing = { Switch(checked = parentalControlEnabled, onCheckedChange = { parentalControlEnabled = it }) }
                    ),
                    SettingItem(
                        title = "Cambiar PIN parental",
                        subtitle = parentalPin.ifBlank { "No configurado" },
                        icon = Icons.Default.Lock,
                        action = { /* show PIN dialog */ }
                    ),
                )
            ),
            SettingsSection(
                title = "Sincronización",
                items = listOf(
                    SettingItem(
                        title = "Sincronizar ajustes",
                        subtitle = "Guardar favoritos, listas y posición en la nube",
                        icon = Icons.Default.CloudSync,
                        trailing = { Switch(checked = syncEnabled, onCheckedChange = { syncEnabled = it }) }
                    ),
                    SettingItem(
                        title = "Cuenta de sincronización",
                        subtitle = syncAccount.ifBlank { "No vinculada" },
                        icon = Icons.Default.Person,
                        action = { /* show account picker */ }
                    ),
                )
            ),
            SettingsSection(
                title = "Datos y privacidad",
                items = listOf(
                    SettingItem(
                        title = "Borrar caché de listas",
                        subtitle = "Liberar espacio eliminando datos temporales",
                        icon = Icons.Default.Delete,
                        isDestructive = true,
                        action = { /* clear cache */ }
                    ),
                    SettingItem(
                        title = "Restablecer ajustes",
                        subtitle = "Volver a valores por defecto",
                        icon = Icons.Default.Restore,
                        isDestructive = true,
                        action = { /* reset settings */ }
                    ),
                )
            ),
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        TopAppBarSmall(
            title = { Text("Configuración") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                }
            },
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(sections, key = { it.title }) { section ->
                SettingsSectionCard(section = section)
            }
        }
    }
}

@Composable
fun SettingsSectionCard(section: SettingsSection) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp)),
        ) {
            section.items.forEachIndexed { index, item ->
                SettingsItem(item = item, isLast = index == section.items.lastIndex)
            }
        }
    }
}

@Composable
fun SettingsItem(
    item: SettingItem,
    isLast: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(if (!isLast) Modifier.background(Color.Transparent, RoundedCornerShape(0.dp)) else Modifier)
            .clickable(enabled = item.action != null, onClick = { item.action?.invoke() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = if (item.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp).padding(end = 16.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.trailing?.invoke()
        if (item.action != null && item.trailing == null) {
            androidx.compose.material.Icon(
                androidx.compose.material.icons.filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

data class SettingsSection(
    val title: String,
    val items: List<SettingItem>,
)

data class SettingItem(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val action: (() -> Unit)? = null,
    val trailing: (@Composable () -> Unit)? = null,
    val isDestructive: Boolean = false,
)