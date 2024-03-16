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
    includeBuild("plugins/markdown2resource")
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
    id("com.gradle.enterprise") version "3.16.2"
}

val isCiBuild = !System.getenv("CI").isNullOrBlank()

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        isUploadInBackground = !isCiBuild
        tag(if (isCiBuild) "CI" else "Local")
        publishAlwaysIf(isCiBuild)
    }
}

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "pachli-android"

include(":app")
include(":core:accounts")
include(":core:activity")
include(":core:common")
include(":core:data")
include(":core:database")
include(":core:designsystem")
include(":core:preferences")
include(":core:navigation")
include(":core:network")
include(":core:testing")
include(":core:ui")
include(":feature:about")
include(":feature:lists")
include(":feature:login")
include(":tools:mklanguages")
include(":tools:mkserverversions")
include(":tools:mvstring")
include(":checks")
