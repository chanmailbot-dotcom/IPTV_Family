package com.iptv.family.desktop.remote

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import java.security.SecureRandom

/**
 * Autenticacion por token compartido para el servidor de control remoto.
 *
 * El token vale como header Bearer, como query param (necesario para
 * EventSource y <video>, que no permiten headers custom) o como cookie de
 * sesion (la que planta /login para no teclear el token en cada carga). El
 * valor de la cookie ES el propio token: regenerar el token invalida
 * cualquier cookie/URL antigua al instante, sin necesitar una tabla de
 * sesiones aparte.
 */
object RemoteAuth {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val random = SecureRandom()

    const val SESSION_COOKIE = "iptv_session"

    fun generateToken(length: Int = 24): String =
        (1..length).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    fun isAuthenticated(call: ApplicationCall, expectedToken: String?): Boolean {
        if (expectedToken.isNullOrBlank()) return false
        val bearer = call.request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim()
        val query = call.request.queryParameters["token"]
        val cookie = call.request.cookies[SESSION_COOKIE]
        return expectedToken == bearer || expectedToken == query || expectedToken == cookie
    }
}
