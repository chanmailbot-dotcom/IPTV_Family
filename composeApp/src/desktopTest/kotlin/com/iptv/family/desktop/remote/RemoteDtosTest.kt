package com.iptv.family.desktop.remote

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El contrato JSON que consume `webui/app.js`. Con 40.000 canales por respuesta,
 * cada campo cuenta, y un cambio de nombre aqui rompe la web en silencio.
 */
class RemoteDtosTest {

    /** La misma configuracion que instala el servidor (ver RemoteWebServer). */
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `un canal de TV no gasta bytes en el tipo porque live es el defecto`() {
        val out = json.encodeToString(ChannelDto(id = "1", name = "La 1"))
        assertFalse("kind" in out, "el tipo por defecto no deberia serializarse: $out")
        // Y al leerlo de vuelta sigue siendo live, que es lo que espera la web.
        assertEquals(KIND_LIVE, json.decodeFromString<ChannelDto>(out).kind)
    }

    @Test
    fun `peliculas y series si publican su tipo`() {
        val peli = json.encodeToString(ChannelDto(id = "2", name = "Dune", kind = KIND_VOD))
        val serie = json.encodeToString(ChannelDto(id = "3", name = "Dark", kind = KIND_SERIES))
        assertTrue("\"kind\":\"vod\"" in peli, peli)
        assertTrue("\"kind\":\"series\"" in serie, serie)
    }

    @Test
    fun `el canal no lleva url para no filtrar las credenciales del panel`() {
        // Regresion: el DTO llevaba la URL de Xtream con usuario y contraseña, y se
        // enviaba para los 40.000 canales a cualquiera que abriera la web.
        val out = json.encodeToString(ChannelDto(id = "1", name = "La 1"))
        assertFalse("url" in out, out)
    }

    @Test
    fun `la categoria viaja con nombre legible, cuenta y tipo`() {
        val out = json.encodeToString(
            GroupDto(id = "142", name = "ES| CINE", count = 87, kind = KIND_VOD)
        )
        val back = json.decodeFromString<GroupDto>(out)
        assertEquals("142", back.id)
        assertEquals("ES| CINE", back.name)
        assertEquals(87, back.count)
        assertEquals(KIND_VOD, back.kind)
    }

    @Test
    fun `los codigos de tipo son los que usa app punto js`() {
        // Si alguien los renombra aqui, el filtro de la web deja de casar y se
        // queda sin resultados sin decir por que.
        assertEquals("live", KIND_LIVE)
        assertEquals("vod", KIND_VOD)
        assertEquals("series", KIND_SERIES)
    }
}
