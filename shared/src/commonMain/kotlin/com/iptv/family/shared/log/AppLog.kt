package com.iptv.family.shared.log

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Logger de archivo plano pensado para poder auditar qué pasó (añadir lista,
 * cargar canales, reproducir) sin depender de que el usuario describa lo que ve.
 *
 * Cada línea también sale por stdout, así que en desarrollo (`gradlew :composeApp:run`)
 * aparece en la consola además de en el archivo. El archivo persiste entre sesiones
 * en `<datos de usuario>/logs/app.log`.
 */
object AppLog {
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val startFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    @Volatile private var file: File? = null

    /** Debe llamarse una vez al arrancar, antes de cualquier otra operación relevante. */
    fun init(directory: File) {
        directory.mkdirs()
        val target = File(directory, "app.log")
        // Log acumulado de varias sesiones: lo recortamos si crece demasiado.
        if (target.exists() && target.length() > 2_000_000) target.writeText("")
        file = target
        raw("==== Arranque ${startFormat.format(Date())} ====")
    }

    fun d(tag: String, message: String) = write("D", tag, message)
    fun w(tag: String, message: String) = write("W", tag, message)

    fun e(tag: String, message: String, error: Throwable? = null) =
        write("E", tag, message + (error?.let { " · ${it::class.simpleName}: ${it.message}" }.orEmpty()))

    /**
     * Oculta credenciales antes de loguear una URL. Los paneles Xtream meten
     * usuario/contraseña en el propio path (`/live/USER/PASS/id.ts`), y algunas
     * URL M3U los llevan como query param.
     */
    fun redactUrl(url: String): String {
        var out = url.replace(Regex("(?i)(password=)[^&]*"), "$1***")
        out = out.replace(Regex("(?i)((?:/live/|/movie/|/vod/|/series/)[^/]+/)[^/]+"), "$1***")
        out = out.replace(Regex("(?i)(username=)[^&]*"), "$1***")
        return out
    }

    private fun write(level: String, tag: String, message: String) {
        raw("${timeFormat.format(Date())} $level/$tag: $message")
    }

    private fun raw(line: String) {
        println(line)
        val f = file ?: return
        runCatching { f.appendText(line + "\n") }
    }
}
