/*
 * Copyright (c) 2026 Pachli Association
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
    kotlin("kapt")
}

android {
    namespace = "app.pachli.core.markwon"
    packaging {
        resources.excludes.apply {
            // Otherwise this error:
            // "2 files found with path 'META-INF/versions/9/OSGI-INF/MANIFEST.MF' from inputs:"
            add("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }
}

configurations {
    androidTestImplementation {
        exclude(group = "org.jetbrains", module = "annotations")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)?.because("LinkMovementMethodCompat")
    implementation(libs.android.material)?.because("Use Material Theme colours")

    // Markdown support
    api(libs.markwon)
    implementation(libs.markwon.html)
    implementation(libs.markwon.inline.parser)
    implementation(libs.markwon.latex)
    implementation(libs.jlatexmath.android)
    implementation(libs.markwon.simple.ext)
    implementation(libs.markwon.strikethrough)
    implementation(libs.markwon.syntax.highlight)
    kapt(libs.prism4j)
    implementation(libs.ksoup.entities)
}
