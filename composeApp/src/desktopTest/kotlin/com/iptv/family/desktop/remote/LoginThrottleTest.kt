package com.iptv.family.desktop.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoginThrottleTest {

    /** Reloj controlado: probar esperas con relojes de verdad da tests lentos y fragiles. */
    private class Reloj(var t: Long = 0L) { fun ahora() = t }

    @Test
    fun `los dos primeros fallos no penalizan`() {
        val r = Reloj(); val f = LoginThrottle(r::ahora)
        assertEquals(0L, f.fallo("ip"))
        assertEquals(0L, f.fallo("ip"))
        assertEquals(0L, f.esperaPendiente("ip"), "equivocarse un par de veces es normal")
    }

    @Test
    fun `a partir del tercero la espera crece`() {
        val r = Reloj(); val f = LoginThrottle(r::ahora)
        f.fallo("ip"); f.fallo("ip")
        assertEquals(2_000L, f.fallo("ip"))
        assertEquals(4_000L, f.fallo("ip"))
        assertEquals(8_000L, f.fallo("ip"))
    }

    @Test
    fun `la espera tiene tope`() {
        val r = Reloj(); val f = LoginThrottle(r::ahora)
        repeat(40) { f.fallo("ip") }
        assertEquals(5 * 60 * 1000L, f.esperaPendiente("ip"), "sin tope, un ataque dejaria la cuenta inservible para siempre")
    }

    @Test
    fun `la espera se agota con el tiempo`() {
        val r = Reloj(); val f = LoginThrottle(r::ahora)
        repeat(3) { f.fallo("ip") }
        assertEquals(2_000L, f.esperaPendiente("ip"))
        r.t += 2_500
        assertEquals(0L, f.esperaPendiente("ip"))
    }

    @Test
    fun `acertar borra el castigo`() {
        val r = Reloj(); val f = LoginThrottle(r::ahora)
        repeat(5) { f.fallo("ip", "usuario:papa") }
        assertTrue(f.esperaPendiente("ip") > 0)
        f.acierto("ip", "usuario:papa")
        assertEquals(0L, f.esperaPendiente("ip", "usuario:papa"))
    }

    @Test
    fun `se frena por IP y por usuario a la vez`() {
        val r = Reloj(); val f = LoginThrottle(r::ahora)
        // Alguien prueba el mismo usuario desde IPs distintas: la IP cambia pero
        // el usuario acumula, asi que igualmente se frena.
        f.fallo("ip:1", "usuario:papa")
        f.fallo("ip:2", "usuario:papa")
        f.fallo("ip:3", "usuario:papa")
        assertEquals(0L, f.esperaPendiente("ip:4"), "una IP nueva no arrastra castigo ajeno")
        assertTrue(f.esperaPendiente("ip:4", "usuario:papa") > 0, "pero el usuario si")
    }

    @Test
    fun `consultar no gasta intentos`() {
        val r = Reloj(); val f = LoginThrottle(r::ahora)
        repeat(3) { f.fallo("ip") }
        val primera = f.esperaPendiente("ip")
        assertEquals(primera, f.esperaPendiente("ip"), "preguntar no debe empeorar el castigo")
    }
}
