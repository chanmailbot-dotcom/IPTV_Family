plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

group = "com.iptv.family"
version = "1.0.0"

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
                implementation("io.ktor:ktor-server-cio:$ktorVersion")
                implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
                // Comprime el JSON de estado (una lista de 40.000 canales son ~7 MB).
                implementation("io.ktor:ktor-server-compression:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                // Cliente HTTP para el proxy del stream hacia el proveedor.
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.iptv.family.desktop.MainKt"

        // El runtime de VLC se copia aqui antes de empaquetar (ver scripts/fetch-vlc.*)
        // y queda accesible en tiempo de ejecucion vía compose.application.resources.dir.
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "IPTV Family"
            packageVersion = "1.0.0"
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
