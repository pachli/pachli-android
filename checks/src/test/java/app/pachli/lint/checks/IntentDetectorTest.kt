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

package app.pachli.lint.checks

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("ktlint:standard:function-naming")
class IntentDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = IntentDetector()

    override fun getIssues(): List<Issue> = listOf(IntentDetector.ISSUE)

    fun `test Intent component constructor emits warning`() {
        lint().files(
            Context,
            Intent,
            kotlin(
                """
                package test.pkg

                import android.content.Context
                import android.content.Intent

                fun makeIntent(context: Context) = Intent(context, String::class.java)
            """,

            ).indented(),
        ).allowMissingSdk().run().expect(
            """src/test/pkg/test.kt:6: Warning: Use functions from core.navigation [IntentDetector]
fun makeIntent(context: Context) = Intent(context, String::class.java)
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
0 errors, 1 warnings""",
        )
    }

    fun `test Intent action and data component constructor emits warning`() {
        lint().files(
            Context,
            Intent,
            kotlin(
                """
                package test.pkg

                import android.content.Context
                import android.content.Intent

                fun makeIntent(context: Context) = Intent(
                    "someAction",
                    Uri.parse("https://example.com"),
                    context,
                    String::class.java,
                )
            """,
            ).indented(),
        ).allowMissingSdk().run().expect(
            """src/test/pkg/test.kt:6: Warning: Use functions from core.navigation [IntentDetector]
fun makeIntent(context: Context) = Intent(
                                   ^
0 errors, 1 warnings""",
        )
    }

    fun `test empty constructor does not warn`() {
        lint().files(
            Context,
            Intent,
            kotlin(
                """
                package test.pkg

                import android.content.Intent

                fun makeIntent() = Intent()
            """,
            ).indented(),
        ).allowMissingSdk().run().expectClean()
    }

    fun `test copy constructor does not warn`() {
        lint().files(
            Context,
            Intent,
            kotlin(
                """
                package test.pkg

                import android.content.Intent

                fun makeIntent(intent: Intent) = Intent(intent, 0)
            """,
            ).indented(),
        ).allowMissingSdk().run().expectClean()
    }

    fun `test action constructor does not warn`() {
        lint().files(
            Context,
            Intent,
            kotlin(
                """
                package test.pkg

                import android.content.Intent

                fun makeIntent() = Intent("some action")
            """,
            ).indented(),
        ).allowMissingSdk().run().expectClean()
    }

    fun `test action and uri constructor does not warn`() {
        lint().files(
            Context,
            Intent,
            kotlin(
                """
                package test.pkg

                import android.content.Intent

                fun makeIntent() = Intent("some action", Uri.parse("http://example.com"))
            """,
            ).indented(),
        ).allowMissingSdk().run().expectClean()
    }

    companion object Stubs {
        /** Stub for `android.content.Context` */
        private val Context = java(
            """
            package android.content;

            public class Context {}
            """,
        ).indented()

        /** Stub for `android.content.Intent` */
        private val Intent = java(
            """
            package android.content;

            import android.content.Context;

            public class Intent {
                public Intent() { return null; }
                public Intent(Intent o, int copyMode) { return null; }
                public Intent(String action) { return null; }
                public Intent(String action, Uri uri) { return null; }
                public Intent(Context packageContext, Class<?> cls) { return null; }
                public Intent(String action, Uri uri, Context packageContext, Class<?> cls) { return null; }
            }
            """,
        ).indented()
    }
}
