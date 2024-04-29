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

import com.android.tools.lint.client.api.TYPE_INT
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

// appcompat-lint doesn't detect ContextCompat.getDrawable,
// https://issuetracker.google.com/issues/337905331
class ContextCompatGetDrawableDetector : Detector(), SourceCodeScanner {
    override fun getApplicableMethodNames() = listOf(METHOD_GET_DRAWABLE)

    val fix = LintFix.create()
        .name("Replace with `AppCompatResources.getDrawable`")
        .replace()
        .text("ContextCompat.")
        .with("AppCompatResources.")
        .imports("androidx.appcompat.content.res.AppCompatResources")
        .independent(true)
        .build()

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, CLASS_CONTEXT_COMPAT)) return
        if (method.name != METHOD_GET_DRAWABLE) return
        if (!context.evaluator.methodMatches(
                method,
                CLASS_CONTEXT_COMPAT,
                true,
                "android.content.Context",
                TYPE_INT,
            )
        ) {
            return
        }

        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getCallLocation(node, includeReceiver = true, includeArguments = true),
            message = "Use AppCompatResources.getDrawable",
            quickfixData = fix,
        )
    }

    companion object {
        private const val CLASS_CONTEXT_COMPAT = "androidx.core.content.ContextCompat"
        private const val METHOD_GET_DRAWABLE = "getDrawable"

        val ISSUE = Issue.create(
            id = "ContextCompatGetDrawableDetector",
            briefDescription = "Don't use `ContextCompat.getDrawable()`, use `AppCompatResources.getDrawable()`",
            explanation = """
                AppCompatResources().getDrawable() backports features and bug fixes,
                ContextCompat.getDrawable() does not.
                See https://medium.com/@crafty/yes-contextcompat-just-saves-you-the-api-level-check-it-doesnt-back-port-and-features-or-bug-9cd7d5f09be4
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ContextCompatGetDrawableDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
