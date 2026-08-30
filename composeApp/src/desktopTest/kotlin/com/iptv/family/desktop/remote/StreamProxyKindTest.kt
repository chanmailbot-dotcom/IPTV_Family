package com.iptv.family.desktop.remote

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Distinguir un segmento de directo de un fichero completo decide como se sirve:
 * el segmento (~7 MB) se baja entero y se cachea, mientras que una pelicula o un
 * episodio (cientos de MB) hay que reenviarlo segun llega.
 *
 * Confundirlos tiene consecuencias visibles: bajar un episodio entero antes de
 * responder agota el tiempo de espera y no reproduce nada, que es lo que pasaba
 * al abrir cualquier episodio de serie.
 */
class StreamProxyKindTest {

    @Test
    fun `las rutas de pelicula y serie de Xtream son ficheros completos`() {
        assertTrue(StreamProxy.looksProgressive("http://panel/series/usuario/clave/2131010.mkv"))
        assertTrue(StreamProxy.looksProgressive("http://panel/movie/usuario/clave/38230.mp4"))
        // Tambien con extension .ts, que Xtream usa a veces para peliculas: aqui
        // manda la RUTA, no la extension.
        assertTrue(StreamProxy.looksProgressive("http://panel/movie/usuario/clave/999.ts"))
    }

    @Test
    fun `un segmento de directo no lo es`() {
        assertFalse(StreamProxy.looksProgressive("http://cdn/hls/abc123/1548099_3354.ts"))
        assertFalse(StreamProxy.looksProgressive("http://panel/live/usuario/clave/1548099.m3u8"))
    }

    @Test
    fun `en listas M3U se reconoce por la extension`() {
        assertTrue(StreamProxy.looksProgressive("http://cualquier/sitio/peli.mkv"))
        assertTrue(StreamProxy.looksProgressive("http://cualquier/sitio/peli.mp4?token=abc"))
        assertFalse(StreamProxy.looksProgressive("http://cualquier/sitio/canal.m3u8"))
    }

    @Test
    fun `los parametros de la url no confunden la deteccion`() {
        assertTrue(StreamProxy.looksProgressive("http://panel/series/u/c/1.mkv?x=.m3u8"))
        assertFalse(StreamProxy.looksProgressive("http://cdn/seg.ts?src=peli.mkv"))
    }

    @Test
    fun `un manifiesto se reconoce como tal`() {
        assertTrue(StreamProxy.looksLikeManifest("http://panel/live/u/c/1.m3u8"))
        assertFalse(StreamProxy.looksLikeManifest("http://panel/series/u/c/1.mkv"))
    }
}
