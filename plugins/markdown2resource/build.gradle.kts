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

@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    `java-gradle-plugin`
    `maven-publish`
    kotlin("jvm") version "1.9.22"
}

group = "app.pachli.plugins"
version = "0.0.1"

gradlePlugin {
    plugins {
        create("markdown2resource") {
            id = "app.pachli.plugins.markdown2resource"
            implementationClass = "app.pachli.plugins.markdown2resource.Markdown2ResourcePlugin"
        }
    }
}

dependencies {
    implementation("com.android.tools.build:gradle:8.2.0")
    implementation("org.jetbrains:markdown:0.6.1")
    implementation("com.squareup:javapoet:1.13.0")
}

kotlin {
    jvmToolchain(17)
}
