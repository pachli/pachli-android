package app.pachli.network

import app.pachli.core.data.model.from
import app.pachli.core.database.model.StatusEntity
import app.pachli.core.model.ContentFilter
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterContext
import app.pachli.core.network.model.FilterContext as NetworkFilterContext
import app.pachli.core.network.model.Status
import app.pachli.core.network.parseAsMastodonHtml
import java.util.Date
import java.util.regex.Pattern

/**
 * Filter statuses using V1 or V2 filters.
 *
 * Construct with [filterContext] that corresponds to the kind of timeline, and optionally the set
 * of v1 filters that should be applied.
 */
class ContentFilterModel(private val filterContext: FilterContext, v1ContentFilters: List<ContentFilter>? = null) {
    /** Pattern to use when matching v1 filters against a status. Null if these are v2 filters */
    private var pattern: Pattern? = null

    init {
        pattern = v1ContentFilters?.let { list ->
            makeFilter(list.filter { it.contexts.contains(filterContext) })
        }
    }

    /** @return the [FilterAction] that should be applied to this status */
    fun filterActionFor(status: Status): FilterAction {
        pattern?.let { pat ->
            // Patterns are expensive and thread-safe, matchers are neither.
            val matcher = pat.matcher("") ?: return FilterAction.NONE

            if (status.poll?.options?.any { matcher.reset(it.title).find() } == true) {
                return FilterAction.HIDE
            }

            val spoilerText = status.actionableStatus.spoilerText
            val attachmentsDescriptions = status.attachments.mapNotNull { it.description }

            return if (
                matcher.reset(status.actionableStatus.content.parseAsMastodonHtml().toString()).find() ||
                (spoilerText.isNotEmpty() && matcher.reset(spoilerText).find()) ||
                (attachmentsDescriptions.isNotEmpty() && matcher.reset(attachmentsDescriptions.joinToString("\n")).find())
            ) {
                FilterAction.HIDE
            } else {
                FilterAction.NONE
            }
        }

        val matchingKind = status.filtered?.filter { result ->
            result.filter.contexts.contains(NetworkFilterContext.from(filterContext))
        }

        return if (matchingKind.isNullOrEmpty()) {
            FilterAction.NONE
        } else {
            matchingKind.maxOf { FilterAction.from(it.filter.filterAction) }
        }
    }

    /** @return the [FilterAction] that should be applied to this status */
    fun filterActionFor(status: StatusEntity): FilterAction {
        pattern?.let { pat ->
            // Patterns are expensive and thread-safe, matchers are neither.
            val matcher = pat.matcher("") ?: return FilterAction.NONE

            if (status.poll?.options?.any { matcher.reset(it.title).find() } == true) {
                return FilterAction.HIDE
            }

            val spoilerText = status.spoilerText
            val attachmentsDescriptions = status.attachments?.mapNotNull { it.description }.orEmpty()

            return if (
                matcher.reset(status.content?.parseAsMastodonHtml().toString()).find() ||
                (spoilerText.isNotEmpty() && matcher.reset(spoilerText).find()) ||
                (attachmentsDescriptions.isNotEmpty() && matcher.reset(attachmentsDescriptions.joinToString("\n")).find())
            ) {
                FilterAction.HIDE
            } else {
                FilterAction.NONE
            }
        }

        val matchingKind = status.filtered?.filter { result ->
            result.filter.contexts.contains(NetworkFilterContext.from(filterContext))
        }

        return if (matchingKind.isNullOrEmpty()) {
            FilterAction.NONE
        } else {
            matchingKind.maxOf { FilterAction.from(it.filter.filterAction) }
        }
    }

    private fun filterToRegexToken(contentFilter: ContentFilter): String? {
        val keyword = contentFilter.keywords.first()
        val phrase = keyword.keyword
        val quotedPhrase = Pattern.quote(phrase)
        return if (keyword.wholeWord && ALPHANUMERIC.matcher(phrase).matches()) {
            "(^|\\W)$quotedPhrase($|\\W)"
        } else {
            quotedPhrase
        }
    }

    private fun makeFilter(contentFilters: List<ContentFilter>): Pattern? {
        val now = Date()
        val nonExpiredFilters = contentFilters.filter { it.expiresAt?.before(now) != true }
        if (nonExpiredFilters.isEmpty()) return null
        val tokens = nonExpiredFilters
            .asSequence()
            .map { filterToRegexToken(it) }
            .joinToString("|")

        return Pattern.compile(tokens, Pattern.CASE_INSENSITIVE)
    }

    companion object {
        private val ALPHANUMERIC = Pattern.compile("^\\w+$")
    }
}
