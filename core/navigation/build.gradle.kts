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
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.quadrant)
}

android {
    namespace = "app.pachli.core.navigation"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.database) // For DraftAttachment, used in ComposeOptions
    implementation(projects.core.model)
    implementation(projects.core.network) // For Attachment, used in AttachmentViewData

    implementation(libs.androidx.core.ktx) // IntentCompat
}

// ktlint checks generated files (https://github.com/JLLeitschuh/ktlint-gradle/issues/580) so
// ensure it's run after the navigation files have been created.
tasks.named("runKtlintCheckOverMainSourceSet").configure { dependsOn(":core:navigation:generateActivityClassNameConstants") }
