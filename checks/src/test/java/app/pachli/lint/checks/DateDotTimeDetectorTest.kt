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
class DateDotTimeDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = DateDotTimeDetector()

    override fun getIssues(): List<Issue> = listOf(DateDotTimeDetector.ISSUE)

    fun `test Intent component constructor emits warning`() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import java.util.Date

                fun getTimeMills() = Date().time
            """,

            ).indented(),
        ).allowMissingSdk().testModes(TestMode.DEFAULT).run().expect(
            """src/test/pkg/test.kt:5: Warning: Use System.currentTimeMillis() [DateDotTimeDetector]
fun getTimeMills() = Date().time
                     ~~~~~~~~~~~
0 errors, 1 warnings""",
        )
    }
}
