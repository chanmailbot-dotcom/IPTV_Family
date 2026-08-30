package com.iptv.family.shared.data.store

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El guardado tiene que ser atomico y conservar la version anterior.
 *
 * Antes se hacia `writeText` directo: un corte a media escritura dejaba el JSON
 * truncado, al arrancar se leia como vacio y el siguiente guardado lo pisaba.
 * Se perdian las listas y las credenciales de Xtream sin ningun aviso.
 */
class FileKeyValueStoreTest {

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "kvs-${System.nanoTime()}").apply { mkdirs() }

    @Test
    fun `guarda y lee`() {
        val store = FileKeyValueStore(tempDir())
        store.write("a.json", "{\"x\":1}")
        assertEquals("{\"x\":1}", store.read("a.json"))
    }

    @Test
    fun `no deja ficheros temporales sueltos`() {
        val dir = tempDir()
        val store = FileKeyValueStore(dir)
        store.write("a.json", "uno")
        store.write("a.json", "dos")
        val sobrantes = dir.listFiles()?.filter { it.name.endsWith(".tmp") }.orEmpty()
        assertTrue(sobrantes.isEmpty(), "quedan temporales: ${sobrantes.map { it.name }}")
    }

    @Test
    fun `conserva la escritura anterior como copia`() {
        val store = FileKeyValueStore(tempDir())
        store.write("a.json", "version-1")
        store.write("a.json", "version-2")
        assertEquals("version-2", store.read("a.json"))
        assertEquals("version-1", store.readBackup("a.json"), "la copia debe ser la version previa")
    }

    @Test
    fun `la primera escritura todavia no tiene copia`() {
        val store = FileKeyValueStore(tempDir())
        store.write("a.json", "unica")
        assertNull(store.readBackup("a.json"))
    }

    @Test
    fun `apartar deja el fichero fuera de sitio pero sin borrarlo`() {
        val dir = tempDir()
        val store = FileKeyValueStore(dir)
        store.write("a.json", "contenido irrecuperable")
        store.quarantine("a.json")

        assertNull(store.read("a.json"), "el original ya no debe leerse")
        val apartado = dir.listFiles()?.firstOrNull { it.name.contains(".corrupto-") }
        assertTrue(apartado != null, "el contenido debe quedar apartado, no borrado")
        assertEquals("contenido irrecuperable", apartado!!.readText())
    }

    @Test
    fun `borrar se lleva tambien la copia`() {
        val store = FileKeyValueStore(tempDir())
        store.write("a.json", "uno")
        store.write("a.json", "dos")
        store.delete("a.json")
        assertNull(store.read("a.json"))
        assertNull(store.readBackup("a.json"), "la copia no debe sobrevivir al borrado")
    }
}
