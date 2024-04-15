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

import com.android.SdkConstants.CLASS_INTENT
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getQualifiedName
import org.jetbrains.uast.util.isConstructorCall

class IntentDetector : Detector(), Detector.UastScanner {
    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            // Ignore anything that is not constructing an Intent
            if (!node.isConstructorCall()) return
            val classRef = node.classReference ?: return
            val className = classRef.getQualifiedName()
            if (className != CLASS_INTENT) return

            // Ignore calls that don't have 2 or 4 parameters
            val constructor = node.resolve() ?: return
            val parameters = constructor.parameterList.parameters
            if (parameters.size != 2 && parameters.size != 4) return

            // Ignore calls where the last parameter is not a class literal
            val lastParam = parameters.last()
            if (lastParam.type.canonicalText != "java.lang.Class<?>") return

            context.report(
                issue = ISSUE,
                scope = node,
                location = context.getCallLocation(node, true, true),
                message = "Use functions from `core.navigation`",
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "IntentDetector",
            briefDescription = "Don't use `Intent(...)`, use functions from core.navigation",
            explanation = """
                Creating an `Intent` with a class from another module can create unnecessary or circular
                dependencies. Use the `...Intent` classes in `core.navigation` to create an intent for
                the appropriate `Activity`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                IntentDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
