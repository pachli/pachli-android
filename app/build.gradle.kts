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
}

apply(from = "gitTools.gradle")
val getGitSha: groovy.lang.Closure<String> by extra
val getGitRevCount: groovy.lang.Closure<Int> by extra

android {
    namespace = "app.pachli"

    defaultConfig {
        applicationId = "app.pachli"
        versionCode = 13
        versionName = "2.4.0"

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
}

dependencies {
    // CachedTimelineRemoteMediator needs the @Transaction annotation from Room
    compileOnly(libs.bundles.room)
    testCompileOnly(libs.bundles.room)

    // @HiltWorker annotation
    implementation(libs.androidx.hilt.common)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(projects.core.accounts)
    implementation(projects.core.activity)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.navigation)
    implementation(projects.core.network)
    implementation(projects.core.preferences)
    implementation(projects.core.ui)

    implementation(projects.feature.about)
    implementation(projects.feature.lists)
    implementation(projects.feature.login)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.bundles.androidx)

    implementation(libs.android.material)

    implementation(libs.moshi)
    implementation(libs.moshi.adapters)
    ksp(libs.moshi.codegen)

    implementation(libs.bundles.retrofit)

    implementation(libs.bundles.okhttp)

    implementation(libs.conscrypt.android)

    implementation(libs.bundles.glide)
    ksp(libs.glide.compiler)

    implementation(libs.sparkbutton)

    implementation(libs.touchimageview)

    implementation(libs.bundles.material.drawer)
    implementation(libs.material.typeface)

    implementation(libs.image.cropper)

    implementation(libs.bundles.filemojicompat)

    implementation(libs.bouncycastle)
    implementation(libs.unified.push)

    implementation(libs.bundles.xmldiff)

    implementation(libs.timber)

    googleImplementation(libs.app.update)
    googleImplementation(libs.app.update.ktx)

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
}
