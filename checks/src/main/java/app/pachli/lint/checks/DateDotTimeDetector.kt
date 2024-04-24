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

import com.android.tools.lint.checks.DataFlowAnalyzer
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

class DateDotTimeDetector : Detector(), SourceCodeScanner {
    override fun getApplicableConstructorTypes(): List<String> = listOf(CLASS_DATE)

    val fix = LintFix.create()
        .name("Replace with `System.currentTimeMillis()`")
        .replace()
        .text("Date().time")
        .with("System.currentTimeMillis()")
        .independent(true)
        .build()

    // Look for a Date() constructor call followed by a getTime() method call
    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        val method = node.getParentOfType(UMethod::class.java) ?: return
        val analyzer = object : DataFlowAnalyzer(listOf(node)) {
            var reported = false

            override fun receiver(call: UCallExpression) {
                if (reported) return
                if (call.methodName != METHOD_GET_TIME) return

                reported = true
                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getCallLocation(call, includeReceiver = true, includeArguments = true),
                    message = "Use `System.currentTimeMillis()`",
                    quickfixData = fix,
                )
            }
        }
        method.accept(analyzer)
    }

    companion object {
        private const val CLASS_DATE = "java.util.Date"
        private const val METHOD_GET_TIME = "getTime"

        val ISSUE = Issue.create(
            id = "DateDotTimeDetector",
            briefDescription = "Don't use `Date().time`, use `System.currentTimeMillis()`",
            explanation = """
                Calling `Date().time` / `Date().getTime()` is unnecessary object creation. Use
                `System.currentTimeMillis()` to avoid this.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DateDotTimeDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
