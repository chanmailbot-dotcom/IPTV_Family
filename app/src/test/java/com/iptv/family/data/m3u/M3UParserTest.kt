package com.iptv.family.data.m3u

import com.iptv.family.domain.model.Channel
import com.iptv.family.domain.model.Category
import com.iptv.family.domain.model.Playlist
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class M3UParserTest {

    @Test
    fun testParseSimpleM3U() {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 tvg-id="test1" tvg-name="Test Channel 1" tvg-logo="http://example.com/logo1.png" group-title="News",Test Channel 1
            http://example.com/stream1.m3u8
            #EXTINF:-1 tvg-id="test2" tvg-name="Test Channel 2" tvg-logo="http://example.com/logo2.png" group-title="Sports",Test Channel 2
            http://example.com/stream2.m3u8
        """.trimIndent()

        val result = M3UParser.parse(m3uContent, "Test Playlist", "http://example.com/list.m3u")

        assertEquals("Test Playlist", result.name)
        assertEquals(2, result.channels.size)
        assertEquals("test1", result.channels[0].tvgId)
        assertEquals("Test Channel 1", result.channels[0].name)
        assertEquals("http://example.com/logo1.png", result.channels[0].logo)
        assertEquals("News", result.channels[0].group)
        assertEquals("http://example.com/stream1.m3u8", result.channels[0].url)
    }

    @Test
    fun testParseM3UWithCategories() {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 group-title="News",BBC News
            http://example.com/bbc.m3u8
            #EXTINF:-1 group-title="Sports",ESPN
            http://example.com/espn.m3u8
            #EXTINF:-1 group-title="News",CNN
            http://example.com/cnn.m3u8
        """.trimIndent()

        val result = M3UParser.parse(m3uContent, "Test", "")

        assertEquals(3, result.channels.size)
        assertEquals(2, result.categories.size)
        val newsCategory = result.categories.find { it.name == "News" }
        assertNotNull(newsCategory)
        assertEquals(2, newsCategory?.channelIds?.size ?: 0)
    }

    @Test
    fun testParseM3UWithRadio() {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 tvg-id="radio1" radio="true",Radio Station
            http://example.com/radio.m3u8
        """.trimIndent()

        val result = M3UParser.parse(m3uContent, "Test", "")
        assertEquals(1, result.channels.size)
        assertTrue(result.channels[0].isRadio)
    }

    @Test
    fun testParseEmptyM3U() {
        val m3uContent = "#EXTM3U\n"

        val result = M3UParser.parse(m3uContent, "Empty", "")
        assertEquals(0, result.channels.size)
        assertEquals(0, result.categories.size)
    }

    @Test
    fun testParseM3UWithSpecialChars() {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 tvg-id="esp" tvg-name="Español" tvg-logo="http://example.com/español.png" group-title="Entretenimiento",Canal Español
            http://example.com/espanol.m3u8
        """.trimIndent()

        val result = M3UParser.parse(m3uContent, "Test", "")
        assertEquals(1, result.channels.size)
        assertEquals("Español", result.channels[0].tvgName)
        assertEquals("Entretenimiento", result.channels[0].group)
    }
}