package com.iptv.family.shared.domain

import com.iptv.family.shared.model.UserSettings
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Control parental: que categorias se consideran para adultos y como se
 * comprueba el PIN.
 *
 * Vive en `shared` porque la regla tiene que ser LA MISMA en escritorio y en
 * Android. Estaba escrita solo en la pantalla de canales del escritorio, y
 * Android se limitaba a ofrecer un interruptor que no filtraba nada: una
 * proteccion infantil que no protege es peor que no tenerla, porque da
 * confianza falsa.
 */
object ParentalControl {

    /**
     * ¿Es una categoria de adultos?
     *
     * Comparacion por texto contenido y sin distinguir mayusculas, porque los
     * proveedores nombran esto de mil formas ("ADULTOS", "XXX 18+", "Adult
     * Movies"). Falta acentos a proposito: los patrones son palabras sin ellos.
     */
    fun isAdultCategory(name: String?, settings: UserSettings): Boolean {
        if (!settings.isParentalLockEnabled) return false
        val limpio = name?.trim().orEmpty()
        if (limpio.isEmpty()) return false
        return settings.adultCategoryNames.any { patron ->
            patron.isNotBlank() && limpio.contains(patron, ignoreCase = true)
        }
    }

    /**
     * ¿Hay que pedir el PIN para entrar en esta categoria?
     *
     * @param unlocked categorias ya desbloqueadas en esta sesion; el PIN se pide
     *   una vez, no en cada clic.
     */
    fun requiresPin(categoryId: String?, categoryName: String?, settings: UserSettings, unlocked: Set<String>): Boolean =
        isAdultCategory(categoryName, settings) && categoryId != null && categoryId !in unlocked

    /**
     * ¿Esta el control parental configurado y utilizable?
     *
     * Con el interruptor puesto pero SIN PIN, las categorias quedaban bloqueadas
     * sin ninguna forma de entrar: no es proteccion, es una puerta tapiada. La
     * interfaz usa esto para avisar de que falta definir el PIN.
     */
    fun isUsable(settings: UserSettings): Boolean =
        !settings.isParentalLockEnabled || !settings.parentalPin.isNullOrBlank()

    // ------------------------------------------------------------------
    // PIN
    // ------------------------------------------------------------------

    /**
     * Convierte un PIN en algo que se pueda guardar.
     *
     * Se guarda derivado y con sal, no en claro. Contra un adversario serio un
     * PIN de cuatro cifras no aguanta nada -- se prueban las 10.000 -- pero aqui
     * el adversario es un niño con curiosidad, y ese no va a abrir
     * `settings.json` y leerlo de un vistazo.
     */
    fun hashPin(pin: String): String {
        val sal = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val derivado = derive(pin, sal)
        return "$PIN_PREFIX${Base64.getEncoder().encodeToString(sal)}:${Base64.getEncoder().encodeToString(derivado)}"
    }

    /**
     * Comprueba el PIN introducido.
     *
     * Acepta tambien un PIN guardado EN CLARO, que es como lo dejaban las
     * versiones anteriores: si no, al actualizar la aplicacion la gente se
     * quedaria fuera de sus propias categorias.
     */
    fun checkPin(entered: String, stored: String?): Boolean {
        if (stored.isNullOrBlank() || entered.isBlank()) return false
        if (!stored.startsWith(PIN_PREFIX)) {
            // Formato antiguo, en claro. Comparacion en tiempo constante igual.
            return MessageDigest.isEqual(entered.toByteArray(), stored.toByteArray())
        }
        val partes = stored.removePrefix(PIN_PREFIX).split(':')
        if (partes.size != 2) return false
        val sal = runCatching { Base64.getDecoder().decode(partes[0]) }.getOrNull() ?: return false
        val esperado = runCatching { Base64.getDecoder().decode(partes[1]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(derive(entered, sal), esperado)
    }

    /** true si el PIN guardado sigue en el formato antiguo (texto plano). */
    fun isLegacyPin(stored: String?): Boolean =
        !stored.isNullOrBlank() && !stored.startsWith(PIN_PREFIX)

    private fun derive(pin: String, sal: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), sal, ITERACIONES, 256))
            .encoded

    /** Longitud aceptada del PIN. Corto porque se teclea con un mando. */
    const val MIN_PIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 6

    private const val PIN_PREFIX = "pbkdf2:"
    private const val ITERACIONES = 60_000
}
