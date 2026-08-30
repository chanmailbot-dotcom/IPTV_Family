package com.iptv.family.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.shape.CircleShape

private const val CONTROLS_HIDE_DELAY_MS = 4000L
private const val ZAP_BAR_HIDE_DELAY_MS = 5000L
private const val SEEK_BAR_HIDE_DELAY_MS = 2500L
/** Salto por pulsacion de flecha. 10 s es lo que usan casi todos los reproductores de TV. */
private const val SEEK_STEP_MS = 10_000L

/** mm:ss, o h:mm:ss si la pelicula pasa de la hora. */
private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

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
    var seekBarVisible by remember { mutableStateOf(false) }
    var lastSeek by remember { mutableStateOf(0L) }
    var showAudioPicker by remember { mutableStateOf(false) }
    var showSubtitlePicker by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    /** Foco de la barra de mandos, para entrar en ella al pulsar OK. */
    val controlsFocus = remember { FocusRequester() }

    // Los controles (play, volumen...) se ocultan solos: en TV, tapan el video
    // y nadie los quiere ver fijos todo el rato. Cualquier pulsacion del mando
    // los vuelve a mostrar y reinicia la cuenta atras.
    // Este temporizador SOLO oculta. Antes tambien mostraba con cualquier
    // pulsacion, y con el modelo de dos modos eso se muerde la cola: la primera
    // flecha sacaria los mandos y a partir de ahi las flechas dejarian de
    // zapear. Ahora los mandos aparecen cuando se piden (OK, Menu, play/pausa)
    // y se van solos tras unos segundos sin tocar nada.
    // Con un menu de pistas abierto no se ocultan: al ocultarse devuelven el foco
    // al fondo de la pantalla y dejarian el menu abierto pero sin manejo.
    LaunchedEffect(controlsVisible, lastInteraction, showAudioPicker, showSubtitlePicker) {
        if (!controlsVisible || showAudioPicker || showSubtitlePicker) return@LaunchedEffect
        delay(CONTROLS_HIDE_DELAY_MS)
        controlsVisible = false
    }

    LaunchedEffect(lastZapInteraction) {
        if (lastZapInteraction == 0L) return@LaunchedEffect
        delay(ZAP_BAR_HIDE_DELAY_MS)
        zapBarVisible = false
    }

    LaunchedEffect(lastSeek) {
        if (lastSeek == 0L) return@LaunchedEffect
        delay(SEEK_BAR_HIDE_DELAY_MS)
        seekBarVisible = false
    }

    // La posicion no llega por eventos: hay que preguntarla. Solo mientras algo
    // se mueve, para no despertar la pantalla cada medio segundo en pausa.
    LaunchedEffect(controller.isPlaying, controller.currentUrl) {
        while (true) {
            controller.refreshProgress()
            if (!controller.isPlaying) break
            delay(500)
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // «Atras» NO puede depender del foco. Estaba resuelto dentro del manejador de
    // teclas del contenedor, que solo recibe pulsaciones mientras algo suyo tiene
    // el foco: al cerrar un menu de pistas el foco se queda en tierra de nadie y
    // la pulsacion se la acaba llevando la actividad, que cierra la aplicacion
    // entera. Y con los mandos ocultos nunca estuvo manejado, asi que salir de una
    // pelicula tampoco devolvia a la lista: cerraba la app.
    BackHandler {
        if (controlsVisible) controlsVisible = false else onBack()
    }

    // Al cerrar un menu de pistas hay que RECUPERAR el foco: mientras el dialogo
    // esta abierto lo tiene el, y al cerrarse no vuelve solo, con lo que las
    // flechas dejarian de saltar hasta volver a tocar algo.
    LaunchedEffect(showAudioPicker, showSubtitlePicker) {
        if (showAudioPicker || showSubtitlePicker) return@LaunchedEffect
        delay(80)
        runCatching {
            if (controlsVisible) controlsFocus.requestFocus() else focusRequester.requestFocus()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            // Mando a distancia, en DOS MODOS.
            //
            // Antes las cuatro flechas se consumian siempre para zapear, asi que
            // el foco no podia bajar nunca a los botones de abajo: estaban
            // dibujados pero eran inalcanzables con el mando. Y arriba/abajo
            // hacia lo mismo que izquierda/derecha, desperdiciando dos
            // direcciones.
            //
            //   Viendo (mandos ocultos)   flechas = zapear · OK = mostrar mandos
            //   Mandos visibles           flechas = moverse por ellos · Atras = ocultarlos
            //
            // Es como funciona cualquier reproductor de television, y es lo que
            // hace que los botones de abajo se puedan usar.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                lastInteraction = System.currentTimeMillis()

                // Teclas de medios: valen en los dos modos. Muchos mandos (y todos
                // los teclados de TV) las tienen y antes no hacian nada.
                when (event.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        controller.togglePlayPause()
                        controlsVisible = true
                        return@onKeyEvent true
                    }
                    Key.MediaNext, Key.ChannelUp -> {
                        zapList.neighbourOf(channel, +1)?.let(onSelectChannel)
                        return@onKeyEvent true
                    }
                    Key.MediaPrevious, Key.ChannelDown -> {
                        zapList.neighbourOf(channel, -1)?.let(onSelectChannel)
                        return@onKeyEvent true
                    }
                    Key.MediaFastForward, Key.MediaRewind -> {
                        if (!controller.isSeekable) return@onKeyEvent true
                        controller.seekBy(if (event.key == Key.MediaRewind) -30_000L else +30_000L)
                        seekBarVisible = true
                        lastSeek = System.currentTimeMillis()
                        return@onKeyEvent true
                    }
                    Key.Menu, Key.Info -> {
                        controlsVisible = !controlsVisible
                        return@onKeyEvent true
                    }
                    else -> Unit
                }

                if (controlsVisible) {
                    // Con los mandos a la vista, las flechas son para recorrerlos:
                    // NO se consumen, que es justo lo que faltaba. De «Atras» se
                    // encarga el BackHandler de arriba, que no depende del foco.
                    return@onKeyEvent false
                }

                when (event.key) {
                    // Arriba/abajo: canal anterior/siguiente al instante.
                    Key.DirectionUp -> {
                        zapList.neighbourOf(channel, -1)?.let(onSelectChannel)
                        true
                    }
                    Key.DirectionDown -> {
                        zapList.neighbourOf(channel, +1)?.let(onSelectChannel)
                        true
                    }
                    // Izquierda/derecha dependen de lo que se este viendo:
                    //  - pelicula o episodio: saltar dentro, que es lo unico que
                    //    se espera de esas dos teclas en algo con duracion.
                    //  - directo: zapear, enseñando la tira de canales para
                    //    ubicarse (se oculta sola a los 5s sin tocar el mando).
                    // El aviso del salto es una barra APARTE, no la de mandos: si
                    // sacara los mandos, la siguiente flecha ya no saltaria, se
                    // pondria a mover el foco entre botones.
                    Key.DirectionLeft -> {
                        if (controller.isSeekable) {
                            controller.seekBy(-SEEK_STEP_MS)
                            seekBarVisible = true
                            lastSeek = System.currentTimeMillis()
                        } else {
                            zapList.neighbourOf(channel, -1)?.let(onSelectChannel)
                            zapBarVisible = true
                            lastZapInteraction = System.currentTimeMillis()
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (controller.isSeekable) {
                            controller.seekBy(+SEEK_STEP_MS)
                            seekBarVisible = true
                            lastSeek = System.currentTimeMillis()
                        } else {
                            zapList.neighbourOf(channel, +1)?.let(onSelectChannel)
                            zapBarVisible = true
                            lastZapInteraction = System.currentTimeMillis()
                        }
                        true
                    }
                    // OK saca los mandos y les pasa el foco.
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        controlsVisible = true
                        true
                    }
                    else -> false // Atras y demas siguen su curso normal
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

            // Aviso de salto: aparece solo al saltar y con los mandos ocultos,
            // para ver donde se ha caido sin cambiar de modo.
            AnimatedVisibility(visible = seekBarVisible && !controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                ProgressBar(controller.positionMs, controller.durationMs, Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            }

            // Al sacar los mandos hay que MOVER el foco hasta ellos; si no, las
            // flechas siguen operando sobre el contenedor y los botones quedan
            // igual de inalcanzables que antes.
            LaunchedEffect(controlsVisible) {
                if (controlsVisible) {
                    delay(80) // dar tiempo a que se compongan
                    runCatching { controlsFocus.requestFocus() }
                } else {
                    runCatching { focusRequester.requestFocus() }
                }
            }

            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
              Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
                // Con duracion conocida, la barra va SIEMPRE a la vista mientras
                // esten los mandos: en una pelicula, saber cuanto queda es la
                // mitad de lo que se le pide a un reproductor.
                if (controller.isSeekable) {
                    ProgressBar(
                        controller.positionMs,
                        controller.durationMs,
                        Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onBack, modifier = Modifier.tvFocusable(CircleShape)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                    // El foco entra aqui al sacar los mandos con OK: es la accion
                    // mas probable, y asi no hay que buscarlo a ciegas.
                    IconButton(
                        onClick = { controller.togglePlayPause(); lastInteraction = System.currentTimeMillis() },
                        modifier = Modifier.tvFocusable(CircleShape).focusRequester(controlsFocus),
                    ) {
                        Icon(
                            if (controller.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (controller.isPlaying) "Pausar" else "Reproducir",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        channel.name,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // Solo se ofrecen si hay algo que elegir: un boton que abre
                    // una lista con una sola entrada es ruido.
                    if (controller.audioTracks.size > 1) {
                        TextButton(
                            onClick = { showAudioPicker = true; lastInteraction = System.currentTimeMillis() },
                            modifier = Modifier.tvFocusable(MaterialTheme.shapes.small),
                        ) { Text("Audio") }
                    }
                    if (controller.subtitleTracks.isNotEmpty()) {
                        TextButton(
                            onClick = { showSubtitlePicker = true; lastInteraction = System.currentTimeMillis() },
                            modifier = Modifier.tvFocusable(MaterialTheme.shapes.small),
                        ) { Text(if (controller.subtitlesEnabled) "Subtítulos ✓" else "Subtítulos") }
                    }
                    // Leyenda de mandos: sin ella no hay forma de adivinar que
                    // hacen las flechas ni que Atras cierra esta barra.
                    Text(
                        if (controller.isSeekable) "◀▶ 10 s   ·   Atrás cerrar" else "▲▼ canal   ·   Atrás cerrar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
              }
            }
        }
    }

    if (showAudioPicker) {
        TrackPickerDialog(
            title = "Pista de audio",
            options = controller.audioTracks,
            allowOff = false,
            onPick = { id -> controller.selectAudioTrack(id!!) },
            onDismiss = { showAudioPicker = false; lastInteraction = System.currentTimeMillis() },
        )
    }
    if (showSubtitlePicker) {
        TrackPickerDialog(
            title = "Subtítulos",
            options = controller.subtitleTracks,
            allowOff = true,
            offSelected = !controller.subtitlesEnabled,
            onPick = { id -> controller.selectSubtitleTrack(id) },
            onDismiss = { showSubtitlePicker = false; lastInteraction = System.currentTimeMillis() },
        )
    }

    // OJO: no paramos el reproductor al salir de esta pantalla a proposito -- al
    // volver a "TV en vivo"/"Peliculas" etc. el canal sigue sonando en la vista
    // previa pequeña (ver MiniPlayerPreview), igual que hacen TiviMate y similares.
}

/**
 * Posicion y duracion. No es focosable a proposito: con un mando se salta con
 * las flechas (ver el manejo de teclas), no arrastrando una bolita.
 */
@Composable
private fun ProgressBar(positionMs: Long, durationMs: Long, modifier: Modifier = Modifier) {
    val fraction = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column(modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(positionMs), style = MaterialTheme.typography.labelSmall)
            // Lo que queda importa mas que la duracion total: es la respuesta a
            // "¿me da tiempo a verla?".
            Text("-${formatTime(durationMs - positionMs)}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Menu de pistas. Vale para audio y para subtitulos; la diferencia es que los
 * subtitulos se pueden apagar y el audio no.
 */
@Composable
private fun TrackPickerDialog(
    title: String,
    options: List<com.iptv.family.player.TrackOption>,
    allowOff: Boolean,
    offSelected: Boolean = false,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (allowOff) {
                    TrackRow("Desactivados", offSelected) { onPick(null); onDismiss() }
                }
                options.forEach { option ->
                    TrackRow(option.label, option.selected) { onPick(option.id); onDismiss() }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tvFocusable(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // La marca va con texto ademas de con negrita: en una tele vista de lejos,
        // "un poco mas gruesa" no se distingue.
        Text(if (selected) "✓" else " ", style = MaterialTheme.typography.bodyLarge)
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
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
