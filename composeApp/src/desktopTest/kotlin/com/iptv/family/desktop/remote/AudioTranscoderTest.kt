package com.iptv.family.desktop.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioTranscoderTest {

    /**
     * Salida REAL de ffprobe contra un canal de la lista del usuario. Lo
     * importante: la primera pista de audio es "qad" (audiodescripcion) y la
     * segunda es el español normal, asi que hay que elegir la SEGUNDA.
     */
    private val salidaRealDeFfprobe = """
        codec_name=h264
        codec_type=video
        codec_name=aac
        codec_type=audio
        TAG:language=qad
        codec_name=aac
        codec_type=audio
        TAG:language=spa
        codec_name=dvb_subtitle
        codec_type=subtitle
        TAG:language=spa
    """.trimIndent()

    /**
     * Salida REAL de ffprobe contra el mux local del canal 1548099, con los mismos
     * flags que usa la app. Lo importante: con una fuente HLS ffprobe imprime CADA
     * stream DOS veces, primero sin etiquetas y despues con ellas.
     *
     * Contando por orden salian tres pistas de audio donde solo hay dos, el
     * español caia en la posicion 2, y `-map 0:a:2?` no existe: como lleva `?`,
     * ffmpeg lo descartaba en silencio y el navegador recibia video SIN AUDIO.
     */
    private val salidaHlsConStreamsDuplicados = """
        index=0
        codec_name=h264
        codec_type=video
        index=1
        codec_name=aac
        codec_type=audio
        index=2
        codec_name=aac
        codec_type=audio
        index=3
        codec_name=dvb_subtitle
        codec_type=subtitle
        index=0
        codec_name=h264
        codec_type=video
        index=1
        codec_name=aac
        codec_type=audio
        TAG:language=qad
        index=2
        codec_name=aac
        codec_type=audio
        TAG:language=spa
        index=3
        codec_name=dvb_subtitle
        codec_type=subtitle
        TAG:language=spa
    """.trimIndent()

    @Test
    fun `los streams repetidos de HLS no inventan pistas de audio de mas`() {
        val info = AudioTranscoder.pickAudioTrack(salidaHlsConStreamsDuplicados)
        assertEquals(2, info?.trackCount, "solo hay dos pistas de audio: qad y spa")
        // El español es la SEGUNDA de audio, o sea `-map 0:a:1`.
        assertEquals(1, info?.trackIndex)
        assertEquals("aac", info?.codec)
    }

    @Test
    fun `el indice elegido siempre cae dentro de las pistas que existen`() {
        // Es la invariante que se rompio: un indice fuera de rango deja al
        // navegador mudo sin que ffmpeg proteste.
        for (salida in listOf(salidaHlsConStreamsDuplicados, salidaRealDeFfprobe)) {
            val info = AudioTranscoder.pickAudioTrack(salida)!!
            assertTrue(
                info.trackIndex in 0 until info.trackCount,
                "pista ${info.trackIndex} fuera de rango (hay ${info.trackCount})"
            )
        }
    }

    @Test
    fun `elige la pista española y no la audiodescripcion que viene primera`() {
        val info = AudioTranscoder.pickAudioTrack(salidaRealDeFfprobe)
        assertEquals("aac", info?.codec)
        // trackIndex es el N de `-map 0:a:N`: cuenta solo las pistas de audio,
        // asi que la española (segunda de audio) es la 1, no la 2.
        assertEquals(1, info?.trackIndex)
    }

    @Test
    fun `con una sola pista de audio elige la unica que hay`() {
        val salida = """
            codec_name=h264
            codec_type=video
            codec_name=ac3
            codec_type=audio
            TAG:language=spa
        """.trimIndent()
        val info = AudioTranscoder.pickAudioTrack(salida)
        assertEquals("ac3", info?.codec)
        assertEquals(0, info?.trackIndex)
    }

    @Test
    fun `sin pistas de audio no devuelve nada`() {
        assertNull(AudioTranscoder.pickAudioTrack("codec_name=h264\ncodec_type=video"))
    }

    @Test
    fun `salida vacia no revienta`() {
        assertNull(AudioTranscoder.pickAudioTrack(""))
    }

    @Test
    fun `entre varios idiomas coge el español con su indice correcto`() {
        val salida = """
            codec_name=h264
            codec_type=video
            codec_name=ac3
            codec_type=audio
            TAG:language=eng
            codec_name=ac3
            codec_type=audio
            TAG:language=fra
            codec_name=ac3
            codec_type=audio
            TAG:language=spa
        """.trimIndent()
        assertEquals(2, AudioTranscoder.pickAudioTrack(salida)?.trackIndex)
    }

    @Test
    fun `los codecs de navegador no necesitan conversion y los demas si`() {
        assertTrue(AudioTranscoder.needsTranscode("ac3"))
        assertTrue(AudioTranscoder.needsTranscode("eac3"))
        assertTrue(AudioTranscoder.needsTranscode("mp2"))
        assertTrue(AudioTranscoder.needsTranscode("dts"))
        assertTrue(!AudioTranscoder.needsTranscode("aac"))
        assertTrue(!AudioTranscoder.needsTranscode("mp3"))
        // Codec desconocido: NO se toca, para no meter un ffmpeg a ciegas.
        assertTrue(!AudioTranscoder.needsTranscode(null))
    }
}
