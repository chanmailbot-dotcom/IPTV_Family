package com.iptv.family.shared.data.xmltv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XmltvParserTest {

    private val parser = XmltvParser()

    private val sampleXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv generator-info-name="test">
          <channel id="CNN.us"><display-name>CNN</display-name></channel>
          <channel id="ESPN.us"><display-name>ESPN</display-name></channel>
          <programme start="20240101010000 +0000" stop="20240101020000 +0000" channel="CNN.us">
            <title lang="es">Noticias de la mañana</title>
            <desc><![CDATA[Resumen de noticias]]></desc>
            <category>Noticias</category>
            <icon src="http://x/cnn.png"/>
          </programme>
          <programme start="20240101020000 +0100" stop="20240101030000 +0100" channel="ESPN.us">
            <title lang="en">Sports Center</title>
          </programme>
        </tv>
    """.trimIndent()

    @Test
    fun parses_programmes_and_fields() {
        val result = parser.parse(sampleXml)

        assertEquals(2, result.size, "Debe parsear 2 programmes")
        val news = result[0]
        assertEquals("CNN.us", news.channelId)
        assertEquals("Noticias de la mañana", news.title)
        assertEquals("Resumen de noticias", news.description)
        assertEquals("Noticias", news.category)
        assertEquals("http://x/cnn.png", news.iconUrl)

        // 2024-01-01 01:00:00 UTC = 1704070800000 ms; 02:00 = 1704074400000 ms
        assertEquals(1704070800000, news.startTime)
        assertEquals(1704074400000, news.endTime)
    }

    @Test
    fun respects_timezone_offset() {
        // El segundo programa usa +0100 → UTC = 01:00
        val espn = parser.parse(sampleXml)[1]
        // 02:00 +0100 → UTC 01:00
        assertEquals(1704070800000L, espn.startTime)
    }

    @Test
    fun returns_empty_on_blank_or_invalid_xml() {
        assertTrue(parser.parse("").isEmpty())
        assertTrue(parser.parse("   \n ").isEmpty())
    }

    @Test
    fun tolerates_missing_title_and_channel() {
        val xml = """
            <tv>
              <programme start="20240101000000 +0000" stop="20240101010000 +0000" channel="X">
                <title>Sin canal valido</title>
              </programme>
              <programme start="20240101000000 +0000" channel="">
                <title>Sin channel</title>
              </programme>
            </tv>
        """.trimIndent()
        val result = parser.parse(xml)
        // Solo el que tiene channel + title
        assertEquals(1, result.size)
    }
}