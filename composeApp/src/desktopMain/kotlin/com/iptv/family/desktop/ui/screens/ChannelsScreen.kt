package com.iptv.family.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.iptv.family.desktop.state.AppState
import com.iptv.family.shared.model.Category
import com.iptv.family.shared.model.CategoryType
import com.iptv.family.shared.model.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.iptv.family.shared.domain.ParentalControl
import com.iptv.family.shared.i18n.T

private enum class TypeFilter(val type: CategoryType?) {
    ALL(null),
    LIVE(CategoryType.LIVE),
    VOD(CategoryType.VOD),
    SERIES(CategoryType.SERIES);

    /** En getter y no en el constructor: una etiqueta fijada al construir el
     *  enum se resuelve una sola vez y se quedaria en el idioma de entonces. */
    val label: String
        get() = when (this) {
            ALL -> T.todo
            LIVE -> T.tvEnDirecto
            VOD -> T.peliculas
            SERIES -> T.series
        }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChannelsScreen(
    appState: AppState,
    scope: CoroutineScope,
    onPlay: (Channel, List<Channel>) -> Unit,
    onGoHome: () -> Unit,
) {
    var selectedCategory by remember { mutableStateOf("all") }
    var typeFilter by remember { mutableStateOf(TypeFilter.ALL) }
    var search by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(setOf<String>()) }
    var pendingPin by remember { mutableStateOf<Category?>(null) }
    /** Serie cuyos episodios se estan mostrando, si hay alguna. */
    var openSeries by remember { mutableStateOf<Channel?>(null) }

    // La regla vive en `shared`: escritorio y Android tienen que bloquear
    // exactamente lo mismo. Estaba escrita solo aqui, y Android no filtraba nada.
    fun isAdult(name: String): Boolean = ParentalControl.isAdultCategory(name, appState.settings)

    // En M3U el grupo del canal ES el nombre de la categoria, pero en Xtream es un id
    // numerico. Sin traducir id -> nombre, el control parental no filtraria nada en Xtream.
    val categoryNames = remember(appState.categories) {
        appState.categories.associate { it.id to it.name }
    }

    fun isAdultGroup(groupId: String?): Boolean {
        val id = groupId ?: return false
        return isAdult(categoryNames[id] ?: id)
    }

    if (appState.selectedPlaylist == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(T.sinListaAbierta, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    T.veAMisListas,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onGoHome) { Text(T.irAMisListas) }
            }
        }
        return
    }

    appState.error?.let { message ->
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    T.noSePudoCargarLaLista,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Button(onGoHome) { Text(T.volverAMisListas) }
            }
        }
        return
    }

    if (appState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(T.cargandoCanales, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val visibleCategories = remember(appState.categories, typeFilter) {
        appState.categories.filter {
            it.id == "all" || typeFilter.type == null || it.type == typeFilter.type
        }
    }

    // Una categoria que ya no encaja con el filtro deja de ser una seleccion valida.
    // En LaunchedEffect y no en el cuerpo: escribir estado durante la composicion
    // provoca una recomposicion extra (y se ejecuta en composiciones descartadas).
    LaunchedEffect(visibleCategories, selectedCategory) {
        if (visibleCategories.none { it.id == selectedCategory }) selectedCategory = "all"
    }

    // Con 40.000 canales, filtrar en el cuerpo del composable recorria la lista
    // entera 3-4 veces por recomposicion (por cada tecla del buscador, por cada
    // cambio de foco...). Con remember solo se recalcula cuando cambia algo que
    // afecta al resultado, y en una sola pasada en vez de cuatro.
    val searchTerm = search.trim()
    val channels = remember(
        appState.channels,
        selectedCategory,
        typeFilter,
        searchTerm,
        unlocked,
        appState.settings.isParentalLockEnabled,
    ) {
        appState.channelsFor(selectedCategory).filter { channel ->
            (typeFilter.type == null || channel.categoryType == typeFilter.type) &&
                (searchTerm.isBlank() || channel.name.contains(searchTerm, ignoreCase = true)) &&
                !(isAdultGroup(channel.group) && channel.group !in unlocked)
        }
    }

    // La ventana estrecha reventaba la pantalla: el panel de categorias se
    // quedaba con sus 250 dp pasara lo que pasara, y lo que se encogia era la
    // lista. A 700 px los nombres de canal quedaban en «C» y «Ca»; a 560, el
    // texto «Buscar canal…» se escribia EN VERTICAL, una letra por linea, y no
    // se veia ni un canal. Por debajo del umbral, las categorias dejan de ser
    // una columna fija y pasan a un desplegable encima de la lista.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val estrecha = maxWidth < ANCHO_MINIMO_PARA_COLUMNA
    val seleccionar: (Category) -> Unit = { category ->
        if (isAdult(category.name) && category.id !in unlocked) pendingPin = category
        else selectedCategory = category.id
    }

    Row(Modifier.fillMaxSize()) {
        if (!estrecha) {
            CategorySidebar(
                categories = visibleCategories,
                selectedId = selectedCategory,
                isLocked = { category -> isAdult(category.name) && category.id !in unlocked },
                onSelect = seleccionar,
            )
        }

        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (estrecha) {
                CategoryDropdown(
                    categories = visibleCategories,
                    selectedId = selectedCategory,
                    isLocked = { category -> isAdult(category.name) && category.id !in unlocked },
                    onSelect = seleccionar,
                )
                Spacer(Modifier.height(8.dp))
            }

            // La lista es la copia guardada: hay que decirlo donde se ve la lista,
            // no en un log. Con un boton para reintentar, que es lo unico que se
            // puede hacer al respecto.
            appState.avisoSinConexion?.let { aviso ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        aviso,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton({ scope.launch { appState.refresh() } }) { Text(T.reintentar) }
                }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text(T.buscarCanal) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton({ search = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = T.limpiarBusqueda)
                        }
                    }
                },
            )

            Spacer(Modifier.height(10.dp))

            // FlowRow y no Row: en una ventana estrecha, una fila normal recorta lo
            // que no cabe y desaparecian «Películas» y «Series» sin dejar rastro
            // -- ni scroll, ni indicio de que hubiera mas filtros. Envolviendo,
            // caben siempre, en una o en dos lineas.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TypeFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = typeFilter == filter,
                        onClick = { typeFilter = filter },
                        label = { Text(filter.label, maxLines = 1) },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                T.cuentaCanales(channels.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            if (channels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (search.isBlank()) T.categoriaVacia else T.ningunCanalCoincide(search),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(channels, key = { it.id }) { channel ->
                        ChannelRow(
                            channel = channel,
                            // Una serie NO es reproducible por si misma: `get_series`
                            // devuelve el contenedor, no un flujo. Pulsarla abria
                            // ese contenedor y fallaba siempre; ahora despliega sus
                            // episodios, como ya hacia Android.
                            onChannelClick = {
                                if (it.categoryType == CategoryType.SERIES) openSeries = it
                                else onPlay(it, channels)
                            },
                            scope = scope,
                            appState = appState,
                        )
                    }
                }
            }
        }
    }
    } // BoxWithConstraints

    openSeries?.let { series ->
        EpisodesDialog(
            series = series,
            appState = appState,
            onDismiss = { openSeries = null },
            onPlayEpisode = { episode, episodes ->
                openSeries = null
                onPlay(episode, episodes)
            },
        )
    }

    pendingPin?.let { category ->
        PinDialog(
            expectedPin = appState.settings.parentalPin,
            onDismiss = { pendingPin = null },
            onUnlocked = {
                unlocked = unlocked + category.id
                selectedCategory = category.id
                pendingPin = null
            },
        )
    }
}

