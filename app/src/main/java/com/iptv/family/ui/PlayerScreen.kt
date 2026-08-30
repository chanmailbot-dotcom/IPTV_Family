package com.iptv.family.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.iptv.family.player.ExoPlayerController
import com.iptv.family.shared.model.Channel
import kotlinx.coroutines.delay

private const val CONTROLS_HIDE_DELAY_MS = 4000L
private const val ZAP_BAR_HIDE_DELAY_MS = 5000L

/**
 * Vecino en la lista de zapeo, DANDO LA VUELTA en los extremos, igual que en
 * escritorio y en la web. Antes se paraba en el primer y ultimo canal.
 */
private fun List<Channel>.neighbourOf(current: Channel, delta: Int): Channel? {
    if (isEmpty()) return null
    val index = indexOfFirst { it.id == current.id }
    if (index < 0) return null
    return this[(index + delta).mod(size)]
}

@Composable
fun PlayerScreen(
    controller: ExoPlayerController,
    channel: Channel,
    zapList: List<Channel> = emptyList(),
    onSelectChannel: (Channel) -> Unit = {},
    onBack: () -> Unit,
) {
    LaunchedEffect(channel.id) { controller.play(channel.url) }

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableStateOf(0L) }
    var zapBarVisible by remember { mutableStateOf(false) }
    var lastZapInteraction by remember { mutableStateOf(0L) }
    val focusRequester = remember { FocusRequester() }

    // Los controles (play, volumen...) se ocultan solos: en TV, tapan el video
    // y nadie los quiere ver fijos todo el rato. Cualquier pulsacion del mando
    // los vuelve a mostrar y reinicia la cuenta atras.
    LaunchedEffect(lastInteraction) {
        controlsVisible = true
        delay(CONTROLS_HIDE_DELAY_MS)
        controlsVisible = false
    }

    LaunchedEffect(lastZapInteraction) {
        if (lastZapInteraction == 0L) return@LaunchedEffect
        delay(ZAP_BAR_HIDE_DELAY_MS)
        zapBarVisible = false
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                lastInteraction = System.currentTimeMillis()
                when (event.key) {
                    // Arriba/abajo: canal anterior/siguiente al instante, sin volver al listado.
                    Key.DirectionUp -> {
                        zapList.neighbourOf(channel, -1)?.let(onSelectChannel)
                        true
                    }
                    Key.DirectionDown -> {
                        zapList.neighbourOf(channel, +1)?.let(onSelectChannel)
                        true
                    }
                    // Izquierda/derecha: lo mismo, pero ademas muestra la tira de canales
                    // para ubicarse (se oculta sola a los 5s sin tocar el mando).
                    Key.DirectionLeft -> {
                        zapList.neighbourOf(channel, -1)?.let(onSelectChannel)
                        zapBarVisible = true
                        lastZapInteraction = System.currentTimeMillis()
                        true
                    }
                    Key.DirectionRight -> {
                        zapList.neighbourOf(channel, +1)?.let(onSelectChannel)
                        zapBarVisible = true
                        lastZapInteraction = System.currentTimeMillis()
                        true
                    }
                    else -> false // deja pasar OK/atras/etc. para que sigan funcionando
                }
            },
    ) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            player = controller.player
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (controller.isBuffering && controller.error == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                controller.error?.let { message ->
                    Text(
                        message,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                }
            }

            AnimatedVisibility(visible = zapBarVisible && zapList.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                ZapBar(zapList, channel)
            }

            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver") }
                    IconButton({ controller.togglePlayPause(); lastInteraction = System.currentTimeMillis() }) {
                        Icon(
                            if (controller.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(channel.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    // OJO: no paramos el reproductor al salir de esta pantalla a proposito -- al
    // volver a "TV en vivo"/"Peliculas" etc. el canal sigue sonando en la vista
    // previa pequeña (ver MiniPlayerPreview), igual que hacen TiviMate y similares.
}

/**
 * Tira horizontal con el canal actual centrado, para ubicarse al zapear con
 * izquierda/derecha. Puramente informativa (no roba el foco del Box principal,
 * que es quien de verdad procesa las flechas) -- por eso no usa tvFocusable.
 */
@Composable
private fun ZapBar(zapList: List<Channel>, current: Channel) {
    val state = rememberLazyListState()
    val currentIndex = remember(current.id, zapList) { zapList.indexOfFirst { it.id == current.id } }
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) state.animateScrollToItem(maxOf(0, currentIndex - 2))
    }
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(zapList, key = { it.id }) { ch ->
            val isCurrent = ch.id == current.id
            Box(
                Modifier
                    .background(
                        if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        MaterialTheme.shapes.small,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    ch.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Miniatura del canal que sigue reproduciendose mientras se navega por el resto
 * de la app (listas, categorias...). Al pulsarla, [onExpand] vuelve a pantalla
 * completa. Usa el mismo [ExoPlayerController.player]: Media3 permite mover el
 * mismo Player entre varias PlayerView, solo una a la vez tiene la superficie.
 */
@Composable
fun MiniPlayerPreview(controller: ExoPlayerController, channelName: String, onExpand: () -> Unit) {
    Column(
        Modifier
            .tvFocusable(MaterialTheme.shapes.medium)
            .background(Color.Black, MaterialTheme.shapes.medium)
            .clickable(onClick = onExpand)
            .padding(2.dp),
    ) {
        Box(Modifier.size(width = 200.dp, height = 112.dp)) {
            AndroidView(
                factory = { context -> PlayerView(context).apply { player = controller.player; useController = false } },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            channelName,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(4.dp),
        )
    }
}
