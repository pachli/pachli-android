/*
 * Copyright (c) 2025 Pachli Association
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

class StringResourceEntityDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = StringResourceEntityDetector()

    override fun getIssues(): List<Issue> = listOf(StringResourceEntityDetector.ISSUE)

    fun `test getText on resource using angle-bracket is clean`() {
        lint().files(
            Context,
            kotlin(
                """
               package test.pkg

               import android.content.Context

               fun foo(context: Context) = return context.getText(R.string.test)
               """,
            ),
            xml(
                "res/values/strings.xml",
                """
                    <?xml version="1.0" encoding="utf-8"?>

                    <resources>
                        <string name="test">This is in <b>bold</b></string>
                    </resources>
                """.trimIndent(),
            ).indented(),
        ).allowMissingSdk().testModes(TestMode.DEFAULT).run().expectClean()
    }

    fun `test getString on resource using entity emits warning`() {
        lint().files(
            Context,
            kotlin(
                """
               package test.pkg

               import android.content.Context

               fun foo(context: Context) = return context.getText(R.string.test)
               """,
            ),
            xml(
                "res/values/strings.xml",
                """
                    <?xml version="1.0" encoding="utf-8"?>

                    <resources>
                        <string name="test">This is in &lt;b>bold&lt;/b></string>
                    </resources>
                """.trimIndent(),
            ).indented(),
        ).allowMissingSdk().testModes(TestMode.DEFAULT).run().expect(
            """
res/values/strings.xml:4: Warning: Replace with < [StringResourceEntityDetector]
    <string name="test">This is in &lt;b>bold&lt;/b></string>
                                   ~~~~
res/values/strings.xml:4: Warning: Replace with < [StringResourceEntityDetector]
    <string name="test">This is in &lt;b>bold&lt;/b></string>
                                             ~~~~
0 errors, 2 warnings
            """.trimIndent(),
        )
    }

    companion object Stubs {
        /** Stub for `android.content.Context` */
        private val Context = java(
            """
            package android.content;

            public class Context {
                public final String getText(int resId) { return null; }
            }
            """,
        ).indented()
    }
}
