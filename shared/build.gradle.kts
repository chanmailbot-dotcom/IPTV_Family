plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

group = "com.iptv.family"
version = "1.0.0"

kotlin {
    // Biblioteca compartida: lógica de dominio y acceso a datos
    // utilizada por la app Android (app/) y el cliente de escritorio
    // (composeApp) para Windows / Linux / macOS.
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }

        val desktopMain by getting {
            // Parser XMLTV (JDK DOM) y utilidades solo-escritorio
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}