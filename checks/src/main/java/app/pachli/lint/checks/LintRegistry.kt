package app.pachli.lint.checks

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

class LintRegistry : IssueRegistry() {
    override val issues: List<Issue>
        get() = listOf(
            AndroidxToolbarDetector.ISSUE,
            ContextCompatGetDrawableDetector.ISSUE,
            DateDotTimeDetector.ISSUE,
            IntentDetector.ISSUE,
        )

    override val api: Int
        get() = CURRENT_API

    override val vendor = Vendor(
        vendorName = "Pachli",
        identifier = "app.pachli.lint:pachli-lint",
        feedbackUrl = "https://github.com/pachli/pachli-android/issues",
        contact = "https://github.com/pachli/pachli-android",
    )
}
