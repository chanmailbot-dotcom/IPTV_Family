package com.iptv.family.ui.main

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MagnifyingGlass
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iptv.family.R

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs: SharedPreferences = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }

    var themeMode by remember { mutableStateOf(prefs.getInt("theme_mode", 0)) }
    var language by remember { mutableStateOf(prefs.getString("language", "es") ?: "es") }
    var bufferSize by remember { mutableStateOf(prefs.getInt("buffer_size", 10)) }
    var enableHardwareDecoding by remember { mutableStateOf(prefs.getBoolean("hw_decoding", true)) }
    var parentalPin by remember { mutableStateOf(prefs.getString("parental_pin", "") ?: "") }
    var parentalEnabled by remember { mutableStateOf(prefs.getBoolean("parental_enabled", false)) }
    var autoSyncEnabled by remember { mutableStateOf(prefs.getBoolean("auto_sync", false)) }
    var syncFrequency by remember { mutableStateOf(prefs.getInt("sync_frequency", 24)) }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = { /* navigate back */ }) {
                        Icon(ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                ),
            )
        },
        containerColor = Color(0xFF121212),
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            // General section
            SettingsSection(
                title = stringResource(R.string.section_general),
                icon = Palette,
            ) {
                SettingsItem(
                    title = stringResource(R.string.theme),
                    subtitle = when (themeMode) {
                        0 -> stringResource(R.string.theme_system)
                        1 -> stringResource(R.string.theme_light)
                        2 -> stringResource(R.string.theme_dark)
                        else -> stringResource(R.string.theme_system)
                    },
                    icon = Palette,
                    onClick = { /* show theme dialog */ }
                )
                SettingsItem(
                    title = stringResource(R.string.language),
                    subtitle = when (language) {
                        "es" -> "Español"
                        "en" -> "English"
                        else -> "Español"
                    },
                    icon = Language,
                    onClick = { /* show language dialog */ }
                )
            }

            // Player section
            SettingsSection(
                title = stringResource(R.string.section_player),
                icon = VolumeUp,
            ) {
                SettingsItem(
                    title = stringResource(R.string.buffer_size),
                    subtitle = "${bufferSize} MB",
                    icon = Folder,
                    onClick = { /* show buffer dialog */ }
                )
                SettingsItem(
                    title = stringResource(R.string.hardware_decoding),
                    subtitle = if (enableHardwareDecoding) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                    icon = Tv,
                    onClick = { enableHardwareDecoding = !enableHardwareDecoding },
                    trailing = {
                        Switch(
                            checked = enableHardwareDecoding,
                            onCheckedChange = { enableHardwareDecoding = it },
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                )
            }

            // Parental section
            SettingsSection(
                title = stringResource(R.string.section_parental),
                icon = Security,
            ) {
                SettingsItem(
                    title = stringResource(R.string.parental_control),
                    subtitle = if (parentalEnabled) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                    icon = Lock,
                    onClick = { parentalEnabled = !parentalEnabled },
                    trailing = {
                        Switch(
                            checked = parentalEnabled,
                            onCheckedChange = { parentalEnabled = it },
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                )
                if (parentalEnabled) {
                    SettingsItem(
                        title = stringResource(R.string.change_pin),
                        subtitle = stringResource(R.string.pin_required),
                        icon = Lock,
                        onClick = { /* show PIN dialog */ }
                    )
                }
            }

            // Sync section
            SettingsSection(
                title = stringResource(R.string.section_sync),
                icon = Cloud,
            ) {
                SettingsItem(
                    title = stringResource(R.string.auto_sync),
                    subtitle = if (autoSyncEnabled) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                    icon = Cloud,
                    onClick = { autoSyncEnabled = !autoSyncEnabled },
                    trailing = {
                        Switch(
                            checked = autoSyncEnabled,
                            onCheckedChange = { autoSyncEnabled = it },
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                )
                if (autoSyncEnabled) {
                    SettingsItem(
                        title = stringResource(R.string.sync_frequency),
                        subtitle = "${syncFrequency}h",
                        icon = Restore,
                        onClick = { /* show frequency dialog */ }
                    )
                }
                SettingsItem(
                    title = stringResource(R.string.sync_now),
                    subtitle = stringResource(R.string.manual_sync),
                    icon = Restore,
                    onClick = { /* trigger sync */ }
                )
            }

            // Data section
            SettingsSection(
                title = stringResource(R.string.section_data),
                icon = Folder,
            ) {
                SettingsItem(
                    title = stringResource(R.string.clear_cache),
                    subtitle = stringResource(R.string.free_space),
                    icon = Folder,
                    onClick = { /* clear cache */ }
                )
                SettingsItem(
                    title = stringResource(R.string.export_settings),
                    subtitle = stringResource(R.string.backup_settings),
                    icon = Cloud,
                    onClick = { /* export settings */ }
                )
                SettingsItem(
                    title = stringResource(R.string.import_settings),
                    subtitle = stringResource(R.string.restore_settings),
                    icon = Restore,
                    onClick = { /* import settings */ }
                )
                SettingsItem(
                    title = stringResource(R.string.reset_app),
                    subtitle = stringResource(R.string.factory_reset),
                    icon = Security,
                    onClick = { /* reset app */ }
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF1E88E5), modifier = Modifier.size(24.dp))
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = Color(0xFF1E88E5),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
        shape = RoundedCornerShape(0.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(16.dp))
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                )
                Text(
                    text = subtitle,
                    color = Color.Gray,
                    fontSize = 14.sp,
                )
            }
            trailing?.invoke() ?: Icon(
                imageVector = ChevronRight,
                contentDescription = null,
                tint = Color.Gray,
            )
        }
    }
}