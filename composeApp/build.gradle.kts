plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

group = "com.iptv.family"
version = providers.gradleProperty("iptvFamilyVersion").get()

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.materialIconsExtended)

                // Modulo KMP compartido con la logica de dominio y acceso a datos
                implementation(project(":shared"))

                // Reproduccion de video embebida (HLS / MPEG-TS / RTMP) via libvlc
                implementation("uk.co.caprica:vlcj:4.8.2")

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

                // Servidor web de control remoto (ver "Servidor web" en Ajustes):
                // motor CIO (puro JVM, sin dependencias nativas, arranque/parada baratos).
                val ktorVersion = "2.3.12"
                implementation("io.ktor:ktor-server-core:$ktorVersion")
                // Netty y no CIO: CIO no soporta HTTPS ("CIO Engine does not
                // currently support HTTPS"), y sin TLS la contraseña del usuario
                // viaja legible en cuanto alguien expone el puerto a internet.
                // Sigue siendo Java puro, sin dependencias nativas.
                implementation("io.ktor:ktor-server-netty:$ktorVersion")
                implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
                // Comprime el JSON de estado (una lista de 40.000 canales son ~7 MB).
                implementation("io.ktor:ktor-server-compression:$ktorVersion")
                // Genera el certificado autofirmado cuando se activa HTTPS y no
                // hay uno propio. Sin esto, activar TLS obligaria a pelearse con
                // keytool en la linea de comandos.
                implementation("io.ktor:ktor-network-tls-certificates:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                // Cliente HTTP para el proxy del stream hacia el proveedor.
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // Pruebas de interfaz: hasta ahora la UI solo se comprobaba a
                // mano (capturas de pantalla), asi que nada impedia que una
                // regresion volviera a colar. Esto permite montar una pantalla
                // en un tamaño concreto y preguntarle que hay dentro.
                implementation(compose.desktop.uiTestJUnit4)
            }
        }
    }
}

/**
 * Mete los textos de licencia DENTRO del paquete.
 *
 * Tanto la LGPL como la GPL exigen que el texto acompañe a lo que se distribuye,
 * y el instalador repartia libvlc y sus 365 plugins sin una sola linea de
 * licencia. Se copian al empaquetar en vez de tenerlos duplicados en el
 * repositorio: la copia de `resources/common/licencias` esta en .gitignore.
 *
 * El `COPYING` propio de VideoLAN lo trae `scripts/fetch-vlc.ps1` junto a las
 * DLL, que es donde corresponde.
 */
val sincronizarLicencias by tasks.registering(Copy::class) {
    from(rootProject.file("licencias"))
    from(rootProject.file("LICENSE"))
    from(rootProject.file("LICENSES-TERCEROS.md"))
    into(layout.projectDirectory.dir("resources/common/licencias"))
}

// `prepareAppResources` es la que de verdad lee ese directorio; sin declararlo,
// Gradle avisa (con razon) de que el orden de las dos tareas no esta garantizado.
tasks.matching {
    it.name.startsWith("package") ||
        it.name == "createDistributable" ||
        it.name.startsWith("prepareAppResources")
}.configureEach { dependsOn(sincronizarLicencias) }

compose.desktop {
    application {
        mainClass = "com.iptv.family.desktop.MainKt"

        // Para depurar en desarrollo sin recompilar:
        //   ./gradlew :composeApp:run -PappJvmArgs="-Dcompose.interop.blending=false"
        (project.findProperty("appJvmArgs") as? String)
            ?.split(' ')
            ?.filter { it.isNotBlank() }
            ?.let { jvmArgs += it }

        // El runtime de VLC se copia aqui antes de empaquetar (ver scripts/fetch-vlc.*)
        // y queda accesible en tiempo de ejecucion vía compose.application.resources.dir.
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "IPTV Family"
            // Sale de `iptvFamilyVersion` en gradle.properties, que es la unica
            // fuente de la version del proyecto.
            packageVersion = project.version.toString()
            description = "Reproductor IPTV para Windows, Linux y macOS"
            vendor = "IPTV Family"
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            // libvlc se carga por JNA, no por el modulo del JDK: hay que incluir jdk.unsupported
            modules("java.naming", "java.sql", "jdk.unsupported")

            windows {
                menu = true
                menuGroup = "IPTV Family"
                shortcut = true
                perUserInstall = true
                dirChooser = true
                iconFile.set(project.file("icons/icon.ico"))
                // UUID fijo: permite que las siguientes versiones actualicen en vez de duplicar
                upgradeUuid = "8B1F4C22-3F5D-4E7A-9C1B-2D6E5A0F7B34"
            }
            linux {
                packageName = "iptv-family"
                iconFile.set(project.file("icons/icon.png"))
            }
        }
    }
}
