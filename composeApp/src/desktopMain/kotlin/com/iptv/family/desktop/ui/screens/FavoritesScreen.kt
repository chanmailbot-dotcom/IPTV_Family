package com.iptv.family.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.iptv.family.desktop.state.AppState
import com.iptv.family.shared.model.Channel

@Composable
fun FavoritesScreen(appState: AppState, onChannelClick: (Channel) -> Unit) {
    val fav = appState.channels.filter { it.isFavorite }
    val scope = rememberCoroutineScope()
    if (fav.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("Sin canales favoritos. Marca estrellas en la lista de canales.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(0.6f))
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(fav, key = { it.id }) { ch ->
            ChannelRow(ch, onChannelClick, scope, appState)
        }
    }
}