/**
 * Episodios de una serie.
 *
 * En Xtream, `get_series` devuelve solo el contenedor de la serie, con un id que
 * NO es reproducible; los episodios, cada uno con su propio id, hay que pedirlos
 * aparte con `get_series_info`. Por eso una serie abre esta lista en vez de
 * intentar reproducirse.
 */
@Composable
private fun EpisodesDialog(
    series: Channel,
    appState: AppState,
    onDismiss: () -> Unit,
    onPlayEpisode: (Channel, List<Channel>) -> Unit,
) {
    var episodes by remember(series.id) { mutableStateOf<List<Channel>?>(null) }
    var error by remember(series.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(series.id) {
        runCatching { appState.loadSeriesEpisodes(series.id) }
            .onSuccess { episodes = it }
            .onFailure {
                error = it.message ?: T.episodiosNoCargados
                episodes = emptyList()
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(series.name, maxLines = 2) },
        text = {
            val list = episodes
            when {
                list == null -> Box(
                    Modifier.fillMaxWidth().padding(28.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                list.isEmpty() -> Text(
                    error ?: T.serieSinEpisodios,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> LazyColumn(
                    Modifier.heightIn(max = 440.dp).width(520.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(list, key = { it.id }) { episode ->
                        TextButton(
                            onClick = { onPlayEpisode(episode, list) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                episode.name,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(T.cerrar) } },
    )
}

/**
 * Las categorias cuando no cabe la columna: un desplegable encima de la lista.
 *
 * Se enseña cuantos canales tiene cada una, igual que en la columna, porque es
 * lo que permite decidir sin entrar a mirar.
 */
@Composable
private fun CategoryDropdown(
    categories: List<Category>,
    selectedId: String,
    isLocked: (Category) -> Boolean,
    onSelect: (Category) -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }
    val actual = categories.firstOrNull { it.id == selectedId }

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { abierto = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                actual?.name ?: "Todas",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${actual?.channels?.size ?: 0}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = T.elegirCategoria)
        }

        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isLocked(category)) {
                                Icon(
                                    Icons.Rounded.Lock,
                                    contentDescription = T.bloqueada,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Text(
                                category.name,
                                fontWeight = if (category.id == selectedId) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 320.dp),
                            )
                            Text(
                                "${category.channels.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = { abierto = false; onSelect(category) },
                )
            }
        }
    }
}

@Composable
private fun CategorySidebar(
    categories: List<Category>,
    selectedId: String,
    isLocked: (Category) -> Boolean,
    onSelect: (Category) -> Unit,
) {
    Column(
        Modifier
            .width(250.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
    ) {
        Text(
            T.categorias,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(categories, key = { it.id }) { category ->
                val selected = category.id == selectedId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            MaterialTheme.shapes.medium,
                        )
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onSelect(category) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        category.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isLocked(category)) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = T.bloqueadaPorControlParental,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    } else if (category.channels.isNotEmpty()) {
                        Text(
                            category.channels.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinDialog(expectedPin: String?, onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T.controlParental) },
        text = {
            Column {
                Text(T.introduceElPin)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6) { pin = it; wrong = false } },
                    label = { Text("PIN") },
                    singleLine = true,
                    isError = wrong,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (wrong) {
                    Text(
                        "PIN incorrecto.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button({ if (ParentalControl.checkPin(pin, expectedPin)) onUnlocked() else wrong = true }) {
                Text(T.desbloquear)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(T.cancelar) } },
    )
}

/**
 * Por debajo de este ancho, la columna de categorias deja sitio a la lista y se
 * convierte en un desplegable. 820 dp es donde la lista deja de tener espacio
 * para un nombre de canal completo con la columna puesta.
 */
private val ANCHO_MINIMO_PARA_COLUMNA = 820.dp
