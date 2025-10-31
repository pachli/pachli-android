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

@Suppress("ktlint:standard:function-naming")
class StringResourceAngleBracketDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = StringResourceAngleBracketDetector()

    override fun getIssues(): List<Issue> = listOf(StringResourceAngleBracketDetector.ISSUE)

    fun `test getString on resource using angle-bracket emits warning`() {
        lint().files(
            Context,
            kotlin(
                """
               package test.pkg

               import android.content.Context

               fun foo(context: Context) = return context.getString(R.string.test)
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
        ).allowMissingSdk().testModes(TestMode.DEFAULT).run().expect(
            """res/values/strings.xml:4: Warning: Replace with &lt;/b&gt; [StringResourceAngleBracketDetector]
    <string name="test">This is in <b>bold</b></string>
                                          ~~~~
res/values/strings.xml:4: Warning: Replace with &lt;b&gt; [StringResourceAngleBracketDetector]
    <string name="test">This is in <b>bold</b></string>
                                   ~~~
0 errors, 2 warnings""",
        )
    }

    fun `test getString on resource using entity is clean`() {
        lint().files(
            Context,
            kotlin(
                """
               package test.pkg

               import android.content.Context

               fun foo(context: Context) = return context.getString(R.string.test)
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
        ).allowMissingSdk().testModes(TestMode.DEFAULT).run().expectClean()
    }

    companion object Stubs {
        /** Stub for `android.content.Context` */
        private val Context = java(
            """
            package android.content;

            public class Context {
                public final String getString(int resId) { return null; }
            }
            """,
        ).indented()
    }
}
