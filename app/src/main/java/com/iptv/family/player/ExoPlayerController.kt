package com.iptv.family.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.iptv.family.shared.log.AppLog
import java.util.Locale

/**
 * Una pista de audio o de subtitulos tal y como se le enseña a la gente.
 * [id] apunta al grupo y a la pista dentro del grupo; es lo que hace falta para
 * pedirle el cambio a ExoPlayer, pero no significa nada fuera de aqui.
 */
data class TrackOption(
    val id: String,
    val label: String,
    val selected: Boolean,
)

/**
 * Envuelve Media3 ExoPlayer y expone su estado como estado de Compose.
 * Equivalente Android de `VlcController` en el cliente de escritorio (que usa
 * libvlc via vlcj, no disponible en Android).
 */
class ExoPlayerController(context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        // Idioma de audio por defecto: español. Ademas se descarta explicitamente
        // la audiodescripcion (ROLE_FLAG_DESCRIBES_VIDEO), porque en television
        // española es habitual que esa sea la PRIMERA pista del canal: sin esto
        // se oye al narrador describiendo la escena en vez del audio normal.
        // Basta con la preferencia de idioma: la audiodescripcion viene etiquetada
        // como "qad" (rango ISO 639-3 de uso local), que NO casa con español, asi
        // que ExoPlayer se queda con la pista "spa" aunque vaya la segunda.
        trackSelectionParameters = trackSelectionParameters
            .buildUpon()
            .setPreferredAudioLanguages("es", "spa", "cas")
            .build()
    }

    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var currentUrl by mutableStateOf<String?>(null)
        private set

    /**
     * Si lo que suena admite saltar por dentro. Es la diferencia real entre una
     * pelicula y un canal en directo, y no se puede deducir del catalogo: hay
     * proveedores que sirven las peliculas por HLS y canales con ventana de
     * rebobinado. Lo dice el propio reproductor una vez abierto el medio.
     */
    var isSeekable by mutableStateOf(false)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var positionMs by mutableStateOf(0L)
        private set

    var audioTracks by mutableStateOf<List<TrackOption>>(emptyList())
        private set
    var subtitleTracks by mutableStateOf<List<TrackOption>>(emptyList())
        private set
    /** Los subtitulos van apagados salvo que se pidan; nadie quiere abrir una pelicula con texto encima. */
    var subtitlesEnabled by mutableStateOf(false)
        private set

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) error = null
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) refreshProgress()
            }

            override fun onTracksChanged(tracks: Tracks) {
                rebuildTracks(tracks)
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
        // Las pistas y la posicion son del medio anterior: si no se limpian, al
        // cambiar de pelicula se ve un instante el menu de audio de la anterior
        // y una barra que marca un minutaje que ya no existe.
        isSeekable = false
        durationMs = 0L
        positionMs = 0L
        audioTracks = emptyList()
        subtitleTracks = emptyList()
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

    /** Relee posicion y duracion del reproductor. La pantalla la llama cada poco. */
    fun refreshProgress() {
        if (released) return
        val duration = player.duration
        durationMs = if (duration == C.TIME_UNSET || duration <= 0L) 0L else duration
        positionMs = player.currentPosition.coerceAtLeast(0L)
        isSeekable = player.isCurrentMediaItemSeekable && durationMs > 0L
    }

    fun seekTo(ms: Long) {
        if (!isSeekable) return
        val target = ms.coerceIn(0L, durationMs)
        player.seekTo(target)
        positionMs = target
    }

    /** Salto relativo, que es como se usa de verdad con un mando: izquierda/derecha. */
    fun seekBy(deltaMs: Long) = seekTo(positionMs + deltaMs)

    fun selectAudioTrack(id: String) = applyOverride(C.TRACK_TYPE_AUDIO, id)

    fun selectSubtitleTrack(id: String?) {
        if (id == null) {
            // Apagar no es "ninguna pista": hay que decirle a ExoPlayer que no
            // elija ninguna, o volveria a poner la que le parezca mejor.
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            subtitlesEnabled = false
            rebuildTracks(player.currentTracks)
        } else {
            applyOverride(C.TRACK_TYPE_TEXT, id)
            subtitlesEnabled = true
        }
    }

    private fun applyOverride(type: Int, id: String) {
        val (groupIndex, trackIndex) = id.split(':').let { it.getOrNull(0)?.toIntOrNull() to it.getOrNull(1)?.toIntOrNull() }
        if (groupIndex == null || trackIndex == null) return
        val group = player.currentTracks.groups.getOrNull(groupIndex) ?: return
        if (group.type != type) return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
        rebuildTracks(player.currentTracks)
    }

    private fun rebuildTracks(tracks: Tracks) {
        val audio = mutableListOf<TrackOption>()
        val text = mutableListOf<TrackOption>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            for (trackIndex in 0 until group.length) {
                // Una pista que este aparato no sabe decodificar no se ofrece:
                // elegirla dejaria la pelicula muda sin explicar por que.
                if (!group.isTrackSupported(trackIndex)) continue
                val format = group.getTrackFormat(trackIndex)
                val option = TrackOption(
                    id = "$groupIndex:$trackIndex",
                    label = describe(format.language, format.label, audio.size + text.size),
                    selected = group.isTrackSelected(trackIndex),
                )
                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> audio += option
                    C.TRACK_TYPE_TEXT -> text += option
                    else -> Unit
                }
            }
        }
        audioTracks = audio
        subtitleTracks = text
        subtitlesEnabled = text.any { it.selected }
    }

    /**
     * Nombre legible de una pista. El codigo de idioma crudo ("spa", "fre") no le
     * dice nada a nadie, asi que se traduce; si la pista no trae idioma se cae a
     * la etiqueta del proveedor y, en ultimo extremo, a un numero.
     */
    private fun describe(language: String?, label: String?, ordinal: Int): String {
        val fromLanguage = language
            ?.takeIf { it.isNotBlank() && !it.startsWith(C.LANGUAGE_UNDETERMINED) }
            ?.let { displayLanguage(it) }
            ?.replaceFirstChar { it.uppercase() }
        return when {
            fromLanguage != null && label != null -> "$fromLanguage · $label"
            fromLanguage != null -> fromLanguage
            !label.isNullOrBlank() -> label
            else -> "Pista ${ordinal + 1}"
        }
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

    private companion object {
        /**
         * Los nombres de idioma de Java estan indexados por el codigo de DOS
         * letras: pedirle el nombre de "spa" devuelve "spa" otra vez. Y las
         * pistas de IPTV vienen casi siempre en tres letras. Asi que se
         * construye la tabla al reves desde el propio Java, que ya sabe que
         * "es" es "spa".
         */
        private val ISO3_TO_ISO2: Map<String, String> = buildMap {
            for (code in Locale.getISOLanguages()) {
                runCatching { Locale(code).isO3Language }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { put(it, code) }
            }
            // Codigos "bibliograficos" de ISO 639-2/B. Java usa los
            // terminologicos (fra, deu...), pero los paneles de IPTV emiten
            // sistematicamente los otros y quedarian sin traducir.
            putAll(
                mapOf(
                    "fre" to "fr", "ger" to "de", "dut" to "nl", "gre" to "el",
                    "chi" to "zh", "cze" to "cs", "ice" to "is", "per" to "fa",
                    "rum" to "ro", "slo" to "sk", "alb" to "sq", "arm" to "hy",
                    "baq" to "eu", "bur" to "my", "geo" to "ka", "mac" to "mk",
                    "mao" to "mi", "may" to "ms", "tib" to "bo", "wel" to "cy",
                ),
            )
        }

        /** Nombre del idioma en español, aceptando codigos de 2 o 3 letras y con region ("es-ES"). */
        fun displayLanguage(raw: String): String {
            val base = raw.substringBefore('-').substringBefore('_').lowercase()
            val iso2 = if (base.length == 2) base else ISO3_TO_ISO2[base]
            val name = iso2?.let { Locale(it).getDisplayLanguage(Locale("es")) }.orEmpty()
            return name.ifBlank { raw }
        }
    }
}
