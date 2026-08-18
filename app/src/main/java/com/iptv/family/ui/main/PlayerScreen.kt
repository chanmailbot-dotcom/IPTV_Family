package com.iptv.family.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrikePathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.iptv.family.R
import com.iptv.family.domain.model.Channel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun PlayerScreen(
    channel: Channel?,
    onBack: () -> Unit,
    onFavoriteToggle: (Channel) -> Unit,
    onSettingsClick: () -> Unit,
    onNextChannel: () -> Unit,
    onPreviousChannel: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }

    WindowInsetsControllerCompat(
        (context as androidx.activity.ComponentActivity).window,
        androidx.compose.ui.platform.LocalView.current,
    ).isBarsVisible = !isFullscreen

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = isControlsVisible) {
                if (isControlsVisible) {
                    isControlsVisible = false
                }
            },
    ) {
        // Video player surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (channel != null) {
                // Video would be rendered here via AndroidView(ExoPlayerView)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF121212)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Video: ${channel.displayName}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }

        // Tap detection for controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            isControlsVisible = !isControlsVisible
                        },
                        onLongPress = { offset ->
                            // Maybe show settings
                            isControlsVisible = true
                        },
                    )
                },
        )

        // Loading indicator
        if (isPlaying && progress < 0.1f) {
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        // Controls overlay
        if (isControlsVisible) {
            // Top bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color(0xAA000000),
                                Color(0x00000000),
                            ),
                        ),
                    )
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                    Text(
                        text = channel?.displayName ?: "Canal desconocido",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showSettingsMenu = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Más opciones", tint = Color.White)
                    }
                }
            }

            // Center: play/pause
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .background(
                        color = Color(0x66000000),
                        shape = RoundedCornerShape(50.dp),
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onPreviousChannel() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Canal anterior", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = { isPlaying = !isPlaying }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }
                IconButton(onClick = { onNextChannel() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Canal siguiente", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }

            // Bottom controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color(0x00000000),
                                Color(0xAA000000),
                            ),
                        ),
                    )
                    .padding(16.dp),
            ) {
                // Progress bar
                Slider(
                    value = progress,
                    onValueChange = {
                        progress = it
                        isSeeking = true
                    },
                    onValueChangeFinished = {
                        isSeeking = false
                    },
                    colors = SliderDefaults.sliderColors(
                        thumbColor = Color.White,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.Gray,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "00:00:00 / 00:00:00",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { isFullscreen = !isFullscreen }) {
                            Icon(Icons.Default.Fullscreen, contentDescription = if (isFullscreen) "Salir pantalla completa" else "Pantalla completa", tint = Color.White)
                        }
                        IconButton(onClick = { showQualityDialog = true }) {
                            Text(
                                text = "480p",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { onFavoriteToggle(channel!!) }) {
                            val isFav = channel?.isFavorite ?: false
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = if (isFav) "Quitar favorito" else "Añadir favorito",
                                tint = if (isFav) MaterialTheme.colorScheme.primary else Color.White,
                            )
                        }
                    }
                }
            }
        }

        // Settings menu
        if (showSettingsMenu) {
            DropdownMenu(
                expanded = showSettingsMenu,
                onDismissRequest = { showSettingsMenu = false },
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                DropdownMenuItem(
                    text = { Text("Calidad de video") },
                    onClick = {
                        showSettingsMenu = false
                        showQualityDialog = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("Subtítulos") },
                    onClick = { /* toggle subtitles */ }
                )
                DropdownMenuItem(
                    text = { Text("Audio") },
                    onClick = { /* toggle audio */ }
                )
            }
        }

        // Quality selection dialog
        if (showQualityDialog) {
            AlertDialog(
                onDismissRequest = { showQualityDialog = false },
                title = { Text("Seleccionar calidad") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val qualities = listOf("1080p", "720p", "480p", "360p", "Auto")
                        qualities.forEach { quality ->
                            TextButton(
                                onClick = {
                                    showQualityDialog = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(quality)
                            }
                        }
                    }
                },
            )
        }
    }
}