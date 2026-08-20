package com.iptv.family.shared.data.store

import java.io.File

/**
 * Almacén clave-valor persistente.
 * En desktop usa archivos del directorio de usuario; en Android
 * podrá apuntar a los archivos internos de la app.
 */
interface KeyValueStore {
    fun write(key: String, value: String)
    fun read(key: String): String?
    fun delete(key: String)
}

/**
 * Implementación sobre sistema de archivos (JVM).
 */
class FileKeyValueStore(private val dir: File) : KeyValueStore {

    init {
        dir.mkdirs()
    }

    override fun write(key: String, value: String) {
        val target = File(dir, sanitize(key))
        target.writeText(value)
    }

    override fun read(key: String): String? {
        val target = File(dir, sanitize(key))
        return if (target.exists()) target.readText() else null
    }

    override fun delete(key: String) {
        File(dir, sanitize(key)).delete()
    }

    private fun sanitize(key: String): String =
        key.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}