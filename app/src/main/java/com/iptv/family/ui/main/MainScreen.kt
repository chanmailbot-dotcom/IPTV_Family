package com.iptv.family.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.iptv.family.R
import com.iptv.family.domain.model.Channel
import com.iptv.family.domain.model.Playlist
import com.iptv.family.util.NetworkUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    val playlists by viewModel.playlists.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var currentRoute by remember { mutableStateOf("home") }
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }
    } else {
        when (currentRoute) {
            "home" -> {
                MainHomeScreen(
                    playlists = playlists,
                    selectedPlaylist = selectedPlaylist,
                    channels = channels,
                    categories = categories,
                    onPlaylistSelected = { viewModel.selectPlaylist(it) },
                    onPlaylistAdd = { currentRoute = "add_playlist" },
                    onCategorySelected = { 
                        // Show channels for this category
                    },
                    onChannelSelected = { channel ->
                        selectedChannel = channel
                        currentRoute = "player"
                    },
                    onSearchRequested = { currentRoute = "search" },
                    onSettingsRequested = { currentRoute = "settings" },
                    onError = { error?.let { /* show message */ } },
                )
            }
            "add_playlist" -> {
                AddPlaylistScreen(
                    onPlaylistAdded = { currentRoute = "home" },
                    onDismiss = { currentRoute = "home" },
                )
            }
            "search" -> {
                SearchScreen(
                    channels = channels,
                    categories = categories,
                    onBack = { currentRoute = "home" },
                    onChannelSelected = { channel ->
                        selectedChannel = channel
                        currentRoute = "player"
                    },
                )
            }
            "player" -> {
                PlayerScreen(
                    channel = selectedChannel,
                    onBack = { currentRoute = "home" },
                    onFavoriteToggle = { /* toggle favorite */ },
                    onSettingsClick = { /* show quality/audio/subs */ },
                    onNextChannel = { /* get next channel */ },
                    onPreviousChannel = { /* get previous channel */ },
                )
            }
            "settings" -> {
                SettingsScreen(
                    onBack = { currentRoute = "home" },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainHomeScreen(
    playlists: List<Playlist>,
    selectedPlaylist: Playlist?,
    channels: List<Channel>,
    categories: List<com.iptv.family.domain.model.Category>,
    onPlaylistSelected: (Playlist) -> Unit,
    onPlaylistAdd: () -> Unit,
    onCategorySelected: (com.iptv.family.domain.model.Category) -> Unit,
    onChannelSelected: (Channel) -> Unit,
    onSearchRequested: () -> Unit,
    onSettingsRequested: () -> Unit,
    onError: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("IPTV Family") },
                actions = {
                    IconButton(onClick = onSearchRequested) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                    IconButton(onClick = onSettingsRequested) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = "Configuración"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onPlaylistAdd) {
                Icon(Icons.Default.Add, contentDescription = "Añadir lista")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Playlist selector
            if (playlists.isNotEmpty()) {
                PlaylistSelector(
                    playlists = playlists,
                    selectedPlaylist = selectedPlaylist,
                    onSelected = onPlaylistSelected,
                )
            }

            // Categories grid
            if (!categories.isNullOrEmpty()) {
                CategoryGrid(
                    categories = categories,
                    channels = channels,
                    onCategorySelected = onCategorySelected,
                )
            }

            // Recent Channels / Favorites
            val recentChannels = channels.take(12)
            if (recentChannels.isNotEmpty()) {
                ChannelSection(
                    title = "Canales",
                    channels = recentChannels,
                    onChannelSelected = onChannelSelected,
                )
            }

            if (playlists.isEmpty()) {
                EmptyState(
                    message = "No hay listas configuradas",
                    actionText = "Añadir lista",
                    onAction = onPlaylistAdd,
                )
            }
        }
    }
}

@Composable
fun PlaylistSelector(
    playlists: List<Playlist>,
    selectedPlaylist: Playlist?,
    onSelected: (Playlist) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clickable { expanded = true }
        ) {
            Text(
                text = selectedPlaylist?.name ?: "Seleccionar lista",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${selectedPlaylist?.channelCount ?: 0} canales",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            playlists.forEach { playlist ->
                DropdownMenuItem(
                    text = { Text(playlist.name) },
                    onClick = {
                        onSelected(playlist)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun CategoryGrid(
    categories: List<com.iptv.family.domain.model.Category>,
    channels: List<Channel>,
    onCategorySelected: (com.iptv.family.domain.model.Category) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(categories.take(9), key = { it.id }) { category ->
            CategoryCard(
                category = category,
                channelCount = channels.count { it.group == category.name },
                onSelected = { onCategorySelected(category) },
            )
        }
    }
}

@Composable
fun CategoryCard(
    category: com.iptv.family.domain.model.Category,
    channelCount: Int,
    onSelected: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onSelected),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                    )
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_category),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$channelCount canales",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ChannelSection(
    title: String,
    channels: List<Channel>,
    onChannelSelected: (Channel) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.padding(horizontal = 8.dp),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(channels.take(20), key = { it.id }) { channel ->
                ChannelCard(
                    channel = channel,
                    onSelected = { onChannelSelected(channel) },
                )
            }
        }
    }
}

@Composable
fun ChannelCard(
    channel: Channel,
    onSelected: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clickable(onClick = onSelected),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_channel),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                if (channel.isLive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(
                                color = MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(bottomStart = 4.dp, topEnd = 4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LIVE",
                            color = MaterialTheme.colorScheme.onError,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = androidx.compose.ui.unit.TextUnit.Unspecified,
                        )
                    }
                }
            }
            Text(
                text = channel.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
fun EmptyState(
    message: String,
    actionText: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_tv),
            contentDescription = null,
            modifier = Modifier.size(128.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAction) {
            Text(actionText)
        }
    }
}