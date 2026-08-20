package com.iptv.family.desktop.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sun.jna.NativeLibrary
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
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
        bundledDir()?.let { dir ->
            // El nombre de la libreria cambia por plataforma; registramos ambos.
            NativeLibrary.addSearchPath("libvlc", dir)
            NativeLibrary.addSearchPath("vlc", dir)
            // libvlc busca su carpeta `plugins` junto al binario.
            System.setProperty("VLC_PLUGIN_PATH", File(dir, "plugins").absolutePath)
        }
        NativeDiscovery().discover()
    } catch (e: Throwable) {
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
        private set
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

    init {
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mp: MediaPlayer) {
                isPlaying = true
                isBuffering = false
                error = null
            }

            override fun paused(mp: MediaPlayer) {
                isPlaying = false
            }

            override fun stopped(mp: MediaPlayer) {
                isPlaying = false
                isBuffering = false
            }

            override fun buffering(mp: MediaPlayer, newCache: Float) {
                isBuffering = newCache < 100f
            }

            override fun error(mp: MediaPlayer) {
                isPlaying = false
                isBuffering = false
                error = "No se pudo abrir el canal. Comprueba que la lista sigue activa."
            }
        })
        player.audio().setVolume(volumeState)
    }

    /** true cuando la superficie de video ya esta en pantalla y acepta un stream. */
    val isSurfaceReady: Boolean get() = component.isDisplayable

    /**
     * Abre [url]. [networkCachingMs] es el buffer de red, en milisegundos.
     *
     * Requiere que la superficie de video ya este en pantalla: libvlc lanza
     * "video surface component must be displayable" si se le pide antes.
     */
    fun play(url: String, networkCachingMs: Int = 15_000, hardwareDecoding: Boolean = true) {
        if (url.isBlank()) return
        error = null
        isBuffering = true
        currentUrl = url
        val options = mutableListOf(":network-caching=$networkCachingMs")
        if (!hardwareDecoding) options += ":avcodec-hw=none"
        runCatching { player.media().play(url, *options.toTypedArray()) }
            .onFailure {
                isBuffering = false
                error = "No se pudo iniciar la reproducción: ${it.message ?: it::class.simpleName}"
            }
    }

    fun togglePlayPause() {
        if (currentUrl == null) return
        player.controls().pause()
    }

    fun stop() {
        player.controls().stop()
        currentUrl = null
        isPlaying = false
        isBuffering = false
    }

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
