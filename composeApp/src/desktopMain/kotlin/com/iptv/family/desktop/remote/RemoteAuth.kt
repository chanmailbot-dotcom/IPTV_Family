package com.iptv.family.desktop.remote

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import java.security.SecureRandom

/** Quien esta al otro lado: manda o solo mira. */
enum class RemoteRole {
    /** Control total: cambiar de canal, favoritos, volumen, pausa. */
    ADMIN,

    /** Solo ver lo que el administrador ha puesto. Nada de escritura. */
    VIEWER,
    ;

    val isAdmin: Boolean get() = this == ADMIN
}

/**
 * Autenticacion por token compartido para el servidor de control remoto.
 *
 * Hay dos tokens independientes ([RemoteRole]): el de administrador y el de
 * invitado. El token vale como header Bearer, como query param (necesario para
 * EventSource y <video>, que no permiten headers custom) o como cookie de
 * sesion (la que planta /login para no teclear el token en cada carga). El
 * valor de la cookie ES el propio token: regenerar un token invalida cualquier
 * cookie/URL antigua al instante, sin necesitar una tabla de sesiones aparte
 * -- y como el rol se deduce de comparar contra el token vigente, degradar a
 * un invitado es tan simple como regenerar su token.
 */
object RemoteAuth {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val random = SecureRandom()

    const val SESSION_COOKIE = "iptv_session"

    fun generateToken(length: Int = 24): String =
        (1..length).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /**
     * Rol de esta peticion, o null si el token no vale para nada.
     *
     * El de administrador gana: si por accidente ambos tokens fueran iguales,
     * el acceso es de administrador y no un invitado con permisos de mas.
     */
    fun roleFor(call: ApplicationCall, adminToken: String?, viewerToken: String?): RemoteRole? {
        val presented = presentedTokens(call)
        if (presented.isEmpty()) return null
        if (!adminToken.isNullOrBlank() && presented.any { it == adminToken }) return RemoteRole.ADMIN
        if (!viewerToken.isNullOrBlank() && presented.any { it == viewerToken }) return RemoteRole.VIEWER
        return null
    }

    /**
     * Todos los sitios de los que puede venir un token, sin quedarse en el
     * primero: un invitado puede tener la cookie de invitado plantada y aun asi
     * abrir un enlace `?token=` de administrador (o al reves). Si solo se mirara
     * el primero que aparece, el enlace nuevo no tendria efecto hasta borrar la
     * cookie a mano.
     */
    private fun presentedTokens(call: ApplicationCall): List<String> = listOfNotNull(
        call.request.queryParameters["token"],
        call.request.cookies[SESSION_COOKIE],
        call.request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim(),
    ).filter { it.isNotBlank() }

    /** Recupera el token con el que autentico esta peticion (query, cookie o Bearer), o null. */
    fun callToken(call: ApplicationCall): String? = presentedTokens(call).firstOrNull()
}
