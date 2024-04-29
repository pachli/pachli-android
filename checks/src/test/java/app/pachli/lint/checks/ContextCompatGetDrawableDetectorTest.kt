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
class ContextCompatGetDrawableDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = ContextCompatGetDrawableDetector()

    override fun getIssues(): List<Issue> = listOf(ContextCompatGetDrawableDetector.ISSUE)

    fun `test Intent component constructor emits warning`() {
        lint().files(
            Context,
            ContextCompat,
            kotlin(
                """
                package test.pkg

                import android.content.Context
                import androidx.core.content.ContextCompat

                fun foo() = ContextCompat.getDrawable(Context(), 0)
            """,

            ).indented(),
        ).allowMissingSdk().testModes(TestMode.DEFAULT).run().expect(
            """src/test/pkg/test.kt:6: Warning: Use AppCompatResources.getDrawable [ContextCompatGetDrawableDetector]
fun foo() = ContextCompat.getDrawable(Context(), 0)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
0 errors, 1 warnings""",
        )
    }

    companion object Stubs {
        /** Stub for `android.content.Context` */
        private val Context = java(
            """
            package android.content;

            public class Context {}
            """,
        ).indented()

        /** Stub for `androidx.core.content.ContextCompat` */
        private val ContextCompat = java(
            """
                package androidx.core.content;

                import android.content.Context;

                public class ContextCompat {
                     public static void getDrawable(Context context, int resource) { return null; }
                }
            """,
        ).indented()
    }
}
