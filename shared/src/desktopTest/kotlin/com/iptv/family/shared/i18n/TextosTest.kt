package com.iptv.family.shared.i18n

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La interfaz garantiza que ningun texto FALTE, pero no que se haya traducido:
 * copiar el castellano en la clase inglesa compila igual de bien. Estas pruebas
 * cubren ese hueco.
 */
class TextosTest {

    @AfterTest
    fun restaurar() = Textos.usar("es")

    @Test
    fun no_english_string_is_left_in_spanish() {
        val es = todosLosTextos(TextosEs)
        val en = todosLosTextos(TextosEn)

        // Los nombres propios y las palabras que se escriben igual en los dos
        // idiomas no cuentan: son iguales porque deben serlo.
        val iguales = es.keys
            .filter { es[it] == en[it] }
            .filterNot { it in COINCIDENCIAS_LEGITIMAS }

        assertTrue(iguales.isEmpty(), "Sin traducir al inglés: $iguales")
    }

    @Test
    fun no_english_string_carries_spanish_accents() {
        val sospechosos = todosLosTextos(TextosEn)
            .filterValues { texto -> texto.any { it in "áéíóúñ¿¡" } }
        assertTrue(sospechosos.isEmpty(), "Textos ingleses con tildes o signos españoles: ${sospechosos.keys}")
    }

    @Test
    fun the_language_falls_back_to_english_for_anything_else() {
        Textos.usar("es")
        assertEquals("Cancelar", T.cancelar)
        Textos.usar("fr")
        assertEquals("Cancel", T.cancelar, "Un idioma sin traducir tiene que caer en inglés, no romperse")
    }

    @Test
    fun counts_agree_in_number() {
        Textos.usar("es")
        assertEquals("1 canal", T.cuentaCanales(1))
        assertEquals("7 canales", T.cuentaCanales(7))
        Textos.usar("en")
        assertEquals("1 channel", T.cuentaCanales(1))
        assertEquals("7 channels", T.cuentaCanales(7))
    }

    @Test
    fun no_string_is_empty() {
        for ((idioma, textos) in listOf("es" to TextosEs, "en" to TextosEn)) {
            val vacios = todosLosTextos(textos).filterValues { it.isBlank() }
            assertTrue(vacios.isEmpty(), "Textos vacíos en '$idioma': ${vacios.keys}")
        }
    }

    @Test
    fun the_saved_list_notice_keeps_the_age_inside() {
        Textos.usar("en")
        val aviso = T.avisoListaGuardada(T.antiguedad(180))
        assertTrue(aviso.contains("3 hours ago"), "El aviso debe incorporar la antigüedad: $aviso")
        assertFalse(aviso.contains("hace"), "y no en castellano: $aviso")
    }

    /**
     * Lee por reflexion todas las propiedades de texto. Asi una cadena nueva
     * entra sola en estas pruebas, sin que haya que acordarse de añadirla.
     */
    private fun todosLosTextos(textos: Textos): Map<String, String> =
        Textos::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == String::class.java && it.name.startsWith("get") }
            .associate { it.name.removePrefix("get") to (it.invoke(textos) as String) }

    private companion object {
        /** Textos que coinciden en ambos idiomas con razon. */
        val COINCIDENCIAS_LEGITIMAS = setOf(
            "Series",      // igual en los dos
            "Audio",       // igual en los dos
            "ListaXtream", // nombre propio del protocolo
        )
    }
}
