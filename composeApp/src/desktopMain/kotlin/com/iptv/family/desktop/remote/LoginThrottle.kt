package com.iptv.family.desktop.remote

import com.iptv.family.shared.log.AppLog
import java.util.concurrent.ConcurrentHashMap

/**
 * Freno progresivo a los intentos de acceso fallidos.
 *
 * Mientras el servidor solo era accesible por VPN esto no hacia falta. Publicada
 * la aplicacion, habra quien abra el puerto en su router: sin freno, una
 * contraseña de seis caracteres se prueba entera en minutos, y no quedaria ni
 * rastro en el log de que alguien lo intento.
 *
 * El retardo crece con los fallos en vez de bloquear de golpe: a quien se
 * equivoca escribiendo apenas le molesta, y a quien prueba en serie le arruina
 * el ritmo. Un acierto lo borra todo.
 *
 *   fallos  1-2 -> sin espera
 *        3      -> 2 s
 *        4      -> 4 s
 *        5      -> 8 s
 *        ...
 *        >=9    -> 5 min (tope)
 *
 * Se cuenta por IP y por usuario a la vez: por IP para frenar a quien prueba
 * muchos usuarios desde un sitio, y por usuario para frenar a quien prueba el
 * mismo usuario desde muchos sitios.
 */
class LoginThrottle(private val now: () -> Long = System::currentTimeMillis) {

    private class Contador(var fallos: Int = 0, var bloqueadoHasta: Long = 0L, var visto: Long = 0L)

    private val porClave = ConcurrentHashMap<String, Contador>()

    /**
     * Milisegundos que faltan para poder volver a intentarlo, o 0 si se puede ya.
     * No consume ningun intento: solo consulta.
     */
    fun esperaPendiente(vararg claves: String): Long {
        limpiar()
        val ahora = now()
        return claves.mapNotNull { porClave[it] }
            .maxOfOrNull { (it.bloqueadoHasta - ahora).coerceAtLeast(0L) } ?: 0L
    }

    /** Anota un intento fallido y devuelve cuanto habra que esperar a partir de ahora. */
    fun fallo(vararg claves: String): Long {
        val ahora = now()
        var espera = 0L
        for (clave in claves) {
            val c = porClave.getOrPut(clave) { Contador() }
            synchronized(c) {
                c.fallos++
                c.visto = ahora
                val castigo = castigoPara(c.fallos)
                c.bloqueadoHasta = ahora + castigo
                if (castigo > espera) espera = castigo
            }
        }
        if (espera > 0) {
            AppLog.w("RemoteAuth", "acceso fallido; siguiente intento en ${espera / 1000}s")
        }
        return espera
    }

    /** Acierto: se olvida todo lo anterior. */
    fun acierto(vararg claves: String) {
        claves.forEach { porClave.remove(it) }
    }

    private fun castigoPara(fallos: Int): Long = when {
        fallos < UMBRAL -> 0L
        else -> {
            val pasos = fallos - UMBRAL
            (BASE_MS shl pasos.coerceAtMost(30)).coerceAtMost(TOPE_MS)
        }
    }

    /** Los contadores viejos se tiran: si no, la memoria crece con cada IP que pase. */
    private fun limpiar() {
        val limite = now() - OLVIDO_MS
        if (porClave.size < 512) return
        porClave.entries.removeIf { it.value.visto < limite }
    }

    private companion object {
        /** Los dos primeros fallos salen gratis: escribir mal la contraseña es normal. */
        const val UMBRAL = 3
        const val BASE_MS = 2_000L
        const val TOPE_MS = 5 * 60 * 1000L
        const val OLVIDO_MS = 60 * 60 * 1000L
    }
}
