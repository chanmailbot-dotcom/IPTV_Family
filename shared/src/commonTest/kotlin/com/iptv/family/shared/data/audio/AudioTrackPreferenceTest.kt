package com.iptv.family.shared.data.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioTrackPreferenceTest {

    private data class Track(val lang: String?, val title: String? = null)

    private fun pick(vararg tracks: Track): Track? =
        AudioTrackPreference.preferred(tracks.toList(), { it.lang }, { it.title })

    /**
     * El caso real que motivo todo esto: un canal de la lista del usuario trae la
     * audiodescripcion como PRIMERA pista, asi que "coger la primera" (lo que hacen
     * VLC y ffmpeg por defecto) pone al narrador describiendo la escena.
     */
    @Test
    fun `prefiere el espanol normal antes que la audiodescripcion que viene primera`() {
        assertEquals(Track("spa"), pick(Track("qad"), Track("spa")))
    }

    @Test
    fun `elige espanol entre varios idiomas`() {
        assertEquals(Track("spa"), pick(Track("eng"), Track("fra"), Track("spa")))
    }

    @Test
    fun `reconoce las variantes del codigo de espanol`() {
        for (code in listOf("es", "spa", "esp", "cast", "castellano", "es-ES")) {
            assertEquals(
                Track(code),
                pick(Track("eng"), Track(code)),
                "no reconocio '$code' como espanol",
            )
        }
    }

    @Test
    fun `detecta audiodescripcion por el titulo aunque el idioma sea spa`() {
        val normal = Track("spa", "Español")
        val descripcion = Track("spa", "Español (audiodescripción)")
        assertEquals(normal, pick(descripcion, normal))
    }

    @Test
    fun `sin espanol se queda con la primera en vez de no elegir nada`() {
        assertEquals(Track("eng"), pick(Track("eng"), Track("fra")))
    }

    @Test
    fun `una pista sin idioma marcado gana a un idioma extranjero`() {
        // En la practica la pista sin etiquetar suele ser el audio principal.
        assertEquals(Track("und"), pick(Track("und"), Track("deu")))
    }

    @Test
    fun `prefiere el doblaje antes que la version original subtitulada`() {
        val doblada = Track("spa", "Castellano")
        val vos = Track("spa", "VOS español")
        assertEquals(doblada, pick(vos, doblada))
    }

    @Test
    fun `con una sola pista no propone cambiar`() {
        assertFalse(
            AudioTrackPreference.shouldSwitch(listOf(Track("qad")), 0, { it.lang }, { it.title })
        )
    }

    @Test
    fun `propone cambiar si la pista actual es peor que la preferida`() {
        val tracks = listOf(Track("qad"), Track("spa"))
        assertTrue(AudioTrackPreference.shouldSwitch(tracks, 0, { it.lang }, { it.title }))
        assertFalse(AudioTrackPreference.shouldSwitch(tracks, 1, { it.lang }, { it.title }))
    }

    @Test
    fun `nombres legibles para el selector`() {
        assertEquals("Español", AudioTrackPreference.displayName("spa"))
        assertEquals("Audiodescripción", AudioTrackPreference.displayName("qad"))
        assertEquals("Inglés", AudioTrackPreference.displayName("eng"))
        assertEquals("Catalán", AudioTrackPreference.displayName("cat"))
        assertEquals("Original", AudioTrackPreference.displayName(null))
        // Un codigo desconocido se muestra tal cual, mejor que inventar una etiqueta.
        assertEquals("zul", AudioTrackPreference.displayName("zul"))
    }
}
