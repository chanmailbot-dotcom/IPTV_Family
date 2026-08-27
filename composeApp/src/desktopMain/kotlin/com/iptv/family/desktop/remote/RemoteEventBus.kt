package com.iptv.family.desktop.remote

import com.iptv.family.desktop.player.VlcController
import com.iptv.family.desktop.state.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

sealed interface RemoteEvent {
    data class NowPlaying(val dto: NowPlayingDto) : RemoteEvent
    data object ChannelsChanged : RemoteEvent
}

/**
 * Puente entre el estado de Compose (`mutableStateOf` en [AppState]/[VlcController],
 * que no se puede observar como Flow fuera de una composicion) y un [MutableSharedFlow]
 * que las conexiones SSE de /api/events pueden colectar.
 *
 * Compara snapshots cada 400ms: es indistinguible de "tiempo real" para una accion
 * humana (cambiar de canal) y no anade carga real.
 */
class RemoteEventBus(
    private val appState: AppState,
    private val controller: VlcController?,
    scope: CoroutineScope,
) {
    val events = MutableSharedFlow<RemoteEvent>(extraBufferCapacity = 16)

    private var lastNowPlaying: NowPlayingDto? = null
    private var lastChannelsSignature: Int? = null

    val job: Job = scope.launch {
        while (true) {
            val now = currentNowPlaying()
            if (now != lastNowPlaying) {
                lastNowPlaying = now
                events.emit(RemoteEvent.NowPlaying(now))
            }
            val signature = appState.channels.size * 31 + appState.categories.size
            if (signature != lastChannelsSignature) {
                lastChannelsSignature = signature
                events.emit(RemoteEvent.ChannelsChanged)
            }
            delay(400)
        }
    }

    fun currentNowPlaying(): NowPlayingDto {
        val url = controller?.currentUrl
        val channel = url?.let { u -> appState.channels.find { it.url == u } }
        return NowPlayingDto(
            channelId = channel?.id,
            channelName = channel?.name,
            logoUrl = channel?.logoUrl,
            isPlaying = controller?.isPlaying ?: false,
            isBuffering = controller?.isBuffering ?: false,
            error = controller?.error,
        )
    }

    fun stop() {
        job.cancel()
    }
}
