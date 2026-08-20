package com.iptv.family.desktop.player

import java.awt.FileDialog
import java.awt.Frame

/** Dialogo nativo para elegir una lista .m3u guardada en el equipo. */
object FilePicker {

    fun chooseM3uFile(): String? = try {
        val owner = Frame().apply { isVisible = false }
        val dialog = FileDialog(owner, "Selecciona una lista M3U", FileDialog.LOAD).apply {
            setFilenameFilter { _, fileName ->
                fileName.endsWith(".m3u", true) || fileName.endsWith(".m3u8", true)
            }
            isVisible = true
        }
        val chosen = dialog.file?.let { (dialog.directory ?: "") + it }
        owner.dispose()
        chosen
    } catch (e: Exception) {
        null
    }
}
