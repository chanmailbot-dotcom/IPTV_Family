package com.iptv.family.desktop.remote

import com.iptv.family.shared.log.AppLog
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.certificates.saveToFile
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.KeyStore

/**
 * Certificado para servir la web por HTTPS.
 *
 * Por que hace falta: sin TLS, la contraseña del usuario viaja legible por la
 * red. En casa, con el router de por medio, el riesgo es bajo; en cuanto alguien
 * expone el puerto a internet -- y alguien lo hara -- es una contraseña regalada
 * a cualquiera que mire el trafico por el camino.
 *
 * El certificado es AUTOFIRMADO, asi que el navegador avisara la primera vez.
 * No es un defecto: para un servidor domestico sin dominio propio no hay otra
 * via automatica, y un aviso que se acepta una vez es mejor que texto plano.
 * Quien tenga un certificado de verdad puede indicar su propio almacen.
 */
object WebServerTls {

    private const val ALIAS = "iptv-family"

    /**
     * Devuelve el almacen de claves a usar, generandolo la primera vez.
     *
     * @param dir carpeta de datos de la aplicacion
     * @param password contraseña del almacen
     */
    fun keyStore(dir: File, password: String): Pair<KeyStore, File>? = runCatching {
        val fichero = File(dir, "web-cert.jks")
        if (fichero.isFile) {
            val ks = KeyStore.getInstance("JKS").apply {
                fichero.inputStream().use { load(it, password.toCharArray()) }
            }
            return@runCatching ks to fichero
        }

        AppLog.d("RemoteServer", "generando certificado autofirmado para HTTPS")
        val ks = buildKeyStore {
            certificate(ALIAS) {
                this.password = password
                // 10 años: es un certificado de casa; renovarlo cada año solo
                // seria una molestia sin ganancia real.
                daysValid = 3650
                // Los nombres por los que se accedera. Sin la IP de la red local
                // el navegador rechaza el certificado al entrar por IP, que es
                // justo como se entra desde el movil.
                domains = buildList {
                    add("localhost")
                    addAll(direccionesLocales())
                }
            }
        }
        ks.saveToFile(fichero, password)
        AppLog.d("RemoteServer", "certificado guardado en ${fichero.name}")
        ks to fichero
    }.onFailure {
        AppLog.e("RemoteServer", "no se pudo preparar el certificado HTTPS", it)
    }.getOrNull()

    /**
     * IPs v4 de la maquina en la red local, para meterlas en el certificado.
     * Se descartan loopback, interfaces caidas y las virtuales (VPN, Docker,
     * maquinas virtuales), que no son por donde entra nadie.
     */
    private fun direccionesLocales(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress }
            .distinct()
    }.getOrDefault(emptyList())
}
