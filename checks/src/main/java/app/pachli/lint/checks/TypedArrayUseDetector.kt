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
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class TypedArrayUseDetector : Detector(), SourceCodeScanner {
    override fun getApplicableMethodNames() = listOf(METHOD_USE)

    private val fix = LintFix.create()
        .name("Replace with `androidx.core.content.res.use`")
        .replace()
        .text("use")
        .with("use")
        .imports("androidx.core.content.res.use")
        .independent(true)
        .build()

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (node.receiverType?.canonicalText != CLASS_TYPED_ARRAY) return
        if (method.name != METHOD_USE) return
        if (node.resolve()?.containingClass?.qualifiedName == CLASS_USE) return

        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getCallLocation(node, includeReceiver = false, includeArguments = false),
            message = "Import `androidx.core.content.res.use`",
            quickfixData = fix,
        )
    }

    companion object {
        private const val CLASS_TYPED_ARRAY = "android.content.res.TypedArray"
        private const val METHOD_USE = "use"
        private const val CLASS_USE = "androidx.core.content.res.TypedArrayKt"

        val ISSUE = Issue.create(
            id = "TypedArrayUseDetector",
            briefDescription = "Don't use `kotlin.use`, use `androidx.core.content.res.use`",
            explanation = """
                TypedArray implements AutoCloseable but doesn't have a desugared `close` on older devices,
                and will cause a class cast exception at runtime on older devices.
                See https://issuetracker.google.com/issues/262851206
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(
                TypedArrayUseDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
