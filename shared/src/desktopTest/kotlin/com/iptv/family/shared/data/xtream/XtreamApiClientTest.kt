package com.iptv.family.shared.data.xtream

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Prueba el cliente Xtream contra un servidor HTTP embebido en memoria
 * para validar parsing, construcciÃ³n de URLs y manejo de errores.
 */
class XtreamApiClientTest {

    private val userInfoOk = """{"user_info":{"username":"alice","status":"12","exp_date":"2026-01-01","auth":"1"}}"""
    private val userInfoBad = """{"user_info":{"auth":"0"}}"""
    private val liveStreamsJson = """[
        {"stream_id":"101","name":"CNN","stream_icon":"http://x/c.png","category_id":"5"},
        {"stream_id":"102","name":"ESPN","category_id":"7"}
    ]"""
    private val vodStreamsJson = """[{"stream_id":"201","name":"Peli","category_id":"6"}]"""
    private val seriesJson = """[{"series_id":"301","name":"Serie","category_id":"8"}]"""
    private val liveCatsJson = """[{"category_id":"5","category_name":"Noticias"},{"category_id":"7","category_name":"Deportes"}]"""
    private val epgJson = """{"epg":[{"id":"1","channel_id":"101","title":"Programa","description":"Desc","start":"1704070800","end":"1704074400","category":"X"}]}"""

    private val responses = mutableMapOf(
        "user_info" to userInfoOk,
        "get_live_streams" to liveStreamsJson,
        "get_vod_streams" to vodStreamsJson,
        "get_series" to seriesJson,
        "get_live_categories" to liveCatsJson,
        "get_vod_categories" to "[]",
        "get_series_categories" to "[]",
        "get_epg" to epgJson
    )

    @Test
    fun login_success_parses_user_info() = withServer { baseUrl ->
        val client = XtreamApiClient(baseUrl, "alice", "pw")
        val result = client.login()
        assertTrue(result.success, "Login deberia ser correcto")
        assertEquals("alice", result.username)
        assertEquals("12", result.status)
        assertEquals("2026-01-01", result.expDate)
    }

    @Test
    fun login_rejects_invalid_credentials() = withAuthResponse(userInfoBad) { baseUrl ->
        val client = XtreamApiClient(baseUrl, "alice", "pw")
        val result = client.login()
        assertFalse(result.success)
        assertFalse(result.error.isNullOrBlank())
    }

    @Test
    fun login_returns_error_when_server_unreachable() = runBlocking {
        val client = XtreamApiClient("http://127.0.0.1:1", "u", "p")
        val result = client.login()
        assertFalse(result.success)
        assertFalse(result.error.isNullOrBlank())
    }

    @Test
    fun parses_live_streams_and_builds_urls() = withServer { baseUrl ->
        val client = XtreamApiClient(baseUrl, "alice", "pw")
        val streams = client.getLiveStreams()

        assertEquals(2, streams.size)
        val first = streams[0]
        assertEquals("101", first.id)
        assertEquals("CNN", first.name)
        assertEquals("http://x/c.png", first.logoUrl)
        assertTrue(first.url.endsWith("/live/alice/pw/101.m3u8"), "URL live debe construirse")
        assertEquals("5", first.group)
    }

    @Test
    fun parses_vod_and_series() = withServer { baseUrl ->
        val client = XtreamApiClient(baseUrl, "alice", "pw")
        val vod = client.getVodStreams()
        assertEquals(1, vod.size)
        assertEquals("201", vod[0].id)
        assertTrue(vod[0].url.endsWith("/movie/alice/pw/201.mp4"))

        val series = client.getSeriesStreams()
        assertEquals(1, series.size)
        assertEquals("301", series[0].id)
        assertEquals("Serie", series[0].name)
    }

    @Test
    fun parses_categories() = withServer { baseUrl ->
        val client = XtreamApiClient(baseUrl, "alice", "pw")
        val live = client.getLiveCategories()
        assertEquals(2, live.size)
        assertEquals("5", live[0].id)
        assertEquals("Noticias", live[0].name)
    }

    @Test
    fun parses_epg_and_converts_to_millis() = withServer { baseUrl ->
        val client = XtreamApiClient(baseUrl, "alice", "pw")
        val epg = client.getEPG()
        assertEquals(1, epg.size)
        assertEquals("1", epg[0].id)
        assertEquals("101", epg[0].channelId)
        assertEquals("Programa", epg[0].title)
        // Epoch en segundos â†’ ms
        assertEquals(1704070800000L, epg[0].startTime)
        assertEquals(1704074400000L, epg[0].endTime)
    }

    @Test
    fun tolerates_malformed_json() = withRawServer("esto no es json") { baseUrl ->
        val client = XtreamApiClient(baseUrl, "u", "p")
        assertTrue(client.getLiveStreams().isEmpty())
        assertTrue(client.getEPG().isEmpty())
    }

    // ------------------------------------------------------------------
    // Helpers (servidor HTTP embebido)
    // ------------------------------------------------------------------

    private fun withServer(block: suspend (String) -> Unit) {
        startServer({ action -> responses[action] ?: "[]" }, block)
    }

    private fun withAuthResponse(userInfo: String, block: suspend (String) -> Unit) {
        startServer({ action ->
            if (action == "user_info") userInfo else "[]"
        }, block)
    }

    private fun withRawServer(body: String, block: suspend (String) -> Unit) {
        startServer({ _ -> body }, block)
    }

    private fun startServer(respond: (action: String) -> String, block: suspend (String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val query = exchange.requestURI?.getQuery() ?: ""
            val action = query.split("&")
                .map { it.split("=", limit = 2) }
                .firstOrNull { it.size > 1 && it[0] == "action" }?.get(1) ?: ""
            val answer = respond(action)
            val bytes = answer.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.write(bytes)
            exchange.responseBody.close()
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            runBlocking { block(baseUrl) }
        } finally {
            server.stop(0)
        }
    }
}
