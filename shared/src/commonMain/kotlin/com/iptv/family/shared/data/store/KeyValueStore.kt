package com.iptv.family.shared.data.store

import com.iptv.family.shared.log.AppLog
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Almacén clave-valor persistente.
 * En desktop usa archivos del directorio de usuario; en Android
 * podrá apuntar a los archivos internos de la app.
 */
interface KeyValueStore {
    fun write(key: String, value: String)
    fun read(key: String): String?
    fun delete(key: String)

    /**
     * Contenido de la escritura ANTERIOR, si se conserva. Sirve para recuperarse
     * de un guardado que quedo a medias. Un almacen que no guarde copia devuelve
     * null y quien lo llama sigue funcionando igual.
     */
    fun readBackup(key: String): String? = null

    /**
     * Aparta un contenido ilegible (renombrandolo) en vez de dejar que el
     * siguiente guardado lo pise. Sin esto, un fichero corrupto se lee como
     * "vacio" y a la primera escritura se pierde para siempre.
     */
    fun quarantine(key: String) {}
}

/**
 * Implementación sobre sistema de archivos (JVM).
 *
 * Escribe de forma ATOMICA: contenido a un temporal, se fuerza a disco y se
 * renombra sobre el destino. Antes se hacia `writeText` directo, asi que un
 * corte de luz, un cierre forzado o un disco lleno a media escritura dejaban el
 * JSON truncado; al arrancar se leia como lista vacia y el siguiente guardado
 * lo sobrescribia. Resultado: listas y credenciales perdidas sin rastro.
 */
class FileKeyValueStore(private val dir: File) : KeyValueStore {

    init {
        dir.mkdirs()
    }

    override fun write(key: String, value: String) {
        val name = sanitize(key)
        // Dos escrituras de la MISMA clave a la vez se pisaban: ambas usaban un
        // temporal con el mismo nombre, la primera lo renombraba y la segunda se
        // quedaba sin fichero que mover (NoSuchFileException) o, peor, mezclaba
        // contenidos. Pasa de verdad: basta con que dos pantallas pidan cargar la
        // misma lista a la vez. Se arregla por partida doble -- un temporal
        // distinto por escritura y un cerrojo por clave.
        synchronized(lockFor(name)) {
            val target = File(dir, name)
            val tmp = File.createTempFile("$name-", ".tmp", dir)

            try {
                // 1) Contenido completo en el temporal, forzado a disco. Sin el fsync,
                //    el renombrado puede llegar antes que los datos y quedar vacio.
                FileOutputStream(tmp).use { out ->
                    out.write(value.toByteArray(Charsets.UTF_8))
                    out.flush()
                    out.fd.sync()
                }

                // 2) La version buena anterior se guarda antes de pisarla.
                if (target.isFile) {
                    runCatching { target.copyTo(File(dir, "$name.bak"), overwrite = true) }
                }

                // 3) Renombrado: atomico donde el sistema lo permita.
                runCatching {
                    Files.move(
                        tmp.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
                    )
                }.recoverCatching {
                    if (it is AtomicMoveNotSupportedException) {
                        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    } else {
                        throw it
                    }
                }.onFailure {
                    AppLog.e("Store", "no se pudo guardar '$name'", it)
                    throw it
                }
            } finally {
                // Si algo fallo antes del renombrado, el temporal no se queda ahi
                // acumulandose escritura tras escritura.
                if (tmp.exists()) tmp.delete()
            }
        }
    }

    /** Un cerrojo por clave: dos claves distintas se pueden escribir a la vez. */
    private fun lockFor(name: String): Any = locks.computeIfAbsent(name) { Any() }

    override fun read(key: String): String? {
        val target = File(dir, sanitize(key))
        return if (target.isFile) target.readText() else null
    }

    override fun readBackup(key: String): String? {
        val backup = File(dir, "${sanitize(key)}.bak")
        return if (backup.isFile) backup.readText() else null
    }

    override fun quarantine(key: String) {
        val name = sanitize(key)
        val target = File(dir, name)
        if (!target.isFile) return
        // El nombre lleva marca de tiempo para no pisar cuarentenas anteriores.
        val aside = File(dir, "$name.corrupto-${System.currentTimeMillis()}")
        val moved = runCatching { target.renameTo(aside) }.getOrDefault(false)
        AppLog.w(
            "Store",
            if (moved) "'$name' era ilegible; se aparta en '${aside.name}' para no perderlo"
            else "'$name' era ilegible y NO se pudo apartar",
        )
    }

    override fun delete(key: String) {
        val name = sanitize(key)
        File(dir, name).delete()
        File(dir, "$name.bak").delete()
    }

    private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    private fun sanitize(key: String): String =
        key.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}
