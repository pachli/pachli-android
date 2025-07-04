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
    kotlin("kapt")
}

android {
    namespace = "app.pachli.core.ui"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.preferences)
        ?.because("PreferenceEnum types in EnumListPreference")

    implementation(libs.retrofit.core)
        ?.because("Uses HttpException")
    implementation(projects.core.network)
        ?.because("ThrowableExtensions uses getServerErrorMessage")

    implementation(libs.bundles.glide)
        ?.because("Loads account avatars and emojis")

    // Uses JsonDataException from Moshi
    implementation(libs.moshi)

    // Some views inherit from AndroidX views
    implementation(libs.bundles.androidx)

    implementation(libs.bundles.glide)
        ?.because("Loads account avatars and emojis")

    api(libs.material.iconics)
    api(libs.material.typeface)

    // Markdown support
    implementation(libs.markwon)
    implementation(libs.markwon.html)
    implementation(libs.markwon.inline.parser)
    implementation(libs.markwon.latex)
    implementation(libs.markwon.simple.ext)
    implementation(libs.markwon.strikethrough)
    implementation(libs.markwon.syntax.highlight)
    kapt(libs.prism4j)
    implementation(libs.ksoup.entities)
    implementation(libs.jlatexmath.android)
}
