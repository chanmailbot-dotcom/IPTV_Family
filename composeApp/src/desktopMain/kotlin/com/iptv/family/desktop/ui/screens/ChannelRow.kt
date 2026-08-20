package com.iptv.family.desktop.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.found.Favorite
import androidx.compose.material.icons.found.FavoriteBorder
import androidx.compose.material.icons.found.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.iptv.family.desktop.ui.rememberRemoteImageBitmap
import com.iptv.family.desktop.state.AppState
import com.iptv.family.shared.model.Channel
import kotlinx.coroutines.launch

@Composable
fun ChannelRow(
    channel: Channel,
    onClick: (Channel) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    appState: AppState,
) {
    ListItem(
        headlineText = { Text(channel.name) },
        supportingText = { Text(channel.group ?: "Sin grupo", style = MaterialTheme.typography.bodySmall) },
        leadingContent = {
            Box(Modifier.size(40.dp)) {
                val bmp = rememberRemoteImageBitmap(channel.logoUrl)
                if (bmp != null) {
                    Image(bitmap = bmp, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(0.2f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        trailingContent = {
            IconButton({ scope.launch { appState.toggleFavorite(channel.id) } }) {
                Icon(
                    if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (channel.isFavorite) "Favorito" else "Añadir a favoritos",
                    tint = if (channel.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier.clickable { onClick(channel) },
    )
}
