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
    id("io.gitlab.arturbosch.detekt") version "1.23.6" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}