package com.iptv.family.desktop.remote

import com.iptv.family.shared.log.AppLog
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Convierte a AAC el audio que los navegadores no pueden reproducir.
 *
 * El problema: muchisimos canales IPTV emiten el audio en AC-3/E-AC-3, MP2 o
 * DTS. VLC en el escritorio los decodifica sin problema, pero ningun navegador
 * puede (no estan en la lista de codecs de Media Source Extensions), asi que en
 * la web se veia la imagen sin sonido.
 *
 * La solucion: ffmpeg re-empaqueta el canal a HLS **copiando el video tal cual**
 * (`-c:v copy`, sin recodificar: es casi gratis en CPU) y recodificando solo el
 * audio a AAC. El navegador consume esa salida y ya se oye.
 *
 * Dos decisiones importantes:
 *
 * 1. **ffmpeg lee del mux local**, no del panel. Si abriera su propia conexion al
 *    proveedor, tendriamos tres clientes contra un panel que permite uno solo --
 *    exactamente el problema que [StreamProxy] existe para evitar. Leyendo de
 *    `http://127.0.0.1:puerto/stream/...` es un consumidor mas del mismo mux.
 * 2. **Solo se transcodifica cuando hace falta.** Si el canal ya trae AAC (o MP3),
 *    montar un ffmpeg por medio solo añadiria latencia y CPU para nada.
 */
