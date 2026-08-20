package com.iptv.family.shared.data.repository

import com.iptv.family.shared.data.store.FileKeyValueStore
import com.iptv.family.shared.model.Playlist
import com.iptv.family.shared.model.SourceType
import com.iptv.family.shared.model.UserSettings
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LibraryRepositoryTest {

    private fun tempStore(): FileKeyValueStore {
        val dir = Files.createTempDirectory("iptv-shared-test").toFile()
        return FileKeyValueStore(dir)
    }

    @Test
    fun playlists_round_trip() = runBlocking {
        val repo = LibraryRepository(tempStore())
        val playlists = listOf(
            Playlist(id = "1", name = "Mi Lista", type = SourceType.M3U_URL, m3uUrl = "http://x/list.m3u"),
            Playlist(id = "2", name = "Xtream", type = SourceType.XTREAM, xtreamUrl = "http://panel", xtreamUser = "u", xtreamPass = "p")
        )

        repo.savePlaylists(playlists)
        val loaded = repo.loadPlaylists()

        assertEquals(2, loaded.size)
        assertEquals("Mi Lista", loaded[0].name)
        assertEquals(SourceType.XTREAM, loaded[1].type)
        assertEquals("http://panel", loaded[1].xtreamUrl)
    }

    @Test
    fun settings_round_trip() = runBlocking {
        val repo = LibraryRepository(tempStore())
        repo.saveSettings(UserSettings(selectedTheme = com.iptv.family.shared.model.ThemeType.LIGHT, bufferMs = 30000))
        val loaded = repo.loadSettings()
        assertEquals(30000, loaded.bufferMs)
    }

    @Test
    fun parse_m3u_payload_without_network() {
        val repo = LibraryRepository(tempStore())
        val content = """
            #EXTM3U
            #EXTINF:-1 group-title="Deportes",Canal Dep
            http://x/dep.m3u8
            #EXTINF:-1 group-title="Películas",Film
            http://x/film.mp4
        """.trimIndent()

        val result = repo.loadChannelsFromContent(content)
        val ok = assertIs<LibraryRepository.ChannelsResult.Ok>(result)
        assertEquals(2, ok.channels.size)
        assertTrue(ok.categories.isNotEmpty())
    }
}