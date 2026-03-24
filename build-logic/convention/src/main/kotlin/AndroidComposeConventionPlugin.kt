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

import app.pachli.libs
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.plugin.compose")
                apply("com.android.compose.screenshot")
            }

            when {
                pluginManager.hasPlugin("com.android.application") -> {
                    configure<ApplicationExtension> {
                        buildFeatures {
                            compose = true
                        }
                        composeOptions {
                            kotlinCompilerExtensionVersion = "1.5.15"
                        }
                        experimentalProperties["android.experimental.enableScreenshotTest"] = true
                    }
                }

                pluginManager.hasPlugin("com.android.library") -> {
                    configure<LibraryExtension> {
                        experimentalProperties["android.experimental.enableScreenshotTest"] = true
                    }
                }
            }

            dependencies {
                val composeBom = platform(libs.findLibrary("androidx.compose.bom").get())
                add("implementation", composeBom)
                add("androidTestImplementation", composeBom)
                add("testImplementation", composeBom)

                // UI previews (@Preview, etc), https://developer.android.com/develop/ui/compose/tooling/previews
                add("debugImplementation", libs.findLibrary("androidx.compose.ui.tooling").get())
                add("implementation", libs.findLibrary("androidx.compose.ui.tooling.preview").get())

                // Dependencies for testing: https://developer.android.com/develop/ui/compose/testing
                add("androidTestImplementation", libs.findLibrary("androidx.compose.ui.test.junit4").get())
                add("debugImplementation", libs.findLibrary("androidx.compose.ui.test.manifest").get())
                add("debugImplementation", libs.findLibrary("androidx.runtime.tracing").get())

                add("androidTestImplementation", libs.findLibrary("espresso.core").get())
                add("androidTestImplementation", libs.findLibrary("androidx.test.junit").get())
                add("androidTestImplementation", libs.findLibrary("truth").get())

                add("screenshotTestImplementation", libs.findLibrary("screenshot.validation.api").get())
                add("screenshotTestImplementation", libs.findLibrary("androidx.compose.ui.tooling").get())
            }
        }
    }
}
