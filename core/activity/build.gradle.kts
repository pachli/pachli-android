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
}

android {
    namespace = "app.pachli.core.activity"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["disableAnalytics"] = "true"
    }
}

dependencies {
    // BaseActivity exposes AccountManager as an injected property
    api(projects.core.data)

    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.navigation)

    // BottomSheetActivity needs MastodonApi for searching
    implementation(projects.core.network)

    implementation(projects.core.preferences)

    implementation(libs.bundles.androidx)

    // Loading avatars
    implementation(libs.bundles.glide)
    implementation(project(":core:database"))

    // Crash reporting in orange (Pachli Current) builds only
    orangeImplementation(libs.bundles.acra)

    orangeCompileOnly(libs.auto.service.annotations)
    kspOrange(libs.auto.service.ksp)

    // BottomSheetActivityTest uses mockito
    testImplementation(libs.bundles.mockito)
}
