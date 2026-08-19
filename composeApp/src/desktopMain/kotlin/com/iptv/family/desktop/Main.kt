package com.iptv.family.desktop

import com.iptv.family.shared.ui.theme.IPTVFamilyTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.window.singleWindowApplication

@OptIn(ExperimentalMaterial3Api::class)
fun main() = singleWindowApplication(
    title = "IPTV Family",
    width = 1280,
    height = 720
) {
    IPTVFamilyTheme {
        DesktopApp()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopApp() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.HOME) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Top App Bar
        TopAppBar(
            title = { Text("IPTV Family", fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = { currentScreen = Screen.SEARCH }) {
                    Icon(Icons.Default.Search, contentDescription = "Buscar")
                }
                IconButton(onClick = { currentScreen = Screen.SETTINGS }) {
                    Icon(Icons.Default.Settings, contentDescription = "Configuración")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF121212),
                titleContentColor = Color.White
            )
        )
        
        // Content
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (currentScreen) {
                Screen.HOME -> HomeScreen(
                    onAddPlaylist = { currentScreen = Screen.ADD_PLAYLIST },
                    onPlayChannel = { currentScreen = Screen.PLAYER }
                )
                Screen.ADD_PLAYLIST -> AddPlaylistScreen(onBack = { currentScreen = Screen.HOME })
                Screen.PLAYER -> PlayerScreen(onBack = { currentScreen = Screen.HOME })
                Screen.SEARCH -> SearchScreen(onBack = { currentScreen = Screen.HOME })
                Screen.SETTINGS -> SettingsScreen(onBack = { currentScreen = Screen.HOME })
            }
        }
    }
}

enum class Screen {
    HOME, ADD_PLAYLIST, PLAYER, SEARCH, SETTINGS
}

@Composable
fun HomeScreen(
    onAddPlaylist: () -> Unit,
    onPlayChannel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Bienvenido a IPTV Family", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Selecciona una lista de reproducción para comenzar", fontSize = 16.sp, color = Color.Gray)
        
        Card(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF1E88E5), modifier = androidx.compose.ui.Modifier.size(48.dp))
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                    Text("Agregar lista M3U / Xtream Codes", fontSize = 18.sp, color = Color.White)
                    Text("URL M3U, archivo local o panel Xtream", fontSize = 14.sp, color = Color.Gray)
                }
            }
        }
        
        Button(
            onClick = onAddPlaylist,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E88E5),
                contentColor = Color.White
            )
        ) {
            Text("Agregar Playlist", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AddPlaylistScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        androidx.compose.material3.TopAppBar(
            title = { Text("Agregar Playlist") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Star, contentDescription = "Atrás") } }
        )
        
        Text("Próximamente: Formulario para agregar M3U URL, archivo local o Xtream Codes", 
             fontSize = 16.sp, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
             modifier = Modifier.fillMaxSize().padding(24.dp))
    }
}

@Composable
fun PlayerScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        androidx.compose.material3.TopAppBar(
            title = { Text("Reproductor") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Star, contentDescription = "Atrás") } }
        )
        
        Card(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF1E88E5), modifier = androidx.compose.ui.Modifier.size(80.dp))
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))
                    Text("Reproductor de Video", fontSize = 24.sp, color = Color.White)
                    Text("Integración con MediaPlayer nativo / VLC", fontSize = 16.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun SearchScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        androidx.compose.material3.TopAppBar(
            title = { Text("Buscar") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Star, contentDescription = "Atrás") } }
        )
        
        Text("Búsqueda de canales", fontSize = 24.sp, color = Color.White)
        Text("Próximamente: Búsqueda en tiempo real con filtrado por categorías", 
             fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(24.dp))
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        androidx.compose.material3.TopAppBar(
            title = { Text("Configuración") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Star, contentDescription = "Atrás") } }
        )
        
        Text("Configuración", fontSize = 24.sp, color = Color.White)
        Text("Próximamente: Tema, idioma, buffer, control parental, etc.", 
             fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(24.dp))
    }
}