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


import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.io.File

class AndroidLintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            when {
                pluginManager.hasPlugin("com.android.application") ->
                    configure<ApplicationExtension> { lint { configure(this@with) } }

                pluginManager.hasPlugin("com.android.library") ->
                    configure<LibraryExtension> { lint { configure(this@with) } }

                else -> {
                    pluginManager.apply("com.android.lint")
                    configure<Lint> { configure(this@with) }
                }
            }
        }
    }
}

private fun Lint.configure(project: Project) {
    lintConfig = File(project.findProject(":app")?.projectDir, "lint.xml")
    baseline = File("lint-baseline.xml")
}
