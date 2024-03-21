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
    id("com.google.devtools.ksp")
    id("com.apollographql.apollo3") version "3.8.3"
}

application {
    mainClass = "app.pachli.mkserverversions.MainKt"
}

dependencies {
    // GraphQL client
    implementation("com.apollographql.apollo3:apollo-runtime:3.8.2")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.3")
    implementation("ch.qos.logback:logback-classic:1.5.3")

    // Moshi
    implementation(libs.moshi)
    ksp(libs.moshi.codegen)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2") // for parameterized tests
}

tasks.test {
    useJUnitPlatform()
}

apollo {
    service("service") {
        packageName = "app.pachli.mkserverversions.fediverseobserver"
    }
}
