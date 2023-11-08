pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
    }

    includeBuild("build-logic")
    includeBuild("plugins/markdown2resource")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "pachli-android"

include(":app")
include(":core:accounts")
include(":core:common")
include(":core:database")
include(":core:preferences")
include(":core:network")
include(":core:testing")
include(":tools:mklanguages")
include(":checks")
