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

plugins {
    alias(libs.plugins.pachli.android.library)
    alias(libs.plugins.pachli.android.hilt)
    alias(libs.plugins.pachli.android.room)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "app.pachli.core.database"

    defaultConfig {
        testInstrumentationRunner = "app.pachli.core.database.HiltTestRunner"
    }

    packaging {
        resources.excludes.apply {
            // Otherwise this error:
            // "2 files found with path 'META-INF/versions/9/OSGI-INF/MANIFEST.MF' from inputs:"
            add("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.preferences)

    // Because of the use of @Json in DraftEntity
    implementation(libs.moshi)
    implementation(libs.moshi.adapters)
    ksp(libs.moshi.codegen)

    implementation(libs.moshix.sealed.runtime)
    ksp(libs.moshix.sealed.codegen)

    // ServerRepository
    implementation(libs.semver)?.because("Converters has to convert Version")

    testImplementation(projects.core.testing)
    testImplementation(projects.core.network)
        ?.because("Creates core.network.model.Status in tests")

    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android)
}
