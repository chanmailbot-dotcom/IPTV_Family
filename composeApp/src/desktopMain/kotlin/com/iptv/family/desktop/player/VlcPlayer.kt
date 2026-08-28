package com.iptv.family.desktop.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iptv.family.shared.data.audio.AudioTrackPreference
import com.iptv.family.shared.log.AppLog
import com.sun.jna.NativeLibrary
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.log.LogLevel
import uk.co.caprica.vlcj.media.TrackType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.MediaPlayerComponent
import java.awt.Component
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
        // libvlc devuelve sus cadenas en UTF-8, pero JNA las decodifica con la
        // codificacion por defecto de la plataforma (Windows-1252 aqui), asi que
        // los nombres de pista llegaban como "Pista 2 - [EspaÃ±ol]" y no habia
        // forma de reconocer el idioma. Hay que fijarlo ANTES de cargar libvlc.
        System.setProperty("jna.encoding", "UTF-8")
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

    /** Volumen 0..100. Asignarlo lo aplica en libvlc (ver [changeVolume]). */
    var volume: Int
        get() = volumeState
        set(value) = changeVolume(value)

    var isMuted: Boolean
        get() = mutedState
        set(value) = changeMuted(value)

    /** Una pista de audio del canal, tal como la ve la UI. */
    data class AudioTrack(
        /** ID interno de libvlc, el que se pasa a `audio().setTrack()`. */
        val id: Int,
        val language: String?,
        val title: String?,
    ) {
        /** Etiqueta para el selector ("Español", "Audiodescripción", "Inglés"...). */
        val label: String get() = AudioTrackPreference.displayName(language, title)
    }

    /** Pistas de audio del canal en curso (vacio hasta que libvlc las descubre). */
    var audioTracks by mutableStateOf<List<AudioTrack>>(emptyList())
        private set

    /** ID de libvlc de la pista de audio activa, o null si no se sabe todavia. */
    var currentAudioTrackId by mutableStateOf<Int?>(null)
        private set

    /**
     * Hilo propio para hablar con libvlc desde fuera de sus callbacks.
     *
     * REGLA DE VLCJ: no se puede llamar a libvlc desde dentro de un manejador de
     * eventos nativo. libvlc tiene tomado un lock interno mientras despacha el
     * evento, asi que reentrar lo bloquea, y con el se bloquea todo lo demas que
     * quiera tocar el reproductor. Paso por aqui todo lo que sale de un evento.
     *
     * Esto no es teorico: `setTrack()` llamado desde `elementaryStreamAdded` dejo
     * el hilo de eventos clavado en `libvlc_audio_set_track`, y el siguiente
     * cambio de canal colgo la UI entera en `libvlc_media_player_set_media`.
     */
    private val playerCommands = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "vlc-commands").apply { isDaemon = true }
    }

    /** Ejecuta [block] fuera del hilo de eventos de libvlc. Ver [playerCommands]. */
    private fun offEventThread(what: String, block: () -> Unit) {
        if (released) return
        runCatching {
            playerCommands.execute {
                // Se vuelve a comprobar: entre encolar y ejecutar puede haberse
                // liberado el reproductor, y usarlo entonces revienta en nativo.
                if (released) return@execute
                runCatching(block).onFailure { AppLog.e("Vlc", "fallo en '$what'", it) }
            }
        }.onFailure { AppLog.e("Vlc", "no se pudo encolar '$what'", it) }
    }

    /** Como [offEventThread] pero [delayMs] mas tarde. */
    private fun offEventThreadDelayed(what: String, delayMs: Long, block: () -> Unit) {
        if (released) return
        runCatching {
            playerCommands.schedule(
                {
                    if (released) return@schedule
                    runCatching(block).onFailure { AppLog.e("Vlc", "fallo en '$what'", it) }
                },
                delayMs,
                TimeUnit.MILLISECONDS,
            )
        }.onFailure { AppLog.e("Vlc", "no se pudo programar '$what'", it) }
    }

    /**
     * Escribe volumen y mute en libvlc.
     *
     * libvlc crea la salida de audio (el "aout") de forma perezosa: no existe
     * hasta que el decodificador entrega las primeras muestras, y mientras no
     * existe TODA escritura de volumen/mute se descarta en silencio. El evento
     * `playing` llega antes de ese momento, asi que aplicarlo solo ahi no vale:
     * se perdia y el reproductor se quedaba mudo.
     *
     * Devuelve true si libvlc confirma el valor leyendolo de vuelta. Mientras no
     * hay aout, `volume()` responde -1 (y vlcj traduce ese -1 de `isMute` a true,
     * porque compara != 0), asi que ese -1 es la senal de "todavia no, reintenta".
     */
    private fun applyAudioSettings(momento: String): Boolean {
        runCatching {
            player.audio().setVolume(volumeState)
            player.audio().isMute = mutedState
        }.onFailure { AppLog.e("Vlc", "no se pudo aplicar volumen/mute", it); return false }
        val vol = runCatching { player.audio().volume() }.getOrNull() ?: -1
        val ok = vol >= 0
        AppLog.d(
            "Vlc",
            "audio ($momento): volumen=$vol mute=${runCatching { player.audio().isMute }.getOrNull()} " +
                if (ok) "-> aplicado" else "-> aun sin salida de audio, se reintenta"
        )
        return ok
    }

    /**
     * Insiste en aplicar volumen/mute hasta que libvlc lo acepta de verdad.
     *
     * No se sabe cuando aparece el aout: depende de lo que tarde el decodificador
     * con este canal. Se reintenta con esperas crecientes en vez de con un retardo
     * fijo, que en un canal lento fallaria igual. Se abandona a los ~8s: si no hay
     * salida de audio a esas alturas, el problema no es el volumen, y queda escrito
     * en el log para poder verlo.
     */
    private fun applyAudioUntilItSticks(url: String?, intento: Int) {
        if (currentUrl != url) return // se cambio de canal, esto ya no aplica
        if (applyAudioSettings("intento $intento")) return
        if (intento >= 6) {
            AppLog.w(
                "Vlc",
                "audio: libvlc sigue sin salida de audio tras $intento intentos; " +
                    "el canal puede no traer audio decodificable"
            )
            return
        }
        offEventThreadDelayed("reaplicar audio", 250L * intento) {
            applyAudioUntilItSticks(url, intento + 1)
        }
    }

    /**
     * Vuelve a LEER de libvlc el estado de audio, en vez de fiarse de lo que
     * creemos haberle escrito. Es la unica forma de distinguir "no se oye porque
     * esta en mute", "porque el volumen es 0" y "porque la pista activa no es la
     * que pedimos".
     */
    private fun logAudioState(momento: String) {
        val vol = runCatching { player.audio().volume() }.getOrNull()
        val mute = runCatching { player.audio().isMute }.getOrNull()
        val track = runCatching { player.audio().track() }.getOrNull()
        AppLog.d(
            "Vlc",
            "estado audio ($momento): libvlc volumen=$vol mute=$mute pista=$track " +
                "| esperado volumen=$volumeState mute=$mutedState"
        )
    }

    /** Cambia la pista de audio (la elige el usuario en el selector). */
    fun selectAudioTrack(id: Int) {
        // Se refleja ya en la UI y la llamada nativa va aparte: setTrack puede
        // tardar y no merece congelar el hilo de quien lo pide.
        currentAudioTrackId = id
        val label = audioTracks.firstOrNull { it.id == id }?.label
        offEventThread("setTrack($id)") {
            player.audio().setTrack(id)
            AppLog.d("Vlc", "pista de audio cambiada a $label (id=$id)")
            logAudioState("tras setTrack($id)")
        }
    }

    /**
     * Lee las pistas del medio y pone la mejor: en España es habitual que la
     * PRIMERA pista sea la audiodescripcion (idioma "qad"), asi que dejar la que
     * elige libvlc por defecto hace que se oiga al narrador describiendo la
     * escena en vez del audio normal. Ver [AudioTrackPreference].
     *
     * Se llama al empezar a reproducir y de nuevo un momento despues: con HLS las
     * pistas van apareciendo a medida que se analiza el stream, y en el primer
     * instante puede que solo se conozca una.
     *
     * OJO: tiene que ejecutarse fuera del hilo de eventos de libvlc. Los sitios
     * que la llaman lo hacen via [offEventThread]. [expectedUrl] es el canal que
     * estaba sonando cuando se encolo: si entretanto se cambio de canal, esto ya
     * no aplica y se descarta.
     */
    private fun refreshAudioTracks(autoSelect: Boolean, expectedUrl: String?) {
        if (currentUrl != expectedUrl) return
        // `audio().trackDescriptions()` y NO `media().info().audioTracks()`: lo
        // segundo son los datos estaticos del medio, que con un HLS en directo
        // vienen vacios (hace falta parsear el medio, y un directo no se parsea).
        // trackDescriptions() es la lista viva del reproductor, la que libvlc
        // conoce mientras suena.
        //
        // Trae un id y un texto tipo "Pista 1 - [Spanish]"; el idioma se saca de
        // los corchetes. El id -1 es la entrada "Desactivar" de libvlc, no es una
        // pista de verdad.
        val tracks = runCatching {
            player.audio().trackDescriptions()
                ?.filter { it.id() >= 0 }
                ?.map { desc ->
                    val text = desc.description().orEmpty()
                    AudioTrack(
                        id = desc.id(),
                        language = LANGUAGE_IN_BRACKETS.find(text)?.groupValues?.get(1)?.trim(),
                        title = text,
                    )
                }
        }.getOrNull().orEmpty()
        if (tracks.isEmpty()) return

        val changed = tracks != audioTracks
        if (changed) {
            AppLog.d(
                "Vlc",
                "audio: ${tracks.size} pista(s) -> " +
                    tracks.joinToString { "${it.label} (id=${it.id}, '${it.title}')" }
            )
        }
        audioTracks = tracks
        val activeId = runCatching { player.audio().track() }.getOrNull()
        currentAudioTrackId = activeId

        if (!autoSelect || !changed) return
        if (autoSelectedForUrl == currentUrl) return // ya se eligio para este canal

        val currentIndex = tracks.indexOfFirst { it.id == activeId }
        val shouldSwitch = AudioTrackPreference.shouldSwitch(
            tracks = tracks,
            currentIndex = currentIndex,
            language = { it.language },
            title = { it.title },
        )
        if (!shouldSwitch) {
            // Solo se da por decidido si habia de verdad entre que elegir. Con una
            // sola pista no hay decision, y marcarlo aqui hacia que al aparecer la
            // segunda (los ES de un HLS van llegando uno a uno) ya no se
            // reconsiderara nunca: se quedaba en la audiodescripcion.
            if (tracks.size >= 2) autoSelectedForUrl = currentUrl
            return
        }
        val best = AudioTrackPreference.preferred(tracks, { it.language }, { it.title }) ?: return
        AppLog.d(
            "Vlc",
            "audio: ${tracks.size} pistas (${tracks.joinToString { it.label }}); " +
                "cambiando a ${best.label} porque la activa no era la preferida"
        )
        autoSelectedForUrl = currentUrl
        selectAudioTrack(best.id)
    }

    /** URL para la que ya se hizo la seleccion automatica, para no repetirla. */
    private var autoSelectedForUrl: String? = null

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
                val url = currentUrl
                offEventThread("playing") {
                    applyAudioUntilItSticks(url, intento = 1)
                    refreshAudioTracks(autoSelect = true, expectedUrl = url)
                }
            }

            /**
             * Las pistas de un HLS no estan todas al empezar: van apareciendo al
             * analizar el stream. Este evento avisa de cada una, y es donde de
             * verdad se puede elegir el español (en `playing` a veces solo se
             * conoce la primera pista, que es justo la audiodescripcion).
             */
            override fun elementaryStreamAdded(mp: MediaPlayer, type: TrackType, id: Int) {
                if (type != TrackType.AUDIO) return
                val url = currentUrl
                offEventThread("esAdded") { refreshAudioTracks(autoSelect = true, expectedUrl = url) }
            }

            override fun elementaryStreamDeleted(mp: MediaPlayer, type: TrackType, id: Int) {
                if (type != TrackType.AUDIO) return
                val url = currentUrl
                offEventThread("esDeleted") { refreshAudioTracks(autoSelect = false, expectedUrl = url) }
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
        // Las pistas del canal anterior no valen para el nuevo.
        audioTracks = emptyList()
        currentAudioTrackId = null
        autoSelectedForUrl = null
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
        volumeState = value.coerceIn(0, 100)
        if (volumeState > 0) mutedState = false
        offEventThread("volumen=$volumeState") { applyAudioSettings("cambio de volumen") }
    }

    fun changeMuted(value: Boolean) {
        mutedState = value
        offEventThread("mute=$value") { applyAudioSettings("cambio de mute") }
    }

    /** Idempotente: liberar dos veces el componente nativo puede abortar la JVM. */
    fun release() {
        if (released) return
        released = true
        // Antes de soltar el reproductor: si queda un comando encolado, se
        // ejecutaria contra un player ya liberado (crash nativo).
        runCatching { playerCommands.shutdownNow() }
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
        /**
         * libvlc describe las pistas como "Pista 1 - [Spanish]" o "Audio - [spa]":
         * de ahi se saca el codigo/nombre de idioma para poder puntuarlas.
         */
        val LANGUAGE_IN_BRACKETS = Regex("""\[([^\]]+)\]""")

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
