

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

import app.pachli.configureFlavors
import app.pachli.disableUnnecessaryAndroidTests
import app.pachli.libs
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
                apply("pachli.android.lint")
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = 36
                configureFlavors(this)
            }

            extensions.configure<LibraryAndroidComponentsExtension> {
                disableUnnecessaryAndroidTests(target)
            }

            dependencies {
                add("implementation", libs.findLibrary("timber").get())
                add("testImplementation", kotlin("test"))
                add("testImplementation", project(":core:testing"))
                add("androidTestImplementation", kotlin("test"))
                add("androidTestImplementation", project(":core:testing"))
                add("lintChecks", project(":checks"))

                val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
                add("implementation", composeBom)

                add("implementation", "androidx.compose.material3:material3")
                add("implementation", "androidx.compose.ui:ui-tooling-preview")
                add("debugImplementation", "androidx.compose.ui:ui-tooling")

                // UI Tests
                add("androidTestImplementation", "androidx.compose.ui:ui-test-junit4")
                add("debugImplementation", "androidx.compose.ui:ui-test-manifest")

                // Optional - Included automatically by material, only add when you need
                // the icons but not the material library (e.g. when using Material3 or a
                // custom design system based on Foundation)
//    implementation("androidx.compose.material:material-icons-core")
                // Optional - Add full set of material icons
//    implementation("androidx.compose.material:material-icons-extended")
                // Optional - Add window size utils
                add("implementation", "androidx.compose.material3.adaptive:adaptive")

                // Optional - Integration with activities
                add("implementation", "androidx.activity:activity-compose:1.10.1")
                // Optional - Integration with ViewModels
                add("implementation", "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
                add("implementation", "androidx.constraintlayout:constraintlayout:2.2.1")
                add("implementation", "androidx.constraintlayout:constraintlayout-compose:1.1.1")
            }
        }
    }
}
