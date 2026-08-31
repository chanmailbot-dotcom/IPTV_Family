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
    // Solo los fuentes JVM: son los que pueden analizarse CON resolucion de
    // tipos. La UI de Android va en una tarea aparte (`detektAndroid`), porque
    // mezclar en una sola tarea el classpath de Android con el de la JVM deja a
    // Gradle sin poder elegir variante de :shared y el analisis no arranca.
    source.setFrom(
        files(
            "shared/src/commonMain",
            "shared/src/desktopMain",
            "shared/src/desktopTest",
            "composeApp/src/desktopMain",
            "composeApp/src/desktopTest",
        ),
    )
}

/**
 * Classpath de compilacion de los modulos JVM: es lo que le da a detekt
 * resolucion de TIPOS. Se comparte entre el analisis y la generacion de la linea
 * base -- si la base se genera sin tipos y el analisis va con tipos, la base no
 * cubre lo que el analisis encuentra y el build queda rojo sin motivo.
 */
val classpathAnalisis = provider {
    files(
        project(":shared").configurations.findByName("desktopCompileClasspath"),
        project(":composeApp").configurations.findByName("desktopCompileClasspath"),
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    // La de Android va sin tipos a proposito (ver detektAndroid), y ahi su base
    // tampoco los necesita.
    if (name != "detektAndroidBaseline") {
        jvmTarget = "17"
        classpath.setFrom(classpathAnalisis)
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    // RESOLUCION DE TIPOS. Sin classpath, detekt solo ve la sintaxis: comprobado
    // que un `a!!` sobre un nulable pasaba desapercibido. Con el classpath de
    // compilacion se activan las reglas que de verdad encuentran fallos.
    //
    // A la mitad Android NO se le da: su classpath es el de Android y aqui solo
    // hay el de la JVM. Con un classpath incompleto detekt no se limita a saber
    // menos, sino que concluye MAL: daba por «codigo inalcanzable» cuatro lineas
    // de `applyOverride` que se ejecutan perfectamente (probadas en el emulador
    // cambiando la pista de audio). Un classpath equivocado es peor que ninguno.
    if (name != "detektAndroid") {
        jvmTarget = "17"
        classpath.setFrom(classpathAnalisis)
    }

    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}

/**
 * Analisis de la interfaz de Android, SIN resolucion de tipos.
 *
 * No es por dejadez: el classpath de Android y el de la JVM no se pueden juntar
 * en una misma tarea (Gradle no sabe entonces que variante de :shared servir), y
 * la logica de verdad -- la que merece el analisis con tipos -- vive en `shared`,
 * que si va cubierta. Aqui quedan pantallas de Compose.
 */
val detektAndroid by tasks.registering(io.gitlab.arturbosch.detekt.Detekt::class) {
    description = "Analisis estatico de la interfaz de Android (sin resolucion de tipos)"
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    baseline = file("config/detekt/baseline-android.xml")
    setSource(files("app/src/main"))
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}

/** Linea base de la mitad Android (el plugin solo la genera para su propia tarea). */
val detektAndroidBaseline by tasks.registering(io.gitlab.arturbosch.detekt.DetektCreateBaselineTask::class) {
    description = "Crea la linea base de detekt para la interfaz de Android"
    buildUponDefaultConfig.set(true)
    config.setFrom(files("config/detekt/detekt.yml"))
    baseline.set(file("config/detekt/baseline-android.xml"))
    setSource(files("app/src/main"))
}

/** Lo que ejecuta el CI: las dos mitades. */
val analisisEstatico by tasks.registering {
    description = "Analisis estatico de todo el proyecto"
    dependsOn(tasks.named("detekt"), detektAndroid)
}

// A proposito NO se añade `detekt-formatting` (ktlint): se probo y de los 981
// hallazgos, mas de 800 eran de formato -- sangrados, orden de imports, saltos de
// linea en listas de argumentos. Aceptarlo obligaria a reformatear el proyecto
// entero en un commit que taparia el historial, y no arregla ni un fallo. Lo que
// se queda es el analisis: complejidad, nombres y fallos potenciales.

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}