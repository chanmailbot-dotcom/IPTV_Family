plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("com.android.library")
}

group = "com.iptv.family"
version = "1.0.0"

kotlin {
    // Biblioteca compartida: lógica de dominio y acceso a datos
    // utilizada por la app Android (app/) y el cliente de escritorio
    // (composeApp) para Windows / Linux / macOS.
    jvm("desktop")
    androidTarget()

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

        val androidMain by getting {
            // java.io.File / java.net.HttpURLConnection de commonMain funcionan igual en
            // Android (son parte de libcore): no hace falta codigo especifico aqui.
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

/**
 * Heap de las pruebas, explicito y ajustable: `-PtestHeap=128m`.
 *
 * La guia EPG es la pieza que puede tumbar la aplicacion por memoria, y en un
 * Fire TV el reparto es de unos cientos de MB, no los varios GB que tiene por
 * defecto una JVM de escritorio. Un tope fijo aqui hace que una regresion de
 * memoria salga en las pruebas y no en el salon de alguien.
 */
tasks.withType<Test>().configureEach {
    maxHeapSize = (project.findProperty("testHeap") as String?) ?: "256m"
}

android {
    namespace = "com.iptv.family.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
