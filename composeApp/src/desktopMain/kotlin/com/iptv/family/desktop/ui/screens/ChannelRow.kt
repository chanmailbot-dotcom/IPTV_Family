package com.iptv.family.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iptv.family.desktop.state.AppState
import com.iptv.family.desktop.ui.ChannelLogo
import com.iptv.family.shared.model.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ChannelRow(
    channel: Channel,
    onChannelClick: (Channel) -> Unit,
    scope: CoroutineScope,
    appState: AppState,
    isCurrent: Boolean = false,
    compact: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val fav = appState.isFavorite(channel.id)

    val background = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        hovered -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(background, MaterialTheme.shapes.medium)
            .then(
                if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                else Modifier,
            )
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onChannelClick(channel) }
            .padding(horizontal = 10.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Numero de dial del proveedor: cifras tabulares y ancho fijo para que la
        // columna quede alineada entre canales de 1 y de 4 cifras.
        channel.number?.let { number ->
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(if (compact) 26.dp else 34.dp),
            )
        }

        ChannelLogo(channel.logoUrl, channel.name, size = if (compact) 32.dp else 44.dp)

        Column(Modifier.weight(1f)) {
            Text(
                channel.name,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!compact) {
                // epgTick como clave: refresca "Ahora" cuando la guia se recarga o pasa el tiempo.
                val epgTick = appState.epgTick
                val program = remember(channel.id, epgTick) { appState.currentProgram(channel) }
                Text(
                    when {
                        program != null -> "Ahora: ${program.title}"
                        // groupName y no channel.group: este ultimo es el id de
                        // categoria y en Xtream se veia un numero suelto.
                        else -> appState.groupName(channel) ?: "Sin grupo"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (program != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        IconButton(
            onClick = { scope.launch { appState.toggleFavorite(channel.id) } },
            modifier = Modifier.size(if (compact) 30.dp else 36.dp),
        ) {
            Icon(
                if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (fav) "Quitar de favoritos" else "Añadir a favoritos",
                tint = if (fav) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (compact) 18.dp else 20.dp),
            )
        }

        if (!compact) {
            IconButton(onClick = { onChannelClick(channel) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Reproducir ${channel.name}",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
