package com.iptv.family.desktop.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iptv.family.shared.log.AppLog
import com.sun.jna.NativeLibrary
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.log.LogLevel
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.MediaPlayerComponent
import java.awt.Component
import java.io.File

/**
 * Localizacion del runtime nativo de libvlc.
 *
 * En la app empaquetada, VLC viaja dentro del instalador y aparece en
 * `<resources>/vlc`. En desarrollo se usa el VLC instalado en el sistema.
 */
object VlcNative {

    /** true si libvlc se pudo cargar; si es false no hay reproduccion posible. */
    val isAvailable: Boolean by lazy { discover() }

    private fun discover(): Boolean = try {
        val dir = bundledDir()
        AppLog.d("Vlc", "discover: bundledDir=$dir")
        dir?.let {
            // El nombre de la libreria cambia por plataforma; registramos ambos.
            NativeLibrary.addSearchPath("libvlc", it)
            NativeLibrary.addSearchPath("vlc", it)
            // libvlc busca su carpeta `plugins` junto al binario.
            System.setProperty("VLC_PLUGIN_PATH", File(it, "plugins").absolutePath)
        }
        val found = NativeDiscovery().discover()
        AppLog.d("Vlc", "discover: libvlc encontrado=$found")
        found
    } catch (e: Throwable) {
        AppLog.e("Vlc", "discover: excepción", e)
        false
    }

    private fun bundledDir(): String? {
        val resources = System.getProperty("compose.application.resources.dir") ?: return null
        val vlc = File(resources, "vlc")
        return if (File(vlc, "plugins").isDirectory) vlc.absolutePath else null
    }
}

/**
 * Envuelve un reproductor de libvlc y expone su estado como estado de Compose.
 *
 * Se crea uno solo por ventana y se reutiliza al cambiar de canal: crear un
 * componente por canal filtra memoria nativa.
 */
class VlcController(compatibilityMode: Boolean = false) {

    /**
     * Componente AWT donde libvlc pinta el video. Se inserta con SwingPanel.
     *
     * "embedded" deja que libvlc pinte directo en la ventana nativa (rapido, es el
     * modo normal en Windows y Linux). "callback" copia cada fotograma a un
     * BufferedImage que pinta Swing: mas lento pero funciona donde el embebido no,
     * y es obligatorio en macOS.
     *
     * El usuario lo cambia en Ajustes ("modo compatibilidad"); -Diptv.video.mode
     * lo fuerza desde la linea de comandos y gana sobre el ajuste.
     */
    val component: Component =
        if (useCallbackMode(compatibilityMode)) CallbackMediaPlayerComponent() else EmbeddedMediaPlayerComponent()

    /** [component] siempre implementa esto; solo su tipo AWT concreto varia. */
    private val mpComponent: MediaPlayerComponent = component as MediaPlayerComponent

    private val player: MediaPlayer = when (val c = component) {
        is CallbackMediaPlayerComponent -> c.mediaPlayer()
        is EmbeddedMediaPlayerComponent -> c.mediaPlayer()
        else -> error("Componente de video no reconocido: ${c.javaClass.name}")
    }

    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var currentUrl by mutableStateOf<String?>(null)

    private var volumeState by mutableStateOf(80)
    private var mutedState by mutableStateOf(false)

    /** Volumen 0..100. Asignarlo lo aplica en libvlc al momento. */
    var volume: Int
        get() = volumeState
        set(value) {
            volumeState = value.coerceIn(0, 100)
            player.audio().setVolume(volumeState)
            if (volumeState > 0 && mutedState) isMuted = false
        }

    var isMuted: Boolean
        get() = mutedState
        set(value) {
            mutedState = value
            player.audio().isMute = value
        }

    /**
     * Ultimas lineas de log nativas de libvlc (WARNING/ERROR), para poder mostrar la
     * causa real de un fallo: el evento `error()` de vlcj no trae motivo, solo avisa
     * de que algo fue mal.
     */
    private val recentNativeLog = ArrayDeque<String>()
    private val nativeLog = runCatching { mpComponent.mediaPlayerFactory().application().newLog() }.getOrNull()

