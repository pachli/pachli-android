pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        gradlePluginPortal()
    }

    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven("https://jitpack.io")
    }
}

plugins {
    id("com.gradle.develocity") version "3.19.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.9.0")
}

develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
        termsOfUseAgree = "yes"
        val isCiBuild = providers.environmentVariable("CI").isPresent
        uploadInBackground = !isCiBuild
        tag(if (isCiBuild) "CI" else "Local")
        publishing.onlyIf { isCiBuild }
    }
}

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "pachli-android"

include(":app")
include(":core:activity")
include(":core:common")
include(":core:data")
include(":core:database")
include(":core:designsystem")
include(":core:domain")
include(":core:eventhub")
include(":core:model")
include(":core:preferences")
include(":core:navigation")
include(":core:network")
include(":core:network-test")
include(":core:testing")
include(":core:ui")
include(":feature:about")
include(":feature:lists")
include(":feature:login")
include(":feature:suggestions")
include(":tools")
include(":tools:fmtsql")
include(":tools:mklanguages")
include(":tools:mkserverversions")
include(":tools:mvstring")
include(":checks")
