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

package app.pachli

import com.android.build.api.dsl.CommonExtension
import com.gradle.develocity.agent.gradle.test.DevelocityTestConfiguration
import com.gradle.develocity.agent.gradle.test.TestRetryConfiguration
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Configure base Kotlin with Android options
 */
internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension,
) {
    commonExtension.apply {
        compileSdk = 37

        defaultConfig.apply {
            minSdk = 23
        }

        compileOptions.apply {
            isCoreLibraryDesugaringEnabled = true
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }

        testOptions.apply {
            unitTests {
                // Without this Robolectric skips some tests with "doesn't support legacy
                // resources mode after P" message
                isIncludeAndroidResources = true

                // Without this Robolectric fails some tests with
                // "Method myLooper in android.os.Looper not mocked"
                isReturnDefaultValues = true
            }

            // https://developer.android.com/studio/test/managed-devices
            managedDevices {
                localDevices {
                    create("pixel9api31") {
                        device = "Pixel 9 Pro XL"
                        apiLevel = 31
                        systemImageSource = "aosp-atd"
                        testedAbi = "x86_64"
                    }
                }
            }
        }

        buildFeatures.apply {
            buildConfig = true
            resValues = true
            viewBinding = true
            dataBinding.apply {
                enable = true
            }
        }
    }

    configureKotlin<KotlinAndroidProjectExtension>()

    dependencies {
        add("coreLibraryDesugaring", libs.findLibrary("desugar.jdk.libs").get())
    }
}

/**
 * Configure base Kotlin options
 */
private inline fun <reified T : KotlinBaseExtension> Project.configureKotlin() = configure<T> {
    // Use withType to workaround https://youtrack.jetbrains.com/issue/KT-55947
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            // Treat all Kotlin warnings as errors (disabled by default)
            // Override by setting warningsAsErrors=true in your ~/.gradle/gradle.properties
            val warningsAsErrors: String = providers.gradleProperty("warningsAsErrors").getOrElse("false")
            allWarningsAsErrors.set(warningsAsErrors.toBoolean())
            optIn.addAll(
                "kotlin.RequiresOptIn",
                // Enable experimental coroutines APIs, including Flow
                "kotlinx.coroutines.ExperimentalCoroutinesApi",
                "kotlinx.coroutines.FlowPreview",
            )
            freeCompilerArgs.addAll(
                "-Xjspecify-annotations=strict",
            )
        }
    }

    if (providers.environmentVariable("CI").isPresent) {
        configureTestTasksOnCi()
    }

    // AGP 9 defaults to failing if a `src/test` directory exists with no tests.
    // Disable this. For details see:
    // - https://github.com/gradle/gradle/issues/33619
    // - https://github.com/react-native-device-info/react-native-device-info/issues/1743
    // - https://issuetracker.google.com/issues/411739086
    tasks.withType<Test>().configureEach {
        failOnNoDiscoveredTests.set(false)
    }
}

/**
 * CI-specific test configuration.
 *
 * GitHub action runners can timeout resulting in occasional test flakiness.
 * Re-run tests on CI to work around this, while disallowing failing tests
 * when developing locally.
 */
private fun Project.configureTestTasksOnCi() {
    tasks.withType<Test>().configureEach {
        testRetry {
            maxRetries.set(4)
            maxFailures.set(5)
            failOnPassedAfterRetry.set(false)
        }
    }
}

private fun Test.testRetry(configure: TestRetryConfiguration.() -> Unit) {
    extensions.getByName<DevelocityTestConfiguration>("develocity").testRetry(configure)
}
