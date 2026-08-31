package com.iptv.family.shared.util

import com.iptv.family.shared.i18n.T

/**
 * Cuánto hace de un instante, en palabras.
 *
 * Se usa para avisar de que lo que se está viendo es una copia guardada. La
 * hora exacta no le dice nada a nadie desde el sofá; lo que importa es si son
 * datos de hace un rato o de la semana pasada, porque de eso depende que el
 * usuario se fíe de la lista o la refresque.
 */
fun textoAntiguedad(instanteMs: Long, ahoraMs: Long = System.currentTimeMillis()): String {
    // Un reloj mal puesto (o una copia recién hecha) no puede producir
    // "hace -3 horas": las traducciones tratan todo lo menor que dos minutos
    // como "hace un momento", asi que en la duda se dice que es de ahora.
    val minutos = (ahoraMs - instanteMs) / 60_000
    return T.antiguedad(minutos)
}

