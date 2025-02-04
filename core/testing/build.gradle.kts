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
}

android {
    namespace = "app.pachli.core.testing"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.network)

    implementation(libs.hilt.android.testing)

    implementation(libs.moshi)
    implementation(libs.moshi.adapters)
    implementation(libs.okhttp.core)
        ?.because("Includes testing utilities for ApiResult")
    implementation(libs.retrofit.core)
        ?.because("Includes testing utilities for ApiResult")

    api(libs.kotlinx.coroutines.test)
    api(libs.androidx.test.junit)
    api(libs.androidx.core.testing)
    api(libs.androidx.test.core.ktx)
    api(libs.bundles.room)?.because("Allows calls to RoomDatabase.close() in tests.")
    api(libs.robolectric)
    api(libs.truth)
    api(libs.turbine)

    androidTestImplementation(libs.androidx.test.junit)
}