class AudioTranscoder(
    private val ffmpegPath: String,
    private val workDir: File,
) {

    /** Una sesion de transcodificado en marcha, por canal. */
    private class Session(
        val channelId: String,
        val process: Process,
        val dir: File,
        @Volatile var lastAccessAt: Long,
    )

    @Volatile
    private var session: Session? = null
    private val lock = Any()

    /** Nombre de la lista que genera ffmpeg dentro de su carpeta de trabajo. */
    private val playlistName = "audio-aac.m3u8"

    /**
     * Asegura que hay un ffmpeg convirtiendo [sourceUrl] para [channelId] y
     * devuelve el fichero de la lista HLS resultante, o null si no se pudo
     * arrancar. Idempotente: si ya hay uno para este canal, lo reutiliza.
     */
    fun playlistFor(channelId: String, sourceUrl: String): File? {
        synchronized(lock) {
            val current = session
            if (current != null && current.channelId == channelId && current.process.isAlive) {
                current.lastAccessAt = System.currentTimeMillis()
                return File(current.dir, playlistName)
            }
            // Cambio de canal (o el proceso murio): fuera el anterior. Solo se
            // mantiene un ffmpeg vivo -- se transcodifica lo que se esta viendo.
            stopLocked()
            return startLocked(channelId, sourceUrl)
        }
    }

    private fun startLocked(channelId: String, sourceUrl: String): File? {
        val dir = File(workDir, "ch-$channelId").apply {
            deleteRecursively()
            mkdirs()
        }
        val playlist = File(dir, playlistName)

        val command = listOf(
            ffmpegPath,
            "-hide_banner",
            "-loglevel", "warning",
            // Reconecta si el mux tiene un hipo, en vez de morir y dejar la web muda.
            "-reconnect", "1",
            "-reconnect_streamed", "1",
            "-reconnect_delay_max", "5",
            // El demuxer HLS de ffmpeg rechaza segmentos cuya URL no acaba en una
            // extension conocida, y las nuestras son `/stream/segment?src=...`.
            // Sin esto falla con "not in allowed_segment_extensions" / "Invalid
            // data found when processing input" y la conversion nunca arranca.
            "-allowed_extensions", "ALL",
            "-extension_picky", "0",
            "-i", sourceUrl,
            // El video se copia SIN recodificar: es lo que mantiene el coste de CPU
            // en algo despreciable. Solo el audio se convierte.
            "-c:v", "copy",
            "-c:a", "aac",
            "-b:a", "160k",
            "-ac", "2",
            // Descarta subtitulos y pistas de datos: el navegador no las va a usar y
            // algunas (teletexto en DVB) hacen fallar al muxer de HLS.
            "-sn", "-dn",
            // Solo la primera pista de video y la primera de audio.
            "-map", "0:v:0", "-map", "0:a:0?",
            "-f", "hls",
            "-hls_time", "4",
            // Ventana corta y borrado de segmentos viejos: es directo, no hace falta
            // guardar historial, y asi la carpeta temporal no crece sin control.
            "-hls_list_size", "6",
            "-hls_flags", "delete_segments+omit_endlist+independent_segments",
            "-hls_segment_type", "mpegts",
            "-hls_segment_filename", File(dir, "seg-%05d.ts").absolutePath,
            playlist.absolutePath,
        )

        AppLog.d("Transcoder", "arrancando ffmpeg para canal $channelId")
        val process = runCatching {
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(File(dir, "ffmpeg.log"))
                .start()
        }.getOrElse {
            AppLog.e("Transcoder", "no se pudo arrancar ffmpeg", it)
            return null
        }

        session = Session(channelId, process, dir, System.currentTimeMillis())

        // ffmpeg tarda un poco en escribir la primera lista con segmentos: se espera
        // a que exista para no devolver un 404 al navegador en el primer intento.
        val deadline = System.currentTimeMillis() + START_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive) {
                AppLog.e(
                    "Transcoder",
                    "ffmpeg murio al arrancar (codigo ${process.exitValue()}): ${tailOfLog(dir)}"
                )
                return null
            }
            // Con al menos un segmento listado ya se puede empezar a reproducir.
            if (playlist.isFile && playlist.readText().contains(".ts")) {
                AppLog.d("Transcoder", "ffmpeg listo para canal $channelId")
                return playlist
            }
            Thread.sleep(200)
        }
        AppLog.w("Transcoder", "ffmpeg no produjo lista en ${START_TIMEOUT_MS}ms: ${tailOfLog(dir)}")
        return null
    }

    /**
     * Fichero de un segmento por nombre, dentro de la carpeta de la sesion activa.
     *
     * Solo se acepta el nombre "pelado" (sin barras ni "..") para que una peticion
     * como `/stream/aac/../../settings.json` no pueda salirse de la carpeta de
     * trabajo y leer cualquier fichero del disco.
     */
    fun segmentFile(name: String): File? {
        if (name.isBlank() || name.contains('/') || name.contains('\\') || name.contains("..")) return null
        val current = session ?: return null
        current.lastAccessAt = System.currentTimeMillis()
        val file = File(current.dir, name)
        // Doble comprobacion: el fichero resuelto tiene que seguir dentro del
        // directorio de la sesion.
        val parent = current.dir.canonicalFile
        return file.takeIf { it.canonicalFile.parentFile == parent }
    }

    /** Cierra el transcodificado si nadie lo ha pedido en un rato. */
    fun stopIfIdle(idleMs: Long = IDLE_TIMEOUT_MS) {
        synchronized(lock) {
            val current = session ?: return
            if (System.currentTimeMillis() - current.lastAccessAt > idleMs) {
                AppLog.d("Transcoder", "sin uso, parando ffmpeg del canal ${current.channelId}")
                stopLocked()
            }
        }
    }

    fun stop() = synchronized(lock) { stopLocked() }

    private fun stopLocked() {
        val current = session ?: return
        session = null
        runCatching {
            current.process.destroy()
            if (!current.process.waitFor(3, TimeUnit.SECONDS)) current.process.destroyForcibly()
        }
        runCatching { current.dir.deleteRecursively() }
    }

    private fun tailOfLog(dir: File): String = runCatching {
        File(dir, "ffmpeg.log").readLines().takeLast(6).joinToString(" | ")
    }.getOrDefault("(sin log)")

    companion object {
        /** Margen para que ffmpeg conecte y saque el primer segmento. */
        const val START_TIMEOUT_MS = 20_000L

        /** Sin peticiones durante este tiempo, se mata el proceso. */
        const val IDLE_TIMEOUT_MS = 60_000L

        /** Codecs de audio que los navegadores SI pueden reproducir en HLS. */
        private val BROWSER_AUDIO_CODECS = setOf("aac", "mp3", "mp4a")

        /**
         * Localiza ffmpeg: primero la ruta que haya configurado el usuario, si no
         * el del PATH. Devuelve null si no hay ninguno usable.
         */
        fun resolveFfmpeg(configured: String?): String? {
            val candidates = listOfNotNull(configured?.trim()?.takeIf { it.isNotEmpty() }, "ffmpeg")
            for (candidate in candidates) {
                val works = runCatching {
                    val p = ProcessBuilder(candidate, "-version")
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start()
                    p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0
                }.getOrDefault(false)
                if (works) return candidate
            }
            return null
        }

        /**
         * Codec de audio del stream, via ffprobe (que viene con ffmpeg). Devuelve
         * null si no se puede averiguar -- en ese caso NO se transcodifica, para no
         * meter un ffmpeg por medio a ciegas.
         */
        fun probeAudioCodec(ffmpegPath: String, url: String): String? {
            val ffprobe = ffmpegPath.replace(Regex("ffmpeg(\\.exe)?$", RegexOption.IGNORE_CASE)) {
                if (it.value.endsWith(".exe", ignoreCase = true)) "ffprobe.exe" else "ffprobe"
            }
            val command = listOf(
                ffprobe, "-hide_banner", "-v", "error",
                // Igual que en el transcodificado: nuestras URLs de segmento no
                // acaban en .ts y el demuxer HLS las rechazaria.
                "-allowed_extensions", "ALL",
                "-extension_picky", "0",
                // Se piden TODAS las pistas y se busca la de audio aqui, en vez de
                // usar `-select_streams a:0`: con una fuente HLS, ese selector se
                // resuelve antes de terminar el sondeo y devolvia vacio siempre
                // (por eso el codec salia "desconocido" y nunca se transcodificaba).
                "-show_entries", "stream=codec_type,codec_name",
                "-of", "default=noprint_wrappers=1",
                "-analyzeduration", "6000000", "-probesize", "6000000",
                url,
            )
            return runCatching {
                val process = ProcessBuilder(command).redirectErrorStream(false).start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                if (!process.waitFor(25, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return null
                }
                // La salida son pares por pista:
                //   codec_name=h264 / codec_type=video / codec_name=aac / codec_type=audio
                var lastName: String? = null
                for (line in output.lineSequence()) {
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("codec_name=") -> lastName = trimmed.substringAfter('=').lowercase()
                        trimmed == "codec_type=audio" -> return@runCatching lastName
                    }
                }
                null
            }.getOrNull()
        }

        /** true si ese codec de audio necesita conversion para oirse en un navegador. */
        fun needsTranscode(audioCodec: String?): Boolean {
            val codec = audioCodec?.lowercase() ?: return false // desconocido: no tocar
            return BROWSER_AUDIO_CODECS.none { codec.startsWith(it) }
        }
    }
}
