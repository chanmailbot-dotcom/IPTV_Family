package com.iptv.family.desktop.security

import com.iptv.family.shared.data.store.SecretVault
import com.iptv.family.shared.log.AppLog
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.W32APIOptions
import java.util.Base64

/**
 * Cifrado de secretos en el escritorio.
 *
 * En Windows usa DPAPI (`CryptProtectData`), que es el mecanismo del propio
 * sistema: la clave la deriva Windows de la cuenta de usuario y nunca la ve la
 * aplicacion. Consecuencia buena: copiar `playlists.json` a otro equipo o a otra
 * cuenta no sirve de nada. Consecuencia a tener presente: tampoco funciona si el
 * usuario mueve su perfil a mano, y por eso el descifrado avisa en vez de
 * devolver basura.
 *
 * En macOS y Linux todavia no hay implementacion (harian falta Keychain y Secret
 * Service): alli se sigue guardando en claro y queda dicho en el log, que es
 * preferible a aparentar una proteccion que no existe.
 */
object DesktopVault {

    fun create(): SecretVault = when {
        isWindows -> DpapiVault()
        else -> {
            AppLog.w(
                "Vault",
                "sin cifrado de secretos en esta plataforma (solo Windows por ahora): " +
                    "la contraseña del panel se guarda en claro"
            )
            SecretVault.NONE
        }
    }

    private val isWindows: Boolean
        get() = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
}

/** Estructura `DATA_BLOB` de la API de Windows. */
@Structure.FieldOrder("cbData", "pbData")
internal open class DataBlob(
    @JvmField var cbData: Int = 0,
    @JvmField var pbData: Pointer? = null,
) : Structure()

private interface Crypt32 : com.sun.jna.win32.StdCallLibrary {
    fun CryptProtectData(
        pDataIn: DataBlob, szDataDescr: String?, pOptionalEntropy: DataBlob?,
        pvReserved: Pointer?, pPromptStruct: Pointer?, dwFlags: Int, pDataOut: DataBlob,
    ): Boolean

    fun CryptUnprotectData(
        pDataIn: DataBlob, ppszDataDescr: Pointer?, pOptionalEntropy: DataBlob?,
        pvReserved: Pointer?, pPromptStruct: Pointer?, dwFlags: Int, pDataOut: DataBlob,
    ): Boolean
}

private interface Kernel32Local : com.sun.jna.win32.StdCallLibrary {
    fun LocalFree(hMem: Pointer?): Pointer?
}

/** Implementacion sobre DPAPI. Ver [DesktopVault]. */
private class DpapiVault : SecretVault {

    private val crypt32 by lazy {
        Native.load("Crypt32", Crypt32::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
    private val kernel32 by lazy {
        Native.load("Kernel32", Kernel32Local::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }

    override fun protect(plain: String): String? = transformar(plain.toByteArray(Charsets.UTF_8), cifrar = true)
        ?.let { Base64.getEncoder().encodeToString(it) }

    override fun reveal(token: String): String? {
        val bytes = runCatching { Base64.getDecoder().decode(token) }.getOrNull() ?: return null
        return transformar(bytes, cifrar = false)?.toString(Charsets.UTF_8)
    }

    private fun transformar(datos: ByteArray, cifrar: Boolean): ByteArray? {
        if (datos.isEmpty()) return null
        val entrada = DataBlob()
        val salida = DataBlob()
        var memoria: Memory? = null
        return try {
            memoria = Memory(datos.size.toLong()).apply { write(0, datos, 0, datos.size) }
            entrada.cbData = datos.size
            entrada.pbData = memoria

            val ok = if (cifrar) {
                // CRYPTPROTECT_UI_FORBIDDEN (0x1): nunca mostrar dialogos; esto
                // corre en segundo plano y un dialogo bloquearia la aplicacion.
                crypt32.CryptProtectData(entrada, "IPTV Family", null, null, null, 0x1, salida)
            } else {
                crypt32.CryptUnprotectData(entrada, null, null, null, null, 0x1, salida)
            }
            if (!ok) {
                AppLog.w("Vault", "DPAPI rechazo la operacion (${if (cifrar) "cifrar" else "descifrar"})")
                return null
            }
            salida.pbData?.getByteArray(0, salida.cbData)
        } catch (e: Throwable) {
            AppLog.e("Vault", "DPAPI no disponible", e)
            null
        } finally {
            // La memoria de salida la reserva Windows: hay que devolverla o se
            // filtra en cada guardado.
            salida.pbData?.let { runCatching { kernel32.LocalFree(it) } }
            runCatching { memoria?.close() }
        }
    }
}
