plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose.desktop")
    id("org.jetbrains.kotlin.plugin.serialization")
}

group = "com.iptv.family"
version = "1.0.0"

kotlin {
    jvm("desktop") {
        // Configuración para escritorio (Windows/Linux/macOS)
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                
                // Room no está disponible en desktop, usar implementación alternativa
                // implementation("androidx.room:room-runtime:2.6.1")
                // implementation("androidx.room:room-ktx:2.6.1")
                
                // ExoPlayer no en desktop, usar MediaPlayer nativo o VLC
                // implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")
                
                // Coil para imágenes
                implementation("io.coil-kt:coil-compose:2.5.0")
            }
        }
        
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-io:0.1.18")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.iptv.family.desktop.MainKt"
        nativeDistributions {
            // Configuración para jpackage (MSI en Windows)
            if (System.getProperty("os.name").lowercase().contains("windows")) {
                packageType = "msi"
                packageName = "IPTV Family"
                packageVersion = "1.0.0"
                maintainer = "IPTV Family Team"
                vendor = "IPTV Family"
                description = "Reproductor IPTV estilo IBO Player para Windows"
                copyright = "2024 IPTV Family"
                license = "MIT"
                icon = file("src/desktop/resources/icon.ico")
                menu = true
                shortcut = true
                winDirChooser = true
                winPerUserInstall = false
                winUpgradeGuid = "{12345678-1234-1234-1234-123456789012}"
            } else {
                packageType = "dmg" // macOS
                packageName = "IPTV Family"
                packageVersion = "1.0.0"
            }
        }
    }
}

repositories {
    google()
    mavenCentral()
}