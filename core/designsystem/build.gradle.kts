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
}

android {
    namespace = "app.pachli.core.designsystem"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(libs.android.material)

    // styles.xml contains overrides for this library
    // TODO: Move these in to MainActivity feature when that's created
    implementation(libs.material.drawer.core)

    // styles.xml contains overrides for this library
    // TODO: Move these styles elsewhere when the feature is created?
    implementation(libs.androidx.core.splashscreen)

    // Styling preferences
    implementation(libs.androidx.preference.ktx)

    // Styling the swipe-refresh layout spinner
    implementation(libs.androidx.swiperefreshlayout)
}
