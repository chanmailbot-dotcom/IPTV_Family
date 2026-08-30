package com.iptv.family.desktop.remote

import com.iptv.family.desktop.player.VlcController
import com.iptv.family.desktop.state.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import com.iptv.family.shared.model.CategoryType

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
            // La firma incluye la LISTA ACTIVA, no solo los conteos: dos listas
            // distintas con el mismo numero de canales y categorias daban la
            // misma firma y la web nunca se enteraba del cambio. Se añade
            // ademas el primer y ultimo id, que cambian aunque los totales
            // coincidan (por ejemplo al refrescar y variar el orden).
            val signature = listOf(
                appState.selectedPlaylistId,
                appState.channels.size,
                appState.categories.size,
                appState.channels.firstOrNull()?.id,
                appState.channels.lastOrNull()?.id,
            ).joinToString("|").hashCode()
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
        val current = channel?.let { appState.currentProgram(it) }
        val next = channel?.let { appState.nextProgram(it) }
        return NowPlayingDto(
            channelId = channel?.id,
            channelName = channel?.name,
            channelNumber = channel?.number,
            logoUrl = channel?.logoUrl,
            // `Channel.group` es el id de categoria (en Xtream, un numero): hay que
            // resolverlo a nombre o la web muestra "142" en vez de "Deportes".
            group = channel?.group?.let { gid ->
                appState.categories.firstOrNull { it.id == gid }?.name ?: gid
            },
            isPlaying = controller?.isPlaying ?: false,
            isBuffering = controller?.isBuffering ?: false,
            error = controller?.error,
            kind = kindOf(channel?.categoryType ?: CategoryType.LIVE),
            volume = controller?.volume ?: 0,
            isMuted = controller?.isMuted ?: false,
            epgNow = current?.title,
            epgNext = next?.title,
            epgEndsAt = current?.endTime?.takeIf { it > 0 },
        )
    }

    fun stop() {
        job.cancel()
    }
}
