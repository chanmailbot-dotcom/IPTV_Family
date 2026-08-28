package com.iptv.family.desktop.remote

import com.iptv.family.shared.data.auth.PasswordHasher
import com.iptv.family.shared.model.WebRole
import com.iptv.family.shared.model.WebUser
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import java.util.concurrent.ConcurrentHashMap

/** Sesion abierta de un usuario en un navegador concreto. */
data class RemoteSession(
    val token: String,
    val username: String,
    val role: WebRole,
    val createdAt: Long,
    @Volatile var lastSeenAt: Long,
) {
    val isAdmin: Boolean get() = role == WebRole.ADMIN
}

/**
 * Autenticacion del servidor de control remoto: usuario + contraseña, con
 * sesiones en memoria.
 *
 * Antes esto era un token compartido que iba en la URL. Se cambio a cuentas
 * porque el token no identifica a nadie (no se sabe quien lo esta usando), no se
 * puede revocar a una sola persona sin echar a todas, y viajaba a la vista en
 * los enlaces. Ahora cada persona tiene su cuenta y el administrador puede
 * crearlas, cambiarles la contraseña o borrarlas.
 *
 * Las sesiones viven en memoria a proposito: al reiniciar la app de escritorio
 * todo el mundo vuelve a identificarse, que es el comportamiento seguro por
 * defecto y evita tener que guardar (y caducar) tokens en disco.
 */
object RemoteAuth {

    const val SESSION_COOKIE = "iptv_session"

    /** Caducidad de una sesion sin actividad. */
    private const val SESSION_IDLE_TIMEOUT_MS = 30L * 24 * 60 * 60 * 1000 // 30 dias

    private val sessions = ConcurrentHashMap<String, RemoteSession>()

    // ------------------------------------------------------------------
    // Login / logout
    // ------------------------------------------------------------------

    /**
     * Comprueba las credenciales y abre una sesion. Devuelve null si el usuario
     * no existe o la contraseña no es correcta -- deliberadamente sin distinguir
     * entre ambos casos, para no confirmar que un usuario existe.
     */
    fun login(users: List<WebUser>, username: String, password: String): RemoteSession? {
        val user = users.firstOrNull { it.username.equals(username.trim(), ignoreCase = true) }
        if (user == null) {
            // Se calcula un hash de todas formas para que un usuario inexistente
            // tarde lo mismo que uno con contraseña incorrecta: si no, el tiempo de
            // respuesta revelaria que cuentas existen.
            PasswordHasher.verify(DUMMY_USER, password)
            return null
        }
        if (!PasswordHasher.verify(user, password)) return null

        val now = System.currentTimeMillis()
        val session = RemoteSession(
            token = PasswordHasher.newSessionToken(),
            username = user.username,
            role = user.role,
            createdAt = now,
            lastSeenAt = now,
        )
        sessions[session.token] = session
        purgeExpired()
        return session
    }

    fun logout(call: ApplicationCall) {
        sessionToken(call)?.let { sessions.remove(it) }
    }

    /** Cierra todas las sesiones de un usuario (al borrarlo o cambiarle la contraseña). */
    fun revokeSessionsOf(username: String) {
        sessions.entries.removeIf { it.value.username.equals(username, ignoreCase = true) }
    }

    fun revokeAll() = sessions.clear()

    // ------------------------------------------------------------------
    // Consulta
    // ------------------------------------------------------------------

    /**
     * Sesion de esta peticion, o null si no hay ninguna valida.
     *
     * [users] sirve para revalidar el rol en cada peticion: si el administrador
     * degrada a alguien a invitado, no hay que esperar a que cierre sesion.
     */
    fun sessionFor(call: ApplicationCall, users: List<WebUser>): RemoteSession? {
        val token = sessionToken(call) ?: return null
        val session = sessions[token] ?: return null
        val now = System.currentTimeMillis()
        if (now - session.lastSeenAt > SESSION_IDLE_TIMEOUT_MS) {
            sessions.remove(token)
            return null
        }
        // El usuario pudo ser borrado (o degradado) mientras su sesion seguia viva.
        val user = users.firstOrNull { it.username.equals(session.username, ignoreCase = true) }
        if (user == null) {
            sessions.remove(token)
            return null
        }
        session.lastSeenAt = now
        return if (user.role == session.role) session else session.copy(role = user.role)
            .also { sessions[token] = it }
    }

    /**
     * El token de sesion de esta peticion, buscando en cookie, query y Bearer.
     *
     * La query es imprescindible: `<video>`, hls.js y EventSource no pueden
     * mandar cabeceras propias, asi que el reproductor y el canal de eventos
     * autentican por ahi.
     */
    fun sessionToken(call: ApplicationCall): String? = listOfNotNull(
        call.request.cookies[SESSION_COOKIE],
        call.request.queryParameters["s"],
        call.request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim(),
    ).firstOrNull { it.isNotBlank() }

    private fun purgeExpired() {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { now - it.value.lastSeenAt > SESSION_IDLE_TIMEOUT_MS }
    }

    /**
     * Usuario de pega, solo para gastar el mismo tiempo de CPU cuando el nombre
     * de usuario no existe (ver [login]).
     */
    private val DUMMY_USER: WebUser by lazy {
        PasswordHasher.createUser("__nadie__", "contraseña que no vale", WebRole.VIEWER)
    }
}
