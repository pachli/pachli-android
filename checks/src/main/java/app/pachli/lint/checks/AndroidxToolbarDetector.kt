package app.pachli.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class AndroidxToolbarDetector : Detector(), XmlScanner {
    override fun getApplicableElements(): Collection<String> {
        return listOf(ANDROIDX_TOOLBAR)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val quickFixData = LintFix.create()
            .name("Replace with `com.google.android.material.appbar.MaterialToolbar`")
            .replace()
            .text(element.localName)
            .with("com.google.android.material.appbar.MaterialToolbar")
            .independent(true)
            .build()

        context.report(
            issue = ISSUE,
            scope = element,
            location = context.getElementLocation(element),
            message = "Use `com.google.android.material.appbar.MaterialToolbar` instead of `androidx.appcompat.widget.Toolbar`",
            quickfixData = quickFixData,
        )
    }

    companion object {
        private const val ANDROIDX_TOOLBAR = "androidx.appcompat.widget.Toolbar"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AndroidxToolbarDetector",
            briefDescription = "Don't use `androidx.appcompat.widget.Toolbar` in this project",
            explanation = """
                Use `com.google.android.material.appbar.MaterialToolbar` instead of
                `androidx.appcompat.widget.Toolbar` to ensure it works as expected with
                other Material components. See https://developer.android.com/reference/com/google/android/material/appbar/MaterialToolbar
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AndroidxToolbarDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE,
            ),
        )
    }
}
