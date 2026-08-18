package com.iptv.family.data.xtream

import com.iptv.family.domain.model.Channel
import com.iptv.family.domain.model.Category
import com.iptv.family.domain.model.Playlist
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class XtreamApiClientTest {

    @Test
    fun testBuildStreamUrl() {
        val client = XtreamApiClient.create(
            "http://example.com:8080",
            "testuser",
            "testpass"
        )

        val stream = XtreamStream(
            stream_id = "12345",
            name = "Test Channel",
            container_extension = "m3u8",
        )

        val url = client.toDomainModels("playlist1", "Test", XtreamLoadResult.Success(
            serverInfo = XtreamServerInfo(
                url = "http://example.com:8080",
                port = "8080",
                version = "1.0",
                time_zone = "UTC",
                timestamp_now = System.currentTimeMillis() / 1000,
                timestamp_timezone_offset = 0,
            ),
            categories = emptyList(),
            streams = listOf(stream),
        ))

        assertEquals(1, url.first.size)
        // The URL should be built correctly
        val channel = url.first.first()
        assertTrue(channel.url.contains("http://example.com:8080"))
        assertTrue(channel.url.contains("testuser"))
        assertTrue(channel.url.contains("testpass"))
        assertTrue(channel.url.contains("12345.m3u8"))
    }

    @Test
    fun testXtreamCategoryConversion() {
        val category = XtreamCategory(
            category_id = "1",
            category_name = "News",
        )

        val stream = XtreamStream(
            stream_id = "100",
            name = "News Channel",
            category_id = "1",
        )

        val client = XtreamApiClient.create(
            "http://example.com:8080",
            "user",
            "pass"
        )

        val result = client.toDomainModels("playlist1", "Test", XtreamLoadResult.Success(
            serverInfo = XtreamServerInfo(
                url = "http://example.com:8080",
                port = "8080",
                version = "1.0",
                time_zone = "UTC",
                timestamp_now = System.currentTimeMillis() / 1000,
                timestamp_timezone_offset = 0,
            ),
            categories = listOf(category),
            streams = listOf(stream),
        ))

        assertEquals(1, result.second.size)
        assertEquals("News", result.second.first().name)
        assertEquals(1, result.second.first().channelIds.size)
    }
}