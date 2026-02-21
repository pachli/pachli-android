/*
 * Copyright 2023 Pachli Association
 *
 * This file is a part of Pachli.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Pachli is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Pachli; if not,
 * see <http://www.gnu.org/licenses>.
 */

import com.android.build.gradle.internal.api.ApkVariantOutputImpl

plugins {
    alias(libs.plugins.pachli.android.application)
    alias(libs.plugins.pachli.android.hilt)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.moshixir)
}

apply(from = "gitTools.gradle")
val getGitSha: groovy.lang.Closure<String> by extra
val getGitRevCount: groovy.lang.Closure<Int> by extra

moshi {
    enableSealed.set(true)
}

android {
    namespace = "app.pachli"

    defaultConfig {
        applicationId = "app.pachli"
        versionCode = 45
        versionName = "3.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["disableAnalytics"] = "true"

        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            isDefault = true
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        resources.excludes.apply {
            add("LICENSE_OFL")
            add("LICENSE_UNICODE")
        }
    }

    bundle {
        language {
            // bundle all languages in every apk so the dynamic language switching works
            enableSplit = false
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        checkDependencies = true
    }

    testOptions {
        unitTests.all {
            it.systemProperty("robolectric.logging.enabled", "true")
            it.systemProperty("robolectric.lazyload", "ON")
        }
    }

    applicationVariants.configureEach {
        tasks.register("printVersionInfo${name.replaceFirstChar { it.uppercaseChar() }}") {
            notCompatibleWithConfigurationCache("Should always print the version info")
            doLast {
                println("$versionCode $versionName")
            }
        }
        outputs.configureEach {
            this as ApkVariantOutputImpl
            // Set the "orange" release versionCode to the number of commits on the
            // branch, to ensure the versionCode updates on every release. Include the
            // SHA of the current commit to help with troubleshooting bug reports
            if (flavorName.startsWith("orange")) {
                versionNameOverride = "$versionName+${getGitSha()}"
            }
            if (buildType.name == "release" && flavorName.startsWith("orange")) {
                versionCodeOverride = getGitRevCount()
            }
            outputFileName = "Pachli_${versionName}_${versionCode}_${getGitSha()}_${flavorName}_${buildType.name}.apk"
        }
    }
}

configurations {
    // JNI-only libraries don't play nicely with Robolectric
    // see https://github.com/tuskyapp/Tusky/pull/3367 and
    // https://github.com/google/conscrypt/issues/649
    testImplementation {
        exclude(group = "org.conscrypt", module = "conscrypt-android")
    }

    implementation {
        exclude(group = "org.jetbrains", module = "annotations")
    }
}

dependencies {
    // CachedTimelineRemoteMediator needs the @Transaction annotation from Room
    compileOnly(libs.bundles.room)
    testCompileOnly(libs.bundles.room)

    // @HiltWorker annotation
    implementation(libs.androidx.hilt.common)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(projects.core.activity)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.eventhub)
    implementation(projects.core.model)
    implementation(projects.core.navigation)
    implementation(projects.core.network)
    implementation(projects.core.preferences)
    implementation(projects.core.ui)
    implementation(projects.core.worker)

    implementation(projects.feature.about)
    implementation(projects.feature.intentrouter)
    implementation(projects.feature.lists)
    implementation(projects.feature.login)
    implementation(projects.feature.manageaccounts)
    implementation(projects.feature.suggestions)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.bundles.androidx)
    implementation(libs.androidx.core.animation)

    implementation(libs.android.material)

    implementation(libs.bundles.retrofit)

    implementation(libs.bundles.okhttp)
    implementation(libs.okio)

    implementation(libs.conscrypt.android)

    ksp(libs.glide.compiler)

    implementation(libs.touchimageview)

    implementation(libs.bundles.material.drawer)
    implementation(libs.material.typeface)

    implementation(libs.image.cropper)

    implementation(libs.bundles.filemojicompat)

    implementation(libs.unified.push)

    implementation(libs.bundles.xmldiff)

    implementation(libs.timber)

    googleImplementation(libs.app.update)
    googleImplementation(libs.app.update.ktx)

    // Language detection
    googleImplementation(libs.play.services.base)
    googleImplementation(libs.mlkit.language.id)
    googleImplementation(libs.kotlinx.coroutines.play.services)

    // Translation
    googleImplementation(libs.mlkit.translation)

    implementation(libs.semver)

    debugImplementation(libs.leakcanary)

    testImplementation(projects.core.testing)
    testImplementation(projects.core.networkTest)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.bundles.mockito)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.test.core.ktx)

    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core.ktx)

    lintChecks(projects.checks)

    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Choose one of the following:
    // Material Design 3
    implementation("androidx.compose.material3:material3")
    // or Material Design 2
//    implementation("androidx.compose.material:material")
    // or skip Material Design and build directly on top of foundational components
//    implementation("androidx.compose.foundation:foundation")
    // or only import the main APIs for the underlying toolkit systems,
    // such as input and measurement/layout
//    implementation("androidx.compose.ui:ui")

    // Android Studio Preview support
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // UI Tests
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Optional - Included automatically by material, only add when you need
    // the icons but not the material library (e.g. when using Material3 or a
    // custom design system based on Foundation)
//    implementation("androidx.compose.material:material-icons-core")
    // Optional - Add full set of material icons
//    implementation("androidx.compose.material:material-icons-extended")
    // Optional - Add window size utils
    implementation("androidx.compose.material3.adaptive:adaptive")

    // Optional - Integration with activities
    implementation("androidx.activity:activity-compose:1.10.1")
    // Optional - Integration with ViewModels
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    // Optional - Integration with LiveData
//    implementation("androidx.compose.runtime:runtime-livedata")
    // Optional - Integration with RxJava
//    implementation("androidx.compose.runtime:runtime-rxjava2")

    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    // To use constraintlayout in compose
    implementation("androidx.constraintlayout:constraintlayout-compose:1.1.1")
    debugImplementation("androidx.compose.runtime:runtime-tracing")
}