    init {
        AppLog.d("Vlc", "nativeLog disponible=${nativeLog != null}")
        nativeLog?.apply {
            setLevel(LogLevel.WARNING)
            addLogListener { level, module, _, _, _, _, _, message ->
                if (level == LogLevel.WARNING || level == LogLevel.ERROR) {
                    // Cada linea WARNING/ERROR de libvlc va al log completo (no solo la
                    // usada para el mensaje de error): la causa real suele estar unas
                    // lineas antes del evento error() (p.ej. fallo de conexion, 403...).
                    AppLog.w("VlcNative", "[$module] $message")
                    synchronized(recentNativeLog) {
                        recentNativeLog.addLast(message)
                        if (recentNativeLog.size > 5) recentNativeLog.removeFirst()
                    }
                }
            }
        }

        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun opening(mp: MediaPlayer) {
                AppLog.d("Vlc", "opening: ${AppLog.redactUrl(currentUrl.orEmpty())}")
            }

            override fun playing(mp: MediaPlayer) {
                AppLog.d("Vlc", "playing: ${AppLog.redactUrl(currentUrl.orEmpty())}")
                isPlaying = true
                isBuffering = false
                error = null
                // libvlc ignora setVolume/isMute mientras no hay medio abierto: el
                // valor puesto en init o antes del play() se perdia y el volumen
                // volvia al de la libreria. Se reaplica aqui, que es el primer
                // momento en que el reproductor lo acepta de verdad.
                runCatching {
                    mp.audio().setVolume(volumeState)
                    mp.audio().isMute = mutedState
                }
            }

            override fun paused(mp: MediaPlayer) {
                AppLog.d("Vlc", "paused")
                isPlaying = false
            }

            override fun stopped(mp: MediaPlayer) {
                AppLog.d("Vlc", "stopped")
                isPlaying = false
                isBuffering = false
            }

            override fun buffering(mp: MediaPlayer, newCache: Float) {
                isBuffering = newCache < 100f
            }

            override fun finished(mp: MediaPlayer) {
                AppLog.w("Vlc", "finished (el stream terminó solo, sin error explícito)")
            }

            override fun error(mp: MediaPlayer) {
                isPlaying = false
                isBuffering = false
                val detail = synchronized(recentNativeLog) { recentNativeLog.lastOrNull() }
                AppLog.e("Vlc", "error al reproducir ${AppLog.redactUrl(currentUrl.orEmpty())}: $detail")
                error = if (detail != null) {
                    "No se pudo abrir el canal: $detail"
                } else {
                    "No se pudo abrir el canal. Comprueba que la lista sigue activa."
                }
            }
        })
        player.audio().setVolume(volumeState)
    }

    /** true cuando la superficie de video ya esta en pantalla y acepta un stream. */
    val isSurfaceReady: Boolean get() = component.isDisplayable

    /**
     * Abre [url] (URL de origen; es la identidad del canal en toda la app).
     * Si [playbackUrl] no es null, libvlc reproduce ESA url en su lugar (p.ej. el
     * multiplexor local `http://127.0.0.1/puerto/stream/current.m3u8`) mientras
     * [url] sigue siendo la referencia para zapeo, historial y el proxy web.
     *
     * Requiere que la superficie de video ya este en pantalla: libvlc lanza
     * "video surface component must be displayable" si se le pide antes.
     */
    fun play(
        url: String,
        networkCachingMs: Int = 15_000,
        hardwareDecoding: Boolean = true,
        playbackUrl: String? = null,
    ) {
        if (url.isBlank()) return
        val media = playbackUrl?.takeIf { it.isNotBlank() } ?: url
        AppLog.d(
            "Vlc",
            "play: ${AppLog.redactUrl(url)} via ${AppLog.redactUrl(media)} (surfaceReady=$isSurfaceReady, caching=${networkCachingMs}ms, hw=$hardwareDecoding)"
        )
        error = null
        isBuffering = true
        currentUrl = url
        // Se recuerdan los argumentos para poder volver a arrancar este mismo canal
        // despues de un stop() (ver togglePlayPause).
        lastPlayArgs = PlayArgs(url, networkCachingMs, hardwareDecoding, playbackUrl)
        val options = mutableListOf(":network-caching=$networkCachingMs")
        if (!hardwareDecoding) options += ":avcodec-hw=none"
        runCatching { player.media().play(media, *options.toTypedArray()) }
            .onFailure {
                AppLog.e("Vlc", "play: fallo al invocar player.media().play()", it)
                isBuffering = false
                error = "No se pudo iniciar la reproducción: ${it.message ?: it::class.simpleName}"
            }
    }

    fun togglePlayPause() {
        if (isPlaying) {
            player.controls().pause()
            return
        }
        // Tras un stop() libvlc ya no tiene medio: `controls().play()` no hace nada.
        // Antes esto dejaba el reproductor muerto (el boton de play no respondia y
        // habia que cambiar de canal para recuperarlo); ahora se vuelve a abrir el
        // ultimo canal con los mismos ajustes.
        if (currentUrl == null) {
            val args = lastPlayArgs ?: return
            play(args.url, args.networkCachingMs, args.hardwareDecoding, args.playbackUrl)
            return
        }
        // Reanuda tras una pausa: en directo, libvlc sigue por donde iba el buffer.
        player.controls().play()
    }

    fun stop() {
        player.controls().stop()
        currentUrl = null
        isPlaying = false
        isBuffering = false
    }

    private data class PlayArgs(
        val url: String,
        val networkCachingMs: Int,
        val hardwareDecoding: Boolean,
        val playbackUrl: String?,
    )

    private var lastPlayArgs: PlayArgs? = null

    fun changeVolume(value: Int) {
        volume = value.coerceIn(0, 100)
        player.audio().setVolume(volume)
        if (volume > 0 && isMuted) changeMuted(false)
    }

    fun changeMuted(value: Boolean) {
        isMuted = value
        player.audio().isMute = value
    }

    /** Idempotente: liberar dos veces el componente nativo puede abortar la JVM. */
    fun release() {
        if (released) return
        released = true
        runCatching { player.controls().stop() }
        runCatching { nativeLog?.release() }
        runCatching {
            when (val c = component) {
                is CallbackMediaPlayerComponent -> c.release()
                is EmbeddedMediaPlayerComponent -> c.release()
            }
        }
    }

    private var released = false

    private companion object {
        val isMacOs: Boolean
            get() = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

        fun useCallbackMode(compatibilityMode: Boolean): Boolean =
            when (System.getProperty("iptv.video.mode")?.lowercase()) {
                "callback" -> true
                "embedded" -> false
                else -> compatibilityMode || isMacOs
            }
    }
}
