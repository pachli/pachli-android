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
    alias(libs.plugins.aboutlibraries)

    id("app.pachli.plugins.markdown2resource")
}

android {
    namespace = "app.pachli.feature.about"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["disableAnalytics"] = "true"
    }
}

aboutLibraries {
    configPath = "licenses"
    includePlatform = false
    duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
    prettyPrint = true
    // The "generated" field contains a timestamp, which breaks reproducible builds.
    excludeFields = arrayOf("generated")
}

markdown2resource {
    files.add(layout.projectDirectory.file("../../PRIVACY.md"))
}

dependencies {
    implementation(projects.core.activity)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.navigation)
    implementation(projects.core.ui)

    // TODO: These three dependencies are required by BottomSheetActivity,
    // make this part of the projects.core.activity API?
    implementation(projects.core.network)
    implementation(projects.core.preferences)
    implementation(libs.bundles.androidx)

    implementation(libs.bundles.aboutlibraries)

    // For FixedSizeDrawable
    implementation(libs.glide.core)
}
