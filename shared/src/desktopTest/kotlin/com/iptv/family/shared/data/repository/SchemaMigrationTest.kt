package com.iptv.family.shared.data.repository

import com.iptv.family.shared.data.store.KeyValueStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Migraciones de esquema: la red que evita que el primer cambio de formato
 * rompa las instalaciones que ya existen. Con la aplicacion publicada eso no se
 * arregla pidiendole a la gente que borre un fichero.
 */
class SchemaMigrationTest {

    private class MemoriaStore(inicial: Map<String, String> = emptyMap()) : KeyValueStore {
        val datos = inicial.toMutableMap()
        override fun write(key: String, value: String) { datos[key] = value }
        override fun read(key: String): String? = datos[key]
        override fun delete(key: String) { datos.remove(key) }
    }

    private fun version(store: MemoriaStore): Int? =
        store.read("schema.json")?.let { Regex("\"version\"\\s*:\\s*(\\d+)").find(it)?.groupValues?.get(1)?.toInt() }

    @Test
    fun `una instalacion limpia nace ya en la version actual y no migra nada`() = runBlocking {
        val store = MemoriaStore()
        val aplicadas = mutableListOf<Int>()
        val repo = LibraryRepository(store, listOf(
            SchemaMigration(1, "de prueba") { aplicadas += 1 },
        ))

        repo.loadPlaylists()

        assertEquals(emptyList(), aplicadas, "no hay datos viejos que migrar")
        assertEquals(2, version(store), "la version actual es la ultima migracion + 1")
    }

    @Test
    fun `unos datos sin sello se tratan como version 1 y se migran`() = runBlocking {
        // Asi son hoy todas las instalaciones existentes: tienen playlists.json
        // pero no schema.json, porque el sello no existia.
        val store = MemoriaStore(mapOf("playlists.json" to "[]"))
        val aplicadas = mutableListOf<Int>()
        val repo = LibraryRepository(store, listOf(
            SchemaMigration(1, "primera") { aplicadas += 1 },
            SchemaMigration(2, "segunda") { aplicadas += 2 },
        ))

        repo.loadPlaylists()

        assertEquals(listOf(1, 2), aplicadas, "se aplican en orden desde la 1")
        assertEquals(3, version(store))
    }

    @Test
    fun `solo se aplican las migraciones pendientes`() = runBlocking {
        val store = MemoriaStore(mapOf(
            "playlists.json" to "[]",
            "schema.json" to "{\"version\":2}",
        ))
        val aplicadas = mutableListOf<Int>()
        val repo = LibraryRepository(store, listOf(
            SchemaMigration(1, "ya hecha") { aplicadas += 1 },
            SchemaMigration(2, "pendiente") { aplicadas += 2 },
        ))

        repo.loadPlaylists()

        assertEquals(listOf(2), aplicadas, "la 1 ya estaba aplicada")
        assertEquals(3, version(store))
    }

    @Test
    fun `unos datos de una version MAS NUEVA no se tocan`() = runBlocking {
        // El usuario probo una version posterior y volvio atras. Migrar aqui
        // seria destruir datos que esta version no entiende.
        val store = MemoriaStore(mapOf(
            "playlists.json" to "[]",
            "schema.json" to "{\"version\":9}",
        ))
        val aplicadas = mutableListOf<Int>()
        val repo = LibraryRepository(store, listOf(
            SchemaMigration(1, "de prueba") { aplicadas += 1 },
        ))

        repo.loadPlaylists()

        assertEquals(emptyList(), aplicadas)
        assertEquals(9, version(store), "el sello no se rebaja")
    }

    @Test
    fun `si una migracion falla no se sella la version`() = runBlocking {
        val store = MemoriaStore(mapOf("playlists.json" to "[]"))
        val repo = LibraryRepository(store, listOf(
            SchemaMigration(1, "revienta") { error("fallo a proposito") },
        ))

        repo.loadPlaylists() // no debe propagar la excepcion

        assertTrue(version(store) == null, "sin sello, la proxima vez se reintenta")
    }

    @Test
    fun `migrar ocurre una sola vez aunque se lea varias veces`() = runBlocking {
        val store = MemoriaStore(mapOf("playlists.json" to "[]"))
        var veces = 0
        val repo = LibraryRepository(store, listOf(
            SchemaMigration(1, "cuenta") { veces++ },
        ))

        repo.loadPlaylists()
        repo.loadSettings()
        repo.loadFavorites()

        assertEquals(1, veces)
    }
}
