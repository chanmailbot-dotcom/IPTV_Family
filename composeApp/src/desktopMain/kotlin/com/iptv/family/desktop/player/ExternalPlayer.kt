package com.iptv.family.desktop.player

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.net.URI

/**
 * Reproduce streams en el escritorio lanzando un reproductor externo instalado
 * (mpv, vlc, ffplay, mplayer) o abriendo el URL con el reproductor del sistema.
 *
 * NOTA: ExoPlayer / Media3 son Android-only; en escritorio se delega al sistema.
 */
object ExternalPlayer {
    private val candidates = listOf(
        "mpv", "vlc", "mpv.com", "vlc.exe", "ffplay", "mplayer", "mplayer.exe", "potplay"
    )

    fun play(url: String) {
        if (url.isBlank()) return
        for (cmd in candidates) {
            val path = find(cmd)
            if (path != null) {
                try {
                    ProcessBuilder(cmd, url)
                        .redirectErrorStream(true)
                        .start()
                    return
                } catch (e: Exception) {
                    /* intentar siguiente candidato */
                }
            }
        }
        openInBrowser(url)
    }

    /** Abre un diálogo nativo para seleccionar un archivo .m3u/.m3u8 local. */
    fun chooseM3uFile(): String? = try {
        val frame = Frame("")
        frame.isVisible = false
        val dialog = FileDialog(frame, "Selecciona lista M3U", FileDialog.LOAD)
        dialog.isVisible = true
        val file = dialog.file
        frame.dispose()
        if (file != null) (dialog.directory ?: "") + file else null
    } catch (e: Exception) {
        null
    }

    private fun find(cmd: String): String? = try {
        val p = ProcessBuilder("which", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readLine() }
        p.waitFor()
        if (p.exitValue() == 0 && out.isNotBlank()) out else null
    } catch (e: Exception) {
        null
    }

    private fun openInBrowser(url: String) {
        try {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(URI(url))
            } else {
                val os = System.getProperty("os.name").lowercase()
                if (os.contains("win")) Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler $url")
                else ProcessBuilder("xdg-open", url).start()
            }
        } catch (e: Exception) {
            /* no se pudo abrir */
        }
    }
}
