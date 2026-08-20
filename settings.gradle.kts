pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "IPTV_Family"
include(":shared")
// include(":app") // se rehabilita al retomar Android (requiere Compose compiler plugin)
include(":composeApp") // escritorio (desktop) — consume :shared

// Hilt plugin applied to all modules
plugins {
    id("com.google.dagger.hilt.android") version "2.52" apply false
}