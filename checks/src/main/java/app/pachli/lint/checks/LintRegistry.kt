package app.pachli.lint.checks

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class LintRegistry : IssueRegistry() {
    override val issues: List<Issue>
        get() = listOf(
            AndroidxToolbarDetector.ISSUE,
            IntentDetector.ISSUE,
        )

    override val api: Int
        get() = CURRENT_API
}
