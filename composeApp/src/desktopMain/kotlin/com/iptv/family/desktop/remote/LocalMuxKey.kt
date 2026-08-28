package com.iptv.family.desktop.remote

import com.iptv.family.shared.data.auth.PasswordHasher

/**
 * Clave interna para que los consumidores LOCALES del mux (el VLC del escritorio
 * y el ffmpeg que convierte el audio) puedan leer `/stream/...` sin tener una
 * cuenta de usuario. La app de escritorio es el servidor: no tiene sentido que
 * se identifique contra si misma.
 *
 * Se genera al azar en cada arranque y nunca sale de este proceso: no aparece en
 * ninguna respuesta HTTP ni se guarda en disco. Solo viaja dentro de las URLs
 * que el propio escritorio construye para VLC y ffmpeg.
 *
 * NO sirve comprobar simplemente que la peticion venga de 127.0.0.1: cuando se
 * publica el puerto con ngrok, ngrok conecta desde loopback, asi que cualquiera
 * de internet pareceria local y entraria sin identificarse.
 */
object LocalMuxKey {
    /** Nombre del parametro de query que la lleva. */
    const val PARAM = "k"

    val value: String by lazy { PasswordHasher.newSessionToken() }
}
