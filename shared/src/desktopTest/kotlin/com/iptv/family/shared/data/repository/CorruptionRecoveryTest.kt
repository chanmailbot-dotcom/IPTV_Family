package com.iptv.family.shared.data.repository

import com.iptv.family.shared.data.store.FileKeyValueStore
import com.iptv.family.shared.model.Playlist
import com.iptv.family.shared.model.SourceType
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La cadena de perdida de datos que habia que romper:
 *
 *   1. el proceso muere a media escritura  -> playlists.json queda truncado
 *   2. al arrancar, el JSON no parsea      -> se devolvia lista VACIA en silencio
 *   3. el usuario toca cualquier cosa      -> se guarda la lista vacia ENCIMA
 *   4. listas y credenciales, perdidas para siempre
 *
 * Cada test de aqui corta uno de esos eslabones.
 */
class CorruptionRecoveryTest {

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "recov-${System.nanoTime()}").apply { mkdirs() }

    private fun playlist(name: String) = Playlist(
        id = "pl-$name",
        name = name,
        type = SourceType.XTREAM,
        xtreamUrl = "http://panel.example",
        xtreamUser = "usuario",
        xtreamPass = "secreto",
    )

    /**
     * La copia guarda la generacion ANTERIOR, asi que recuperando se vuelve al
     * ultimo estado completo: se pierde el cambio que se estaba escribiendo
     * cuando reventó, y nada mas. Es justo lo que debe pasar.
     */
    @Test
    fun `un fichero truncado vuelve al ultimo estado completo`() = runBlocking {
        val dir = tempDir()
        val repo = LibraryRepository(FileKeyValueStore(dir))

        repo.savePlaylists(listOf(playlist("casa")))                        // estado completo
        repo.savePlaylists(listOf(playlist("casa"), playlist("segunda")))   // este es el que se corrompe

        // Simula el corte: el fichero bueno queda a medias.
        File(dir, "playlists.json").writeText("[{\"id\":\"pl-casa\",\"na")

        val recuperadas = repo.loadPlaylists()
        assertEquals(1, recuperadas.size, "vuelve el estado anterior, no una lista vacia")
        assertEquals("casa", recuperadas.first().name)
        assertEquals("secreto", recuperadas.first().xtreamPass, "las credenciales siguen ahi")
    }

    @Test
    fun `sin copia utilizable, el fichero ilegible se aparta en vez de perderse`() = runBlocking {
        val dir = tempDir()
        val repo = LibraryRepository(FileKeyValueStore(dir))

        // Un unico guardado: no hay copia previa a la que recurrir.
        repo.savePlaylists(listOf(playlist("unica")))
        File(dir, "playlists.json").writeText("esto no es json")

        assertEquals(emptyList(), repo.loadPlaylists())

        val apartado = dir.listFiles()?.firstOrNull { it.name.contains(".corrupto-") }
        assertTrue(apartado != null, "el contenido ilegible debe quedar apartado para poder mirarlo")
        assertEquals("esto no es json", apartado!!.readText())
    }

    @Test
    fun `tras recuperar, el siguiente guardado no pisa lo recuperado`() = runBlocking {
        val dir = tempDir()
        val repo = LibraryRepository(FileKeyValueStore(dir))

        repo.savePlaylists(listOf(playlist("casa")))
        repo.savePlaylists(listOf(playlist("casa"), playlist("segunda")))
        File(dir, "playlists.json").writeText("{roto")

        // El usuario abre la app (recupera "casa") y anade otra lista.
        val recuperadas = repo.loadPlaylists()
        repo.savePlaylists(recuperadas + playlist("tercera"))

        val finales = repo.loadPlaylists().map { it.name }
        assertEquals(listOf("casa", "tercera"), finales, "lo recuperado no se pierde al guardar encima")
    }

    @Test
    fun `los ajustes tambien se recuperan`() = runBlocking {
        val dir = tempDir()
        val repo = LibraryRepository(FileKeyValueStore(dir))

        repo.saveSettings(repo.loadSettings().copy(webServerPort = 7000))
        repo.saveSettings(repo.loadSettings().copy(webServerPort = 7001))
        File(dir, "settings.json").writeText("{\"webServerPort\":")

        // Igual que con las listas: vuelve el estado completo anterior (7000),
        // no los ajustes de fabrica.
        assertEquals(7000, repo.loadSettings().webServerPort)
    }

    @Test
    fun `un almacen sin fichero devuelve los valores por defecto sin apartar nada`() = runBlocking {
        val dir = tempDir()
        val repo = LibraryRepository(FileKeyValueStore(dir))
        assertEquals(emptyList(), repo.loadPlaylists())
        assertTrue(dir.listFiles().orEmpty().none { it.name.contains(".corrupto-") })
    }
}
