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

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Configure base Kotlin with Android options
 */
internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension<*, *, *, *, *>,
) {
    commonExtension.apply {
        compileSdk = 34

        defaultConfig {
            minSdk = 23
        }

        testOptions {
            unitTests {
                // Without this Robolectric skips some tests with "doesn't support legacy
                // resources mode after P" message
                isIncludeAndroidResources = true
            }
        }

        buildFeatures {
            buildConfig = true
            resValues = true
            viewBinding = true
        }

//        compileOptions {
//            // Up to Java 11 APIs are available through desugaring
//            // https://developer.android.com/studio/write/java11-minimal-support-table
//            sourceCompatibility = JavaVersion.VERSION_11
//            targetCompatibility = JavaVersion.VERSION_11
//            isCoreLibraryDesugaringEnabled = true
//        }
    }

    configureKotlin()

//    dependencies {
//        add("coreLibraryDesugaring", libs.findLibrary("android.desugarJdkLibs").get())
//    }
}

/**
 * Configure base Kotlin options for JVM (non-Android)
 */
internal fun Project.configureKotlinJvm() {
//    extensions.configure<JavaPluginExtension> {
//        // Up to Java 11 APIs are available through desugaring
//        // https://developer.android.com/studio/write/java11-minimal-support-table
//        sourceCompatibility = JavaVersion.VERSION_11
//        targetCompatibility = JavaVersion.VERSION_11
//    }

    configureKotlin()
}

/**
 * Configure base Kotlin options
 */
private fun Project.configureKotlin() {
    // Use withType to workaround https://youtrack.jetbrains.com/issue/KT-55947
    tasks.withType<KotlinCompile>().configureEach {
//        kotlinOptions {
//            // Set JVM target to 11
//            jvmTarget = JavaVersion.VERSION_11.toString()
//            // Treat all Kotlin warnings as errors (disabled by default)
//            // Override by setting warningsAsErrors=true in your ~/.gradle/gradle.properties
//            val warningsAsErrors: String? by project
//            allWarningsAsErrors = warningsAsErrors.toBoolean()
//            freeCompilerArgs = freeCompilerArgs + listOf(
//                "-opt-in=kotlin.RequiresOptIn",
//                // Enable experimental coroutines APIs, including Flow
//                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
//                "-opt-in=kotlinx.coroutines.FlowPreview",
//            )
//        }
    }
}
