package com.iptv.family.shared.data.store

import com.iptv.family.shared.log.AppLog

/**
 * Protege los secretos que hay que guardar en disco: hoy, la contraseña del
 * panel Xtream.
 *
 * Se guardaban en claro dentro de `playlists.json`. En un PC personal era
 * defensa en profundidad; publicada la aplicacion, son las credenciales de otra
 * persona, en un fichero que acaba en copias de seguridad, en carpetas
 * sincronizadas y en cualquier volcado que alguien comparta para pedir ayuda.
 *
 * Cada plataforma pone lo suyo (DPAPI en Windows, Keystore en Android), asi que
 * la implementacion se inyecta desde el modulo de aplicacion. `shared` no puede
 * depender ni de JNA ni del SDK de Android.
 */
interface SecretVault {

    /** Cifra un secreto. Devuelve null si esta plataforma no sabe hacerlo. */
    fun protect(plain: String): String?

    /** Descifra lo que produjo [protect]. Devuelve null si no se puede. */
    fun reveal(token: String): String?

    companion object {
        /**
         * Marca de lo ya cifrado. Sirve para distinguirlo del texto plano de las
         * instalaciones anteriores y poder convertirlo sin que el usuario note
         * nada.
         */
        const val PREFIX = "enc:v1:"

        /** El que se usa cuando la plataforma no ofrece nada. No cifra, y lo dice. */
        val NONE: SecretVault = object : SecretVault {
            override fun protect(plain: String): String? = null
            override fun reveal(token: String): String? = null
        }
    }
}

/**
 * Cifra si se puede; si no, devuelve el texto tal cual.
 *
 * No se rompe el guardado por no poder cifrar: es preferible una aplicacion que
 * funciona con un aviso en el log a una que se niega a guardar la lista.
 */
fun SecretVault.protectOrPlain(plain: String): String {
    if (plain.isEmpty()) return plain
    if (plain.startsWith(SecretVault.PREFIX)) return plain // ya estaba cifrado
    val cifrado = runCatching { protect(plain) }.getOrNull()
    if (cifrado == null) {
        AppLog.w("Vault", "esta plataforma no puede cifrar: el secreto se guarda en claro")
        return plain
    }
    return SecretVault.PREFIX + cifrado
}

/**
 * Descifra lo que lo necesite y deja pasar lo demas.
 *
 * El texto sin marca es de una instalacion anterior: se devuelve tal cual, y al
 * siguiente guardado quedara cifrado. Asi la conversion es transparente.
 */
fun SecretVault.revealOrPlain(stored: String): String {
    if (!stored.startsWith(SecretVault.PREFIX)) return stored
    val claro = runCatching { reveal(stored.removePrefix(SecretVault.PREFIX)) }.getOrNull()
    if (claro == null) {
        // Pasa si se copian los datos a otro equipo o a otro usuario: el cifrado
        // esta atado a la cuenta. Mejor decirlo que devolver basura silenciosa.
        AppLog.e("Vault", "no se pudo descifrar un secreto (¿datos de otro equipo o usuario?)")
        return ""
    }
    return claro
}
