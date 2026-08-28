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
