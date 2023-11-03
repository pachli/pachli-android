package app.pachli.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
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
        val incident = Incident(context, ISSUE)
            .message("Use `<com.google.android.material.appbar.MaterialToolbar>` instead of `<androidx.appcompat.widget.Toolbar>`")
            .at(element)
        context.report(incident)
    }

    companion object {
        private const val ANDROIDX_TOOLBAR = "androidx.appcompat.widget.Toolbar"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AndroidxToolbarDetector",
            briefDescription = "Don't use `<androidx.appcompat.widget.Toolbar>` in this project",
            explanation = "<androidx.appcompat.widget.Toolbar> found in source code.\n Use <com.google.android.material.appbar.MaterialToolbar> instead for this project.",
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
