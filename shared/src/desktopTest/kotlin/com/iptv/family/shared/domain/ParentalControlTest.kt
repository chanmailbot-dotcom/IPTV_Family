package com.iptv.family.shared.domain

import com.iptv.family.shared.model.UserSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParentalControlTest {

    private val activo = UserSettings(isParentalLockEnabled = true, parentalPin = "1234")
    private val inactivo = UserSettings(isParentalLockEnabled = false, parentalPin = "1234")

    // ---------- Que se considera "para adultos" ----------

    @Test
    fun `reconoce las formas habituales de nombrarlo`() {
        // Los proveedores lo nombran de mil maneras; se compara sin distinguir
        // mayusculas y por texto contenido.
        listOf("ADULTOS", "Adult Movies", "ES| XXX", "Canales 18+", "adult").forEach {
            assertTrue(ParentalControl.isAdultCategory(it, activo), "deberia bloquear «$it»")
        }
    }

    @Test
    fun `no bloquea categorias normales`() {
        listOf("Deportes", "ES| CINE", "Infantil", "Documentales").forEach {
            assertFalse(ParentalControl.isAdultCategory(it, activo), "no deberia bloquear «$it»")
        }
    }

    @Test
    fun `con el control desactivado no bloquea nada`() {
        assertFalse(ParentalControl.isAdultCategory("XXX", inactivo))
    }

    @Test
    fun `un nombre vacio o nulo no bloquea`() {
        assertFalse(ParentalControl.isAdultCategory(null, activo))
        assertFalse(ParentalControl.isAdultCategory("   ", activo))
    }

    @Test
    fun `un patron vacio no bloquea la lista entera`() {
        // Si un patron es cadena vacia, `contains("")` es SIEMPRE cierto: sin el
        // filtro, activar el control ocultaria todos los canales.
        val conVacio = activo.copy(adultCategoryNames = listOf("", "xxx"))
        assertFalse(ParentalControl.isAdultCategory("Deportes", conVacio))
        assertTrue(ParentalControl.isAdultCategory("XXX", conVacio))
    }

    // ---------- Cuando se pide el PIN ----------

    @Test
    fun `una categoria ya desbloqueada no vuelve a pedir el PIN`() {
        assertTrue(ParentalControl.requiresPin("c1", "Adultos", activo, emptySet()))
        assertFalse(ParentalControl.requiresPin("c1", "Adultos", activo, setOf("c1")))
    }

    // ---------- Estado utilizable ----------

    @Test
    fun `activarlo sin PIN se detecta como no utilizable`() {
        // Era una trampa: con el interruptor puesto y sin PIN, la comprobacion
        // nunca podia dar bien y la categoria quedaba tapiada para siempre.
        assertFalse(ParentalControl.isUsable(activo.copy(parentalPin = null)))
        assertFalse(ParentalControl.isUsable(activo.copy(parentalPin = "")))
        assertTrue(ParentalControl.isUsable(activo))
        assertTrue(ParentalControl.isUsable(inactivo.copy(parentalPin = null)), "desactivado siempre es utilizable")
    }

    // ---------- PIN ----------

    @Test
    fun `el PIN no se guarda en claro`() {
        val guardado = ParentalControl.hashPin("2468")
        assertFalse("2468" in guardado, "el PIN aparece tal cual: $guardado")
        assertTrue(ParentalControl.checkPin("2468", guardado))
        assertFalse(ParentalControl.checkPin("1357", guardado))
    }

    @Test
    fun `dos PIN iguales dan cadenas distintas`() {
        // Con sal: si no, comparar dos ficheros de ajustes revelaria que dos
        // instalaciones usan el mismo PIN.
        assertTrue(ParentalControl.hashPin("1234") != ParentalControl.hashPin("1234"))
    }

    @Test
    fun `un PIN antiguo guardado en claro se sigue aceptando`() {
        // Al actualizar, quien ya tenia PIN no puede quedarse fuera de sus
        // propias categorias.
        assertTrue(ParentalControl.checkPin("1234", "1234"))
        assertFalse(ParentalControl.checkPin("9999", "1234"))
        assertTrue(ParentalControl.isLegacyPin("1234"))
        assertFalse(ParentalControl.isLegacyPin(ParentalControl.hashPin("1234")))
    }

    @Test
    fun `sin PIN guardado no se desbloquea con nada`() {
        assertFalse(ParentalControl.checkPin("1234", null))
        assertFalse(ParentalControl.checkPin("1234", ""))
        assertFalse(ParentalControl.checkPin("", "1234"), "un PIN vacio no vale")
    }

    @Test
    fun `un formato corrupto no abre la puerta`() {
        assertFalse(ParentalControl.checkPin("1234", "pbkdf2:basura"))
        assertFalse(ParentalControl.checkPin("1234", "pbkdf2:no-es-base64:tampoco"))
    }

    @Test
    fun `la longitud aceptada es la de un mando`() {
        assertEquals(4, ParentalControl.MIN_PIN_LENGTH)
        assertEquals(6, ParentalControl.MAX_PIN_LENGTH)
    }
}
