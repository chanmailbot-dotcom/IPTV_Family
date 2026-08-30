package com.iptv.family.shared.data.repository

import com.iptv.family.shared.data.store.FileKeyValueStore
import com.iptv.family.shared.model.Playlist
import com.iptv.family.shared.model.SourceType
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Copia del catalogo en disco: lo que hace que un corte de internet no deje la
 * aplicacion vacia. Se prueba contra un servidor local, apagandolo entre medias
 * para simular la caida.
 */
class CatalogoOfflineTest {

    private val m3u = """
        #EXTM3U
        #EXTINF:-1 tvg-id="uno" group-title="Cine",Canal Uno
        http://proveedor/live/pepe/clavesecreta/1.ts
        #EXTINF:-1 tvg-id="dos" group-title="Cine",Canal Dos
        http://proveedor/live/pepe/clavesecreta/2.ts
    """.trimIndent()

    private fun tempDir() = Files.createTempDirectory("iptv-offline-test").toFile()

    /** Servidor minimo que sirve la lista y se puede apagar a media prueba. */
    private class ServidorM3U(private val cuerpo: String) {
        private val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        val url: String get() = "http://127.0.0.1:${server.address.port}/lista.m3u"

        fun arrancar() {
            server.createContext("/lista.m3u") { intercambio ->
                val bytes = cuerpo.toByteArray()
                intercambio.sendResponseHeaders(200, bytes.size.toLong())
                intercambio.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        fun parar() = server.stop(0)
    }

    @Test
    fun serves_the_saved_catalogue_when_the_provider_is_unreachable() = runBlocking {
        val store = FileKeyValueStore(tempDir())
        val repo = LibraryRepository(store)
        val servidor = ServidorM3U(m3u)
        servidor.arrancar()
        val playlist = Playlist(id = "p1", name = "Prueba", type = SourceType.M3U_URL, m3uUrl = servidor.url)

        val enLinea = repo.buildChannels(playlist)
        assertIs<LibraryRepository.ChannelsResult.Ok>(enLinea)
        assertEquals(2, enLinea.channels.size)
        assertNull(enLinea.guardadoEnMs, "Recién descargado: no viene de la copia")

        // Se cae el proveedor.
        servidor.parar()

        val sinLinea = repo.buildChannels(playlist)
        assertIs<LibraryRepository.ChannelsResult.Ok>(sinLinea)
        assertEquals(2, sinLinea.channels.size, "Debe seguir habiendo canales sin conexión")
        assertNotNull(sinLinea.guardadoEnMs, "Y debe declarar que es una copia guardada")
        // Las direcciones tienen que servir para reproducir, con sus credenciales
        // intactas: si la copia devolviera el marcador, el canal no abriria.
        assertEquals(
            enLinea.channels.map { it.url }.toSet(),
            sinLinea.channels.map { it.url }.toSet(),
        )
        assertTrue(sinLinea.channels.all { it.url.contains("clavesecreta") })
    }

    @Test
    fun the_saved_catalogue_never_holds_the_password_in_the_clear() = runBlocking {
        val dir = tempDir()
        val repo = LibraryRepository(FileKeyValueStore(dir))
        val servidor = ServidorM3U(m3u)
        servidor.arrancar()
        val playlist = Playlist(
            id = "p1",
            name = "Prueba",
            type = SourceType.XTREAM,
            m3uUrl = servidor.url,
            xtreamUser = "pepe",
            xtreamPass = "clavesecreta",
        ).copy(type = SourceType.M3U_URL) // se descarga como M3U, pero con credenciales Xtream guardadas

        repo.buildChannels(playlist)
        servidor.parar()

        val enDisco = dir.listFiles().orEmpty().filter { it.name.startsWith("catalogo_") }
        assertTrue(enDisco.isNotEmpty(), "Debe haberse guardado la copia")
        val contenido = enDisco.joinToString("\n") { it.readText() }
        assertFalse(contenido.contains("clavesecreta"), "La contraseña no puede quedar escrita en disco")
        assertTrue(contenido.contains("{{secreto:"), "Debe quedar el marcador en su lugar")
    }

    @Test
    fun deleting_a_playlist_also_deletes_its_saved_catalogue() = runBlocking {
        val dir = tempDir()
        val repo = LibraryRepository(FileKeyValueStore(dir))
        val servidor = ServidorM3U(m3u)
        servidor.arrancar()
        val playlist = Playlist(id = "p1", name = "Prueba", type = SourceType.M3U_URL, m3uUrl = servidor.url)
        repo.savePlaylists(listOf(playlist))
        repo.buildChannels(playlist)
        servidor.parar()

        repo.deletePlaylist("p1")

        val quedan = dir.listFiles().orEmpty().filter { it.name.startsWith("catalogo_") }
        assertTrue(quedan.isEmpty(), "No pueden quedar canales de una lista que el usuario cree borrada")
    }

    @Test
    fun a_corrupt_saved_catalogue_does_not_break_the_error_path() = runBlocking {
        val dir = tempDir()
        val store = FileKeyValueStore(dir)
        val repo = LibraryRepository(store)
        store.write("catalogo_p1.json", "{ esto no es json valido")

        val result = repo.buildChannels(
            Playlist(id = "p1", name = "X", type = SourceType.M3U_URL, m3uUrl = "http://127.0.0.1:1/nada.m3u"),
        )

        // Sin copia utilizable se informa del error real, no se finge un catálogo.
        assertIs<LibraryRepository.ChannelsResult.Error>(result)
        Unit
    }
}
