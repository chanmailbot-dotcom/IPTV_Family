plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.0.21"
    id("org.jetbrains.compose.desktop") version "1.7.1"
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
                implementation(compose.materialIconsCore)

                // Modulo KMP compartido con la logica de dominio y acceso a datos
                implementation(project(":shared"))

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
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
        nativeDistributions {
            packageName = "IPTV Family"
            packageVersion = "1.0.0"
            description = "Reproductor IPTV estilo IBO Player para Windows/Linux/macOS"
        }
    }
}
