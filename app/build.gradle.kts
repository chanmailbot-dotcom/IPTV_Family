import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.iptv.family"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.iptv.family"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    /**
     * Firma de release.
     *
     * Hasta ahora el APK que se instalaba en el Fire TV era el de DEPURACION,
     * firmado con la clave de desarrollo que genera el SDK. Eso vale para
     * probar, pero no para repartirlo: esa clave la tiene cualquiera, va sin
     * optimizar y Android trata las actualizaciones firmadas con otra clave como
     * una aplicacion distinta -- es decir, la primera actualizacion "de verdad"
     * obligaria a desinstalar y perder los datos.
     *
     * Las credenciales NO viven en el repositorio: se leen del entorno (asi las
     * pone el CI desde los secretos del proyecto) o de un `keystore.properties`
     * local que esta en .gitignore. Si no hay ninguna, no se define la firma y
     * `assembleRelease` no se intenta: es preferible a generar un APK que
     * parezca publicable y no lo sea.
     */
    val propiedadesFirma = rootProject.file("keystore.properties")
    val firmaDisponible = propiedadesFirma.exists() || System.getenv("ANDROID_KEYSTORE_BASE64") != null

    signingConfigs {
        if (firmaDisponible) {
            create("release") {
                val props = Properties().apply {
                    if (propiedadesFirma.exists()) propiedadesFirma.inputStream().use { load(it) }
                }
                fun valor(clave: String, entorno: String): String? =
                    props.getProperty(clave) ?: System.getenv(entorno)

                val ruta = valor("storeFile", "ANDROID_KEYSTORE_FILE")
                if (ruta != null) storeFile = file(ruta)
                storePassword = valor("storePassword", "ANDROID_KEYSTORE_PASSWORD")
                keyAlias = valor("keyAlias", "ANDROID_KEY_ALIAS")
                keyPassword = valor("keyPassword", "ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (firmaDisponible) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packagingOptions {
        resources.excludes.add("/META-INF/*")
    }
}

dependencies {
    // Modulo KMP compartido: parseo M3U/Xtream, LibraryRepository, modelos, logging.
    implementation(project(":shared"))

    // Android Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Compose (BOM fija las versiones del resto de artefactos compose.*)
    implementation(platform("androidx.compose:compose-bom:2024.08.00"))
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Media3 ExoPlayer: reproductor de vídeo (equivalente Android a VlcController en desktop)
    val media3Version = "1.5.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")

    // Logos de canal
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
}
