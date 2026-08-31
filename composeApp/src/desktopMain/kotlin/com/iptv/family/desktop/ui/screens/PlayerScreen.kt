package com.iptv.family.desktop.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iptv.family.desktop.player.VlcController
import com.iptv.family.desktop.player.VlcNative
import com.iptv.family.desktop.remote.LocalMuxKey
import com.iptv.family.desktop.state.AppState
import com.iptv.family.shared.model.Channel
import com.iptv.family.shared.i18n.T
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

/**
 * Pantalla de reproduccion: video a la izquierda, lista de zapeo a la derecha
 * y barra de controles debajo.
 *
 * Los controles NO van superpuestos al video: libvlc pinta en un componente AWT
 * pesado y Compose no puede dibujar por encima de forma fiable en Windows.
 */
@Composable
fun PlayerScreen(
    controller: VlcController,
    channel: Channel,
    zapList: List<Channel>,
    appState: AppState,
    bufferMs: Int,
    hardwareDecoding: Boolean,
    scope: CoroutineScope,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onSelectChannel: (Channel) -> Unit,
    onBack: () -> Unit,
) {
    // El stream arranca aqui, no al pulsar el canal: libvlc necesita que su
    // superficie AWT este ya en pantalla antes de aceptar un medio.
    LaunchedEffect(channel.id, controller) {
        var waited = 0L
        while (!controller.isSurfaceReady && waited < SURFACE_TIMEOUT_MS) {
            delay(SURFACE_POLL_MS)
            waited += SURFACE_POLL_MS
        }
        // Si el servidor web esta activo, VLC consume el canal a traves del
        // multiplexor local (`/stream/current.m3u8`): hacia el proveedor hay
        // UNA sola conexion aunque VLC y los navegadores remotos lo vean a la
        // vez (paneles limitados a T.unaConexionPorCuenta patean la 2a sesion).
        // url (origen) sigue siendo la identidad del canal para zapeo/EPG/web;
        // el proxy la lee de controller.currentUrl.
        fun muxUrl(): String? {
            val s = appState.settings
            if (!s.enableWebServer) return null
            // Clave interna, no una cuenta de usuario: el escritorio es el servidor
            // y no tiene por que identificarse contra si mismo (ver LocalMuxKey).
            // `nt=1` para que el mux le sirva el stream original y no la version
            // con el audio reconvertido, que es solo para navegadores.
            // Con HTTPS activo el servidor abre ademas un conector plano en
            // loopback (puerto+1) para los consumidores internos: VLC no sabe
            // que hacer con un certificado autofirmado y abriria un dialogo.
            val puerto = if (s.webServerHttps) s.webServerPort + 1 else s.webServerPort
            return "http://127.0.0.1:$puerto/stream/current.m3u8" +
                "?nt=1&${LocalMuxKey.PARAM}=${LocalMuxKey.value}"
        }
        controller.play(
            url = channel.url,
            networkCachingMs = bufferMs,
            hardwareDecoding = hardwareDecoding,
            playbackUrl = muxUrl(),
        )
    }

    // La linea "Ahora / Luego" del EPG debe refrescarse cuando cambian los programas.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            appState.bumpEpgTick()
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.weight(1f).fillMaxHeight().background(Color.Black), contentAlignment = Alignment.Center) {
                if (VlcNative.isAvailable) {
                    SwingPanel(
                        background = Color.Black,
                        factory = { controller.component },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    MissingVlcNotice()
                }
            }

            if (!isFullscreen) {
                ZapList(
                    channels = zapList,
                    current = channel,
                    appState = appState,
                    scope = scope,
                    onSelectChannel = onSelectChannel,
                )
            }
        }

        // Errores y progreso van FUERA del area de video: el componente AWT de
        // libvlc es pesado y tapa cualquier cosa que Compose dibuje encima.
        controller.error?.let { message ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        ControlBar(
            controller = controller,
            channel = channel,
            appState = appState,
            isFullscreen = isFullscreen,
            onToggleFullscreen = onToggleFullscreen,
            onBack = onBack,
            onPrev = { zapList.neighbourOf(channel, -1)?.let(onSelectChannel) },
            onNext = { zapList.neighbourOf(channel, +1)?.let(onSelectChannel) },
        )
    }
}

private const val SURFACE_POLL_MS = 50L
private const val SURFACE_TIMEOUT_MS = 5_000L

