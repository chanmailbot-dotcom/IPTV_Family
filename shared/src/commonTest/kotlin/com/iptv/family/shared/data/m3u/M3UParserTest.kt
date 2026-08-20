package com.iptv.family.shared.data.m3u

import com.iptv.family.shared.model.CategoryType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class M3UParserTest {

    private val parser = M3UParser()

    @Test
    fun parses_basic_m3u_with_attributes() {
        val content = """
            #EXTM3U
            #EXTINF:-1 tvg-id="CNN.us" tvg-logo="https://x/cnn.png" group-title="Noticias",CNN Internacional
            https://stream.example/cnn.m3u8
            #EXTINF:-1 tvg-id="ESPN.us" group-title="Deportes",ESPN
            http://stream.example/espn.m3u8
        """.trimIndent()

        val result = parser.parse(content)

        assertEquals(2, result.channels.size, "Debe parsear 2 canales")
        val cnn = result.channels[0]
        assertEquals("CNN.us", cnn.id)
        assertEquals("CNN Internacional", cnn.name)
        assertEquals("https://x/cnn.png", cnn.logoUrl)
        assertEquals("Noticias", cnn.group)
        assertEquals(CategoryType.LIVE, cnn.categoryType)

        val espn = result.channels[1]
        assertEquals("ESPN", espn.name)

        // Categorías agrupadas
        val noticias = result.categories.first { it.name == "Noticias" }
        assertNotNull(noticias)
        assertEquals(1, noticias.channels.size)
    }

    @Test
    fun infers_category_type_from_group_name() {
        val content = """
            #EXTM3U
            #EXTINF:-1 group-title="Películas",Mi Película
            http://x/movie.mp4
            #EXTINF:-1 group-title="Series",Mi Serie
            http://x/series.mp4
            #EXTINF:-1 group-title="General",Canal 1
            http://x/c1.m3u8
        """.trimIndent()

        val result = parser.parse(content)

        assertEquals(CategoryType.VOD, result.channels[0].categoryType)
        assertEquals(CategoryType.SERIES, result.channels[1].categoryType)
        assertEquals(CategoryType.LIVE, result.channels[2].categoryType)
    }

    @Test
    fun handles_extgrp_and_blank_entries() {
        val content = """
            #EXTM3U
            #EXTINF:-1,Canal Uno
            #EXTGRP:Noticias
            http://x/uno.m3u8

            #EXTINF:-1,Canal Dos
            http://x/dos.m3u8
        """.trimIndent()

        val result = parser.parse(content)
        assertEquals(2, result.channels.size)
        // #EXTGRP:Noticias aplica a "Canal Uno"
        assertEquals("Noticias", result.channels[0].group)
    }

    @Test
    fun assigns_fallback_name_and_id_when_missing() {
        val content = "#EXTM3U\n#EXTINF:-1,\nhttp://x/stream.m3u8".trimIndent()
        val result = parser.parse(content)

        val ch = result.channels.single()
        assertNotNull(ch.id)
        assertNotNull(ch.name)
        assertNull(null)
    }
}