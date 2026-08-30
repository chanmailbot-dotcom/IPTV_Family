package com.iptv.family.shared.util

/**
 * Cuánto hace de un instante, en palabras.
 *
 * Se usa para avisar de que lo que se está viendo es una copia guardada. La
 * hora exacta no le dice nada a nadie desde el sofá; lo que importa es si son
 * datos de hace un rato o de la semana pasada, porque de eso depende que el
 * usuario se fíe de la lista o la refresque.
 */
fun textoAntiguedad(instanteMs: Long, ahoraMs: Long = System.currentTimeMillis()): String {
    val minutos = (ahoraMs - instanteMs) / 60_000
    return when {
        // Un reloj mal puesto (o una copia recién hecha) no puede producir
        // "hace -3 horas": en la duda, se dice que es de ahora mismo.
        minutos < 2 -> "hace un momento"
        minutos < 60 -> "hace $minutos minutos"
        minutos < 120 -> "hace una hora"
        minutos < 24 * 60 -> "hace ${minutos / 60} horas"
        minutos < 48 * 60 -> "ayer"
        else -> "hace ${minutos / (24 * 60)} días"
    }
}
