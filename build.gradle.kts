// build.gradle.kts (Project level)
plugins {
    id("com.android.application") version "8.7.2" apply false
    id("com.android.library") version "8.7.2" apply false
    // Alineado con el resto del proyecto (shared/composeApp usan Kotlin 2.0.21):
    // la version 1.9.24 que tenia esto antes generaba metadata Kotlin incompatible
    // al depender :app de :shared.
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
    id("org.jetbrains.kotlin.multiplatform") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// Detekt estaba DECLARADO aqui con `apply false` y no se aplicaba en ningun
// modulo: es decir, el proyecto decia tener analisis estatico y no analizaba
// nada, igual que el job de CI llamado "Tests y analisis estatico" que solo
// ejecutaba tests.
//
// Se aplica en la raiz y se le dan los fuentes de los tres modulos a mano. En un
// proyecto multiplataforma es lo mas simple que funciona: aplicarlo modulo a
// modulo obliga a lidiar con los source sets de cada target (desktopMain,
// androidMain, commonMain...) para acabar analizando los mismos ficheros.
apply(plugin = "io.gitlab.arturbosch.detekt")

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    baseline = file("config/detekt/baseline.xml")
    source.setFrom(
        files(
            "app/src/main",
            "shared/src/commonMain",
            "shared/src/desktopMain",
            "shared/src/desktopTest",
            "composeApp/src/desktopMain",
            "composeApp/src/desktopTest",
        ),
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}

// A proposito NO se añade `detekt-formatting` (ktlint): se probo y de los 981
// hallazgos, mas de 800 eran de formato -- sangrados, orden de imports, saltos de
// linea en listas de argumentos. Aceptarlo obligaria a reformatear el proyecto
// entero en un commit que taparia el historial, y no arregla ni un fallo. Lo que
// se queda es el analisis: complejidad, nombres y fallos potenciales.

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}