/**
 * Vecino en la lista de zapeo, DANDO LA VUELTA en los extremos.
 *
 * Antes estos botones se paraban en el primer y ultimo canal mientras el
 * teclado (Main.kt) y la web si daban la vuelta: el mismo gesto hacia dos cosas
 * distintas segun por donde se pidiera. Circular es ademas lo que hace
 * cualquier television.
 */
private fun List<Channel>.neighbourOf(current: Channel, delta: Int): Channel? {
    if (isEmpty()) return null
    val index = indexOfFirst { it.id == current.id }
    if (index < 0) return null
    return this[(index + delta).mod(size)]
}

@Composable
private fun ZapList(
    channels: List<Channel>,
    current: Channel,
    appState: AppState,
    scope: CoroutineScope,
    onSelectChannel: (Channel) -> Unit,
) {
    val state = rememberLazyListState()

    // Al cambiar de canal, mantener el actual a la vista.
    LaunchedEffect(current.id, channels.size) {
        val index = channels.indexOfFirst { it.id == current.id }
        if (index >= 0) state.animateScrollToItem(index)
    }

    Column(
        Modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
    ) {
        Text(
            "Canales (${channels.size})",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            items(channels, key = { it.id }) { ch ->
                ChannelRow(
                    channel = ch,
                    onChannelClick = onSelectChannel,
                    scope = scope,
                    appState = appState,
                    isCurrent = ch.id == current.id,
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun ControlBar(
    controller: VlcController,
    channel: Channel,
    appState: AppState,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = T.volverALaLista)
        }
        IconButton(onPrev) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = T.canalAnterior)
        }
        IconButton({ controller.togglePlayPause() }) {
            Icon(
                if (controller.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (controller.isPlaying) "Pausar" else "Reproducir",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onNext) {
            Icon(Icons.Rounded.SkipNext, contentDescription = T.canalSiguiente)
        }
        IconButton({ controller.stop() }) {
            Icon(Icons.Rounded.Stop, contentDescription = T.detener)
        }

        Spacer(Modifier.width(8.dp))

        if (controller.isBuffering && controller.error == null) {
            CircularProgressIndicator(
                Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                channel.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                when {
                    controller.error != null -> "Error"
                    controller.isBuffering -> "Cargando…"
                    controller.isPlaying -> "En directo · ${appState.groupName(channel) ?: T.sinGrupo}"
                    else -> T.enPausa
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            // Guia: "Ahora" y, si hay, "Luego". Oculto en pantalla completa.
            if (!isFullscreen) {
                val epgTick = appState.epgTick
                val now = remember(channel.id, epgTick) { appState.currentProgram(channel) }
                val next = remember(channel.id, epgTick) { appState.nextProgram(channel) }
                now?.let { program ->
                    Text(
                        buildString {
                            append("Ahora: ${program.title}")
                            next?.let { append(" · Luego: ${it.title}") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }

        // Selector de idioma: solo aparece si el canal trae mas de una pista.
        AudioTrackSelector(controller)

        IconButton({ controller.changeMuted(!controller.isMuted) }) {
            Icon(
                if (controller.isMuted || controller.volume == 0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = if (controller.isMuted) T.quitarSilencio else T.silenciar,
            )
        }
        Slider(
            value = controller.volume.toFloat(),
            onValueChange = { controller.changeVolume(it.toInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.width(120.dp),
        )

        IconButton(onToggleFullscreen) {
            Icon(
                if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                contentDescription = if (isFullscreen) T.salirPantallaCompleta else T.pantallaCompleta,
            )
        }
    }
}

/**
 * Elegir el idioma del audio. No se muestra cuando el canal solo trae una pista,
 * que es la mayoria: ocuparia sitio en la barra sin ofrecer nada.
 *
 * El español se selecciona solo al empezar (ver VlcController.refreshAudioTracks);
 * esto es para cambiarlo a mano cuando haga falta.
 */
@Composable
private fun AudioTrackSelector(controller: VlcController) {
    val tracks = controller.audioTracks
    if (tracks.size < 2) return

    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(
                Icons.Rounded.Translate,
                contentDescription = T.idiomaDelAudio,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(T.audio, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            tracks.forEach { track ->
                DropdownMenuItem(
                    text = { Text(track.label) },
                    leadingIcon = {
                        if (track.id == controller.currentAudioTrackId) {
                            Icon(Icons.Rounded.Check, contentDescription = T.seleccionado)
                        }
                    },
                    onClick = {
                        controller.selectAudioTrack(track.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MissingVlcNotice() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            T.sinMotorDeVideo,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            T.sinMotorDeVideoAyuda,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFB0B8C8),
            textAlign = TextAlign.Center,
        )
    }
}
