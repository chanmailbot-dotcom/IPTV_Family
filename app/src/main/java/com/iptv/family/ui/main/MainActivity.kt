package com.iptv.family.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.iptv.family.ui.channels.ChannelListScreen
import com.iptv.family.ui.player.PlayerScreen
import com.iptv.family.ui.theme.Theme
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IPTVFamilyApp()
        }
    }
}

@Composable
fun IPTVFamilyApp() {
    val navController = rememberNavController()

    Theme {
        Scaffold(
            bottomBar = {
                BottomBar(navController = navController)
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AppNavHost(navController = navController)
            }
        }
    }
}

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "channels"
    ) {
        composable("channels") {
            ChannelListScreen(
                onChannelClick = { channelId ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("channelId", channelId)
                    navController.navigate("player")
                },
                onAddPlaylist = {
                    navController.navigate("add_playlist")
                }
            )
        }
        composable("player") {
            val channelId = navController.previousBackStackEntry
                ?.savedStateHandle?.get<String>("channelId")
            PlayerScreen(
                channelId = channelId ?: "",
                onBack = { navController.popBackStack() }
            )
        }
        composable("search") {
            Text(
                text = "Buscar",
                modifier = Modifier.fillMaxSize(),
                style = MaterialTheme.typography.headlineMedium
            )
        }
        composable("favorites") {
            Text(
                text = "Favoritos",
                modifier = Modifier.fillMaxSize(),
                style = MaterialTheme.typography.headlineMedium
            )
        }
        composable("settings") {
            Text(
                text = "Ajustes",
                modifier = Modifier.fillMaxSize(),
                style = MaterialTheme.typography.headlineMedium
            )
        }
        composable("add_playlist") {
            Text(
                text = "Agregar Playlist",
                modifier = Modifier.fillMaxSize(),
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}