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

    @Test
    fun parses_from_a_stream_without_loading_it_whole() {
        val result = parser.parse(sampleXml.byteInputStream())
        assertEquals(2, result.size)
        assertEquals("Noticias de la mañana", result[0].title)
    }

    @Test
    fun keeps_only_programmes_inside_the_window() {
        // Ventana: solo el segundo programa (01:00 UTC). El primero termina
        // antes de que empiece la ventana y el tercero empieza despues.
        val xml = """
            <tv>
              <programme start="20240101000000 +0000" stop="20240101003000 +0000" channel="A">
                <title>Ya terminó</title>
              </programme>
              <programme start="20240101010000 +0000" stop="20240101020000 +0000" channel="A">
                <title>En ventana</title>
              </programme>
              <programme start="20240105000000 +0000" stop="20240105010000 +0000" channel="A">
                <title>Demasiado lejos</title>
              </programme>
            </tv>
        """.trimIndent()

        val result = parser.parse(
            xml.byteInputStream(),
            keepFromMs = 1704070200000L, // 2024-01-01 00:50 UTC
            keepUntilMs = 1704079200000L, // 2024-01-01 03:20 UTC
        )

        assertEquals(1, result.size)
        assertEquals("En ventana", result[0].title)
    }

    @Test
    fun a_programme_without_end_survives_the_window() {
        // Sin `stop` no se sabe cuando acaba: descartarlo por el final dejaria
        // sin guia a las listas que no declaran fin de programa.
        val xml = """
            <tv>
              <programme start="20240101010000 +0000" channel="A"><title>Sin fin</title></programme>
            </tv>
        """.trimIndent()

        val result = parser.parse(xml.byteInputStream(), keepFromMs = 1704074400000L, keepUntilMs = Long.MAX_VALUE)

        assertEquals(1, result.size)
    }

    @Test
    fun stops_at_the_cap_instead_of_running_out_of_memory() {
        val muchos = buildString {
            append("<tv>")
            repeat(50) { i ->
                append("""<programme start="20240101010000 +0000" channel="C$i"><title>P$i</title></programme>""")
            }
            append("</tv>")
        }

        val result = parser.parse(muchos.byteInputStream(), maxProgrammes = 10)

        assertEquals(10, result.size)
    }

    /**
     * La prueba que justifica todo el cambio: una guia mas grande que el heap.
     *
     * Se generan ~150 MB de XMLTV SIN tenerlos nunca en memoria (el flujo los
     * va fabricando segun se leen), de los que solo tres programas caen en la
     * ventana. Con el parser anterior -- que cargaba el XML entero en una
     * cadena y encima montaba el DOM completo -- esto no llegaba a terminar
     * con el heap de las pruebas: se moria por memoria, que es exactamente lo
     * que le pasaba en un Fire TV con una guia real de un proveedor.
     */
    @Test
    fun digests_a_guide_far_bigger_than_the_heap() {
        val heapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        val programas = 900_000 // ~150 MB de XML

        val result = parser.parse(
            guiaGenerada(programas),
            keepFromMs = 1704070200000L,
            keepUntilMs = 1704079200000L,
        )

        assertEquals(3, result.size, "Solo los 3 de la ventana (heap de la prueba: $heapMb MB)")
        assertTrue(result.all { it.title == "En ventana" })
    }

    /**
     * Flujo que fabrica una guia XMLTV al vuelo, sin materializarla: si la
     * prueba construyera la cadena entera, el que se quedaria sin memoria
     * seria el propio test y no probaria nada.
     */
    private fun guiaGenerada(programas: Int): java.io.InputStream {
        val trozos = sequence {
            yield("<tv>")
            repeat(programas) { i ->
                // Casi todos quedan muy lejos en el futuro; tres caen dentro.
                val enVentana = i % 300_000 == 0
                val inicio = if (enVentana) "20240101010000 +0000" else "20240115120000 +0000"
                val titulo = if (enVentana) "En ventana" else "Fuera $i"
                yield(
                    """<programme start="$inicio" stop="20240115130000 +0000" channel="C$i">""" +
                        """<title>$titulo</title><desc>Relleno para que pese: ${"x".repeat(80)}</desc>""" +
                        """</programme>""",
                )
            }
            yield("</tv>")
        }.iterator()

        return object : java.io.InputStream() {
            private var actual = ByteArray(0)
            private var pos = 0

            override fun read(): Int {
                val uno = ByteArray(1)
                return if (read(uno, 0, 1) == 1) uno[0].toInt() and 0xFF else -1
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                while (pos >= actual.size) {
                    if (!trozos.hasNext()) return -1
                    actual = trozos.next().toByteArray()
                    pos = 0
                }
                val n = minOf(len, actual.size - pos)
                actual.copyInto(b, off, pos, pos + n)
                pos += n
                return n
            }
        }
    }

    @Test
    fun keeps_the_first_title_when_the_guide_repeats_it_in_several_languages() {
        val xml = """
            <tv>
              <programme start="20240101010000 +0000" channel="A">
                <title lang="es">Título</title>
                <title lang="en">Title</title>
              </programme>
            </tv>
        """.trimIndent()

        assertEquals("Título", parser.parse(xml)[0].title)
    }
}