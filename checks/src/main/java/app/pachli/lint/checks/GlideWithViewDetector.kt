/*
 * Copyright 2025 Pachli Association
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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class GlideWithViewDetector : Detector(), SourceCodeScanner {
    override fun getApplicableMethodNames() = listOf(METHOD_WITH)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, CLASS_GLIDE)) return
        if (method.name != METHOD_WITH) return
        if (!context.evaluator.methodMatches(
                method,
                CLASS_GLIDE,
                true,
                "android.view.View",
            )
        ) {
            return
        }

        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getCallLocation(node, includeReceiver = true, includeArguments = true),
            message = "Use an activity or fragment, not a view.",
        )
    }

    companion object {
        private const val CLASS_GLIDE = "com.bumptech.glide.Glide"
        private const val METHOD_WITH = "with"

        val ISSUE = Issue.create(
            id = "GlideWithViewDetector",
            briefDescription = "Don't use `Glide.with(view)`, pass the activity or fragment",
            explanation = """
                `Glide.with(view)` always uses the activity's lifecycle, even if the view is displayed
                by a fragment.

                This means in-progress loads are not paused or cancelled when the fragment is stopped
                or destroyed, only when the activity is destroyed.

                Always call `Glide.with` with an explicit activity or fragment.

                See https://github.com/bumptech/glide/issues/898
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                GlideWithViewDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
