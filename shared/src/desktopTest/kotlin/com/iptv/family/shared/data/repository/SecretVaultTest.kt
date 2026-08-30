package com.iptv.family.shared.data.repository

import com.iptv.family.shared.data.store.FileKeyValueStore
import com.iptv.family.shared.data.store.SecretVault
import com.iptv.family.shared.model.Playlist
import com.iptv.family.shared.model.SourceType
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La contraseña del panel Xtream no debe quedar en claro en `playlists.json`.
 *
 * Se prueba con un cifrado de mentira (Base64 al reves) en vez de con DPAPI: lo
 * que hay que verificar es el CAMINO -- que se cifra al guardar, se descifra al
 * leer y que lo antiguo en claro se sigue entendiendo -- no la criptografia del
 * sistema operativo, que no es nuestra.
 */
class SecretVaultTest {

    /** Cifrado ficticio y reversible, para poder comprobar el flujo. */
    private class VaultDeMentira : SecretVault {
        var cifrados = 0
        override fun protect(plain: String): String {
            cifrados++
            return Base64.getEncoder().encodeToString(plain.reversed().toByteArray())
        }
        override fun reveal(token: String): String =
            String(Base64.getDecoder().decode(token)).reversed()
    }

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "vault-${System.nanoTime()}").apply { mkdirs() }

    private fun playlist(pass: String) = Playlist(
        id = "pl-1", name = "Casa", type = SourceType.XTREAM,
        xtreamUrl = "http://panel.example", xtreamUser = "usuario", xtreamPass = pass,
    )

    @Test
    fun `la contraseña no queda en claro en el fichero`() = runBlocking {
        val dir = tempDir()
        val repo = LibraryRepository(FileKeyValueStore(dir), vault = VaultDeMentira())

        repo.savePlaylists(listOf(playlist("secreto-muy-visible")))

        val crudo = File(dir, "playlists.json").readText()
        assertFalse("secreto-muy-visible" in crudo, "la contraseña esta en claro en disco:\n$crudo")
        assertTrue(SecretVault.PREFIX in crudo, "deberia llevar la marca de cifrado")
    }

    @Test
    fun `al leer se recupera la contraseña original`() = runBlocking {
        val repo = LibraryRepository(FileKeyValueStore(tempDir()), vault = VaultDeMentira())
        repo.savePlaylists(listOf(playlist("clave-real")))
        assertEquals("clave-real", repo.loadPlaylists().single().xtreamPass)
    }

    @Test
    fun `una instalacion anterior con la contraseña en claro se sigue leyendo`() = runBlocking {
        val dir = tempDir()
        // Asi quedo el fichero antes de que existiera el cifrado.
        File(dir, "playlists.json").writeText(
            """[{"id":"pl-1","name":"Casa","type":"XTREAM","xtreamUrl":"http://p","xtreamUser":"u","xtreamPass":"vieja"}]"""
        )
        val repo = LibraryRepository(FileKeyValueStore(dir), vault = VaultDeMentira())

        assertEquals("vieja", repo.loadPlaylists().single().xtreamPass, "no se puede dejar tirado a quien ya tenia listas")
    }

    @Test
    fun `lo antiguo queda cifrado al siguiente guardado`() = runBlocking {
        val dir = tempDir()
        File(dir, "playlists.json").writeText(
            """[{"id":"pl-1","name":"Casa","type":"XTREAM","xtreamUrl":"http://p","xtreamUser":"u","xtreamPass":"vieja"}]"""
        )
        val repo = LibraryRepository(FileKeyValueStore(dir), vault = VaultDeMentira())

        repo.savePlaylists(repo.loadPlaylists()) // el usuario toca cualquier cosa

        val crudo = File(dir, "playlists.json").readText()
        assertFalse("vieja" in crudo, "la conversion debe ocurrir sola")
        assertEquals("vieja", repo.loadPlaylists().single().xtreamPass)
    }

    @Test
    fun `no se cifra dos veces`() = runBlocking {
        val vault = VaultDeMentira()
        val repo = LibraryRepository(FileKeyValueStore(tempDir()), vault = vault)
        repo.savePlaylists(listOf(playlist("clave")))
        val tras1 = vault.cifrados
        // Guardar lo mismo otra vez sin pasar por load: no debe re-cifrar lo ya cifrado.
        repo.savePlaylists(repo.loadPlaylists())
        assertEquals(tras1 + 1, vault.cifrados, "cada guardado cifra una vez, no acumula capas")
        assertEquals("clave", repo.loadPlaylists().single().xtreamPass)
    }

    @Test
    fun `sin plataforma que cifre, se guarda en claro pero no se rompe nada`() = runBlocking {
        val dir = tempDir()
        val repo = LibraryRepository(FileKeyValueStore(dir), vault = SecretVault.NONE)
        repo.savePlaylists(listOf(playlist("clave")))
        // Preferible una aplicacion que funciona con un aviso en el log a una que
        // se niega a guardar la lista.
        assertEquals("clave", repo.loadPlaylists().single().xtreamPass)
    }

    @Test
    fun `una lista M3U sin credenciales no se ve afectada`() = runBlocking {
        val repo = LibraryRepository(FileKeyValueStore(tempDir()), vault = VaultDeMentira())
        val m3u = Playlist(id = "pl-2", name = "Publica", type = SourceType.M3U_URL, m3uUrl = "http://x/l.m3u")
        repo.savePlaylists(listOf(m3u))
        assertEquals(null, repo.loadPlaylists().single().xtreamPass)
    }
}
