/*
 * Copyright 2024 Pachli Association
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
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "app.pachli.core.data"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(projects.core.accounts)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.preferences)

    // PreferenceDataStore
    implementation(libs.bundles.androidx)

    // ServerRepository
    implementation(libs.semver)

    testImplementation(projects.core.networkTest)
    testImplementation(libs.bundles.mockito)
}
