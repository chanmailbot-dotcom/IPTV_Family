package com.iptv.family.desktop.state

import com.iptv.family.shared.model.SourceType
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppStateTest {

    private val sampleM3u = """
        #EXTM3U
        #EXTINF:0 tvg-id="cnn" group-title="News",CNN
        http://example.com/cnn.m3u8
        #EXTINF:0 group-title="Movies",Movie Channel
        http://example.com/movie.m3u8
    """.trimIndent()

    @Test
    fun add_m3u_file_playlist_parses_channels_and_categories() = runBlocking {
        val state = newInMemoryAppState()
        state.addM3uFile("Mi Lista", sampleM3u)

        assertEquals(1, state.playlists.size)
        assertEquals("Mi Lista", state.playlists.first().name)
        assertEquals(SourceType.M3U_FILE, state.playlists.first().type)
        assertEquals(2, state.channels.size)
        assertEquals("CNN", state.channels[0].name)
        assertEquals("Movie Channel", state.channels[1].name)
        assertTrue(state.channels.all { !it.isFavorite })

        val names = state.categories.map { it.name }
        assertTrue("Todas" in names)
        assertTrue("News" in names)
        assertTrue("Movies" in names)
    }

    @Test
    fun toggle_favorite_persists() = runBlocking {
        val state = newInMemoryAppState()
        state.addM3uFile("Mi Lista", sampleM3u)
        val id = state.channels.first { it.name == "CNN" }.id
        state.toggleFavorite(id)

        assertEquals(1, state.favorites.size)
        assertTrue(state.isFavorite(id))
        assertTrue(state.channels.first { it.id == id }.isFavorite)
    }

    @Test
    fun delete_selected_playlist_clears_channels() = runBlocking {
        val state = newInMemoryAppState()
        state.addM3uFile("Mi Lista", sampleM3u)
        val id = state.playlists.first().id

        state.deletePlaylist(id)
        assertEquals(0, state.playlists.size)
        assertEquals(0, state.channels.size)
        assertEquals(0, state.categories.size)
    }

    @Test
    fun load_unknown_playlist_id_loads_nothing() = runBlocking {
        val state = newInMemoryAppState()
        state.addM3uFile("Mi Lista", sampleM3u)
        state.selectPlaylist("no-existe")
        assertEquals(0, state.channels.size)
    }

    // No hay fixtures de disco; los @BeforeTest/@AfterTest quedan como ganchos de limpieza.
    @BeforeTest fun up() {}
    @AfterTest fun down() {}
}
