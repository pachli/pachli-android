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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

group = "app.pachli.buildlogic"

// Configure the build-logic plugins to target JDK 17
// This matches the JDK used to build the project, and is not related to what is running on device.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
//    compileOnly(libs.firebase.crashlytics.gradlePlugin)
//    compileOnly(libs.firebase.performance.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
//        register("androidApplicationCompose") {
//            id = "pachli.android.application.compose"
//            implementationClass = "AndroidApplicationComposeConventionPlugin"
//        }
//        register("androidApplication") {
//            id = "pachli.android.application"
//            implementationClass = "AndroidApplicationConventionPlugin"
//        }
//        register("androidApplicationJacoco") {
//            id = "pachli.android.application.jacoco"
//            implementationClass = "AndroidApplicationJacocoConventionPlugin"
//        }
//        register("androidLibraryCompose") {
//            id = "pachli.android.library.compose"
//            implementationClass = "AndroidLibraryComposeConventionPlugin"
//        }
        register("androidLibrary") {
            id = "pachli.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
//        register("androidFeature") {
//            id = "pachli.android.feature"
//            implementationClass = "AndroidFeatureConventionPlugin"
//        }
//        register("androidLibraryJacoco") {
//            id = "pachli.android.library.jacoco"
//            implementationClass = "AndroidLibraryJacocoConventionPlugin"
//        }
//        register("androidTest") {
//            id = "pachli.android.test"
//            implementationClass = "AndroidTestConventionPlugin"
//        }
        register("androidHilt") {
            id = "pachli.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "pachli.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
//        register("androidFirebase") {
//            id = "pachli.android.application.firebase"
//            implementationClass = "AndroidApplicationFirebaseConventionPlugin"
//        }
        register("androidFlavors") {
            id = "pachli.android.application.flavors"
            implementationClass = "AndroidApplicationFlavorsConventionPlugin"
        }
//        register("androidLint") {
//            id = "pachli.android.lint"
//            implementationClass = "AndroidLintConventionPlugin"
//        }
//        register("jvmLibrary") {
//            id = "pachli.jvm.library"
//            implementationClass = "JvmLibraryConventionPlugin"
//        }
    }
}
