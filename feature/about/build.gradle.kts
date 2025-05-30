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

import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    alias(libs.plugins.pachli.android.library)
    alias(libs.plugins.pachli.android.hilt)
    alias(libs.plugins.aboutlibraries)
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
}

val privacyPolicySpec = copySpec { from("../../PRIVACY.md") }

val copyPrivacyPolicy =
    tasks.register<Copy>("copyPrivacyPolicy") {
        into("src/main/assets")
        with(privacyPolicySpec)
    }

afterEvaluate { tasks.named("preBuild").dependsOn(copyPrivacyPolicy) }

tasks.clean { delete("src/main/assets/PRIVACY.md") }

dependencies {
    implementation(projects.core.activity)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.navigation)
    implementation(projects.core.ui)

    // TODO: These three dependencies are required by BottomSheetActivity,
    // make this part of the projects.core.activity API?
    implementation(projects.core.network)
    implementation(libs.bundles.androidx)

    implementation(libs.bundles.aboutlibraries)

    // For FixedSizeDrawable
    implementation(libs.glide.core)

    // Markdown support for the privacy policy
    implementation(libs.markwon)
    implementation(libs.markwon.tables)
}
