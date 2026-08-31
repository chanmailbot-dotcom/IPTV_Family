package com.iptv.family.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.iptv.family.player.ExoPlayerController
import com.iptv.family.state.AppState
import com.iptv.family.shared.model.Category
import com.iptv.family.shared.model.CategoryType
import com.iptv.family.shared.i18n.T
import com.iptv.family.shared.model.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.iptv.family.shared.domain.ParentalControl
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Button
import androidx.compose.material.icons.rounded.Lock

/**
 * null = pantalla de Favoritos (todos los tipos, filtrados por favorito).
 *
 * [onPreview]/[previewController] son opcionales y solo tienen sentido para
 * TV en vivo: al mover el foco por la lista, se previsualiza el canal en una
 * ventanita antes de confirmar con OK (igual que TiviMate y similares).
 */
@Composable
fun ChannelsScreen(
    appState: AppState,
    scope: CoroutineScope,
    mediaType: CategoryType?,
    onPlay: (Channel, List<Channel>) -> Unit,
    onPreview: ((Channel, List<Channel>) -> Unit)? = null,
    previewController: ExoPlayerController? = null,
    /** El canal a re-enfocar al entrar (p.ej. al volver de pantalla completa). */
    restoreFocusChannelId: String? = null,
) {
    var activeCategory by remember(mediaType) { mutableStateOf("all") }
    /** Categorias desbloqueadas en esta sesion: el PIN se pide una vez, no en cada clic. */
    var unlocked by remember { mutableStateOf(setOf<String>()) }
    /** Categoria esperando PIN, si hay alguna. */
    var pendingPin by remember { mutableStateOf<com.iptv.family.shared.model.Category?>(null) }
    var search by remember { mutableStateOf("") }
    var focusedChannel by remember { mutableStateOf<Channel?>(null) }
    val listState = rememberLazyListState()

    val byType = remember(appState.channels, mediaType) {
        if (mediaType == null) appState.channels.filter { it.isFavorite }
        else appState.channels.filter { it.categoryType == mediaType }
    }
    val categoriesForType = remember(appState.categories, mediaType) {
        if (mediaType == null) emptyList() else appState.categories.filter { it.type == mediaType || it.id == "all" }
    }
    // Control parental. Sin esto el ajuste solo movia un interruptor: ni ocultaba
    // categorias ni pedia PIN. La regla vive en `shared` para que escritorio y
    // Android bloqueen exactamente lo mismo.
    val nombrePorCategoria = remember(appState.categories) {
        appState.categories.associate { it.id to it.name }
    }
    fun esAdulto(groupId: String?): Boolean = ParentalControl.isAdultCategory(
        // En Xtream `group` es un id numerico; hay que traducirlo a nombre o no
        // se reconoceria ninguna categoria.
        nombrePorCategoria[groupId] ?: groupId, appState.settings,
    )

    val list = remember(byType, activeCategory, search, unlocked, appState.settings) {
        byType.filter { ch ->
            (activeCategory == "all" || ch.group == activeCategory) &&
                (search.isBlank() || ch.name.contains(search, ignoreCase = true)) &&
                // Un canal de una categoria bloqueada no aparece ni en «Todas»
                // ni en las busquedas mientras no se desbloquee.
                !(esAdulto(ch.group) && ch.group !in unlocked)
        }
    }

    val isGrid = mediaType == CategoryType.VOD || mediaType == CategoryType.SERIES
    val showPreview = mediaType == CategoryType.LIVE && onPreview != null && previewController != null

    // Espera a que el foco se quede quieto un momento antes de cargar el stream:
    // si no, mover el mando rapido por la lista dispararia una peticion de red
    // (y un reinicio de video) por cada canal de paso.
    LaunchedEffect(focusedChannel, list) {
        val channel = focusedChannel ?: return@LaunchedEffect
        delay(450)
        onPreview?.invoke(channel, list)
    }

    Row(Modifier.fillMaxSize()) {
        if (mediaType != null) {
            Column(
                Modifier.width(220.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(12.dp),
            ) {
                Text(
                    appState.selectedPlaylist?.name ?: "Canales",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text(T.buscarCanal) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                LazyColumn {
                    items(categoriesForType, key = { it.id }) { category ->
                        val bloqueada = ParentalControl.isAdultCategory(category.name, appState.settings) &&
                            category.id !in unlocked
                        CategoryRow(category, category.id == activeCategory, bloqueada) {
                            if (bloqueada) pendingPin = category else activeCategory = category.id
                        }
                    }
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            // La lista es la copia guardada: se dice donde se ve la lista, no en
            // un log. Sin boton de reintentar porque desde el mando el gesto
            // natural es volver a entrar en la lista, y ademas se reintenta solo
            // al recargar la seccion.
            appState.avisoSinConexion?.let { aviso ->
                Text(
                    aviso,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }

            if (showPreview) {
                LivePreviewPanel(controller = previewController!!, channelName = (focusedChannel ?: list.firstOrNull())?.name)
            }

            // "Cargando" y "vacio" NO son lo mismo, y aqui se enseñaban igual:
            // mientras se descargaba la lista (que con un proveedor lento son
            // varios segundos, y hasta 15 si acaba en tiempo agotado) la pantalla
            // decia «No hay elementos en esta categoría». O sea, se afirmaba que
            // no hay nada cuando todavia no se sabe.
            if (appState.isLoading && appState.channels.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(T.cargandoCanales, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (list.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (mediaType == null) T.sinFavoritos else T.sinElementosEnCategoria,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (isGrid) {
                var openSeries by remember { mutableStateOf<Channel?>(null) }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.id }) { channel ->
                        PosterCard(
                            channel = channel,
                            // Una pelicula se reproduce directo; una serie no es reproducible
                            // por si misma (ver getSeriesEpisodes), asi que abre su lista de episodios.
                            onClick = {
                                if (mediaType == CategoryType.SERIES) openSeries = channel
                                else onPlay(channel, list)
                            },
                            onToggleFavorite = { scope.launch { appState.toggleFavorite(channel.id) } },
                        )
                    }
                }

                pendingPin?.let { categoria ->
        UnlockPinDialog(
            categoryName = categoria.name,
            expectedPin = appState.settings.parentalPin,
            onDismiss = { pendingPin = null },
            onUnlocked = {
                unlocked = unlocked + categoria.id
                activeCategory = categoria.id
                pendingPin = null
            },
        )
    }

    openSeries?.let { series ->
                    EpisodesDialog(
                        seriesName = series.name,
                        appState = appState,
                        seriesId = series.id,
                        onDismiss = { openSeries = null },
                        onPlayEpisode = { episode, episodes ->
                            openSeries = null
                            onPlay(episode, episodes)
                        },
                    )
                }
            } else {
                // Al volver de pantalla completa el foco no debe "perderse": se recupera
                // sobre el canal que se estaba viendo, no en el primero de la lista.
                LaunchedEffect(restoreFocusChannelId, list) {
                    val idx = list.indexOfFirst { it.id == restoreFocusChannelId }
                    if (idx >= 0) listState.scrollToItem(idx)
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().padding(12.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(list, key = { it.id }) { channel ->
                        ChannelRow(
                            channel = channel,
                            onClick = { onPlay(channel, list) },
                            onToggleFavorite = { scope.launch { appState.toggleFavorite(channel.id) } },
                            onFocusChange = { focused -> if (focused) focusedChannel = channel },
                            requestFocusOnAppear = channel.id == restoreFocusChannelId,
                            appState = appState,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodesDialog(
    seriesName: String,
    appState: AppState,
    seriesId: String,
    onDismiss: () -> Unit,
    onPlayEpisode: (Channel, List<Channel>) -> Unit,
) {
    var episodes by remember(seriesId) { mutableStateOf<List<Channel>?>(null) }

    LaunchedEffect(seriesId) {
        episodes = appState.loadSeriesEpisodes(seriesId)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(seriesName) },
        text = {
            val list = episodes
            when {
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                list.isEmpty() -> Text("No se encontraron episodios para esta serie.")
                else -> LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(list, key = { it.id }) { episode ->
                        ChannelRow(
                            channel = episode,
                            onClick = { onPlayEpisode(episode, list) },
                            onToggleFavorite = {},
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun LivePreviewPanel(controller: ExoPlayerController, channelName: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            AndroidView(
                factory = { context -> PlayerView(context).apply { player = controller.player; useController = false } },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            channelName ?: "",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun CategoryRow(category: Category, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tvFocusable(MaterialTheme.shapes.medium)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // El candado hace visible que la categoria existe pero pide PIN. Sin el,
        // pulsar y que no pase nada parece que la aplicacion esta rota.
        if (locked) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = "Bloqueada",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(category.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelRow(
    channel: Channel,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
    requestFocusOnAppear: Boolean = false,
    /** Si se pasa, la fila muestra "Ahora" con la guia EPG de la lista activa. */
    appState: AppState? = null,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(requestFocusOnAppear) {
        if (requestFocusOnAppear) focusRequester.requestFocus()
    }

    // Un unico foco por fila: la estrella no es un foco aparte (obligaria a
    // moverse a la derecha en cada canal para alcanzarla, muy tedioso en listas
    // largas). Se pulsa OK para reproducir y se mantiene pulsado para marcar
    // favorito -- patron habitual en apps de TV con mando.
    Row(
        Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .tvFocusable(MaterialTheme.shapes.medium, onFocusChange)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onToggleFavorite)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (channel.logoUrl != null) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(32.dp),
            )
        } else {
            Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small))
        }
        // Ahora / programa en curso (EPG) de la lista activa, si hay guia.
        val state = appState
        val program = if (state != null) {
            val epgTick = state.epgTick
            remember(channel.id, epgTick) { state.currentProgram(channel) }
        } else null
        Column(Modifier.weight(1f)) {
            Text(channel.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (program != null) {
                Text(
                    "Ahora: ${program.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            if (channel.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = if (channel.isFavorite) "Favorito (mantén pulsado para quitar)" else "Mantén pulsado para marcar favorito",
            tint = if (channel.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterCard(channel: Channel, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    // Un unico foco por caratula; mantener pulsado marca/desmarca favorito
    // (igual criterio que ChannelRow, ver comentario alli).
    Column(
        Modifier
            .tvFocusable(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onToggleFavorite),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
        ) {
            if (channel.logoUrl != null) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        channel.name.take(2).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (channel.isFavorite) {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = "Favorito",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
        }
        Text(
            channel.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Pide el PIN para abrir una categoria bloqueada.
 *
 * Si no hay PIN definido se dice claramente en vez de dejar al usuario probando
 * combinaciones: con el control activado y sin PIN, la categoria no se puede
 * abrir de ninguna manera.
 */
@Composable
private fun UnlockPinDialog(
    categoryName: String,
    expectedPin: String?,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var fallo by remember { mutableStateOf(false) }
    val sinPin = expectedPin.isNullOrBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Control parental") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (sinPin) {
                    Text("«$categoryName» está bloqueada y todavía no hay ningún PIN definido.")
                    Text(
                        "Ve a Ajustes → Control parental para definirlo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Introduce el PIN para abrir «$categoryName».")
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= ParentalControl.MAX_PIN_LENGTH) { pin = it; fallo = false } },
                        label = { Text("PIN") },
                        singleLine = true,
                        isError = fallo,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    if (fallo) {
                        Text(
                            "PIN incorrecto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!sinPin) {
                Button(onClick = {
                    if (ParentalControl.checkPin(pin, expectedPin)) onUnlocked() else fallo = true
                }) { Text("Desbloquear") }
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(if (sinPin) "Entendido" else "Cancelar") } },
    )
}
