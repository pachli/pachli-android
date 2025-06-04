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

package app.pachli.lint.checks

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("ktlint:standard:function-naming")
class GlideWithViewDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = GlideWithViewDetector()

    override fun getIssues(): List<Issue> = listOf(GlideWithViewDetector.ISSUE)

    fun `test Glide with view emits warning`() {
        lint().files(
            View,
            Glide,
            kotlin(
                """
                package test.pkg

                import android.view.View
                import com.bumptech.glide.Glide

                val x = Glide.with(View())
            """,
            ).indented(),
        ).allowMissingSdk().testModes(TestMode.DEFAULT).run().expect(
            """src/test/pkg/test.kt:6: Warning: Use an activity or fragment, not a view. [GlideWithViewDetector]
val x = Glide.with(View())
        ~~~~~~~~~~~~~~~~~~
0 errors, 1 warnings""",
        )
    }

    companion object Stubs {
        /** Stub for `android.view.View` */
        private val View = java(
            """
            package android.view;

            public class View {}
            """,
        ).indented()

        /** Stub for `com.bumptech.glide.Glide` */
        private val Glide = java(
            """
                package com.bumptech.glide;

                import android.view.View;

                public class Glide {
                     public static RequestManager with(View view) { return RequestManager(); }
                }
            """,
        ).indented()
    }
}
