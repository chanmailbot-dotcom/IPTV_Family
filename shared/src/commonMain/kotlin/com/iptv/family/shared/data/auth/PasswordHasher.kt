package com.iptv.family.shared.data.auth

import com.iptv.family.shared.model.WebRole
import com.iptv.family.shared.model.WebUser
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hasheo de contraseñas de los usuarios del servidor web.
 *
 * PBKDF2-HMAC-SHA256 con sal por usuario. No se guarda la contraseña en ningun
 * momento: `settings.json` vive en texto plano en el disco del usuario y acaba
 * en copias de seguridad, asi que guardar contraseñas ahi seria regalarlas.
 */
object PasswordHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    /** Iteraciones por defecto: coste de ~100 ms, suficiente para un servidor domestico. */
    const val DEFAULT_ITERATIONS = 120_000

    private val random = SecureRandom()
    private val encoder: Base64.Encoder = Base64.getEncoder()
    private val decoder: Base64.Decoder = Base64.getDecoder()

    fun createUser(
        username: String,
        password: String,
        role: WebRole,
        iterations: Int = DEFAULT_ITERATIONS,
    ): WebUser {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return WebUser(
            username = username.trim(),
            passwordHash = encoder.encodeToString(derive(password, salt, iterations)),
            salt = encoder.encodeToString(salt),
            iterations = iterations,
            role = role,
        )
    }

    /** Devuelve el mismo usuario con la contraseña cambiada (misma sal nueva). */
    fun withNewPassword(user: WebUser, password: String): WebUser =
        createUser(user.username, password, user.role, user.iterations)
            .copy(createdAt = user.createdAt)

    /**
     * true si [password] es la contraseña de [user].
     *
     * La comparacion es en tiempo constante (MessageDigest.isEqual): comparar
     * hashes con `==` filtra por el tiempo de respuesta cuantos bytes iniciales
     * coinciden, que es justo la pista que necesita un ataque por temporizacion.
     */
    fun verify(user: WebUser, password: String): Boolean {
        val expected = runCatching { decoder.decode(user.passwordHash) }.getOrNull() ?: return false
        val salt = runCatching { decoder.decode(user.salt) }.getOrNull() ?: return false
        val actual = runCatching { derive(password, salt, user.iterations) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }

    /** Token de sesion opaco y aleatorio (no deriva de la contraseña). */
    fun newSessionToken(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
