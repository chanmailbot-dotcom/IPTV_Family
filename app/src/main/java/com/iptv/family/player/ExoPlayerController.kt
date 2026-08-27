package com.iptv.family.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.iptv.family.shared.log.AppLog

/**
 * Envuelve Media3 ExoPlayer y expone su estado como estado de Compose.
 * Equivalente Android de `VlcController` en el cliente de escritorio (que usa
 * libvlc via vlcj, no disponible en Android).
 */
class ExoPlayerController(context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var currentUrl by mutableStateOf<String?>(null)
        private set

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) error = null
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
            }

            override fun onPlayerError(playbackException: PlaybackException) {
                isPlaying = false
                isBuffering = false
                AppLog.e("ExoPlayer", "error al reproducir ${AppLog.redactUrl(currentUrl.orEmpty())}", playbackException)
                error = "No se pudo abrir el canal: ${playbackException.errorCodeName}"
            }
        })
    }

    fun play(url: String) {
        if (url.isBlank()) return
        // Si ya es el mismo canal (p.ej. la previsualizacion por foco ya lo cargo
        // y ahora se confirma con OK), no reiniciar: cortaria la reproduccion en
        // curso sin necesidad.
        if (url == currentUrl && (isPlaying || isBuffering)) return
        AppLog.d("ExoPlayer", "play: ${AppLog.redactUrl(url)}")
        error = null
        isBuffering = true
        currentUrl = url
        runCatching {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        }.onFailure {
            AppLog.e("ExoPlayer", "play: fallo al preparar", it)
            isBuffering = false
            error = "No se pudo iniciar la reproducción: ${it.message ?: it::class.simpleName}"
        }
    }

    fun togglePlayPause() {
        if (currentUrl == null) return
        player.playWhenReady = !player.playWhenReady
    }

    fun stop() {
        player.stop()
        currentUrl = null
        isPlaying = false
        isBuffering = false
    }

    fun release() {
        if (released) return
        released = true
        runCatching { player.release() }
    }

    private var released = false
}
