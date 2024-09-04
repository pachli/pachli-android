package app.pachli.network

import app.pachli.core.data.model.ContentFilter
import app.pachli.core.network.model.Filter as NetworkFilter
import app.pachli.core.network.model.FilterContext
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
class FilterModel(private val filterContext: FilterContext, v1ContentFilters: List<ContentFilter>? = null) {
    /** Pattern to use when matching v1 filters against a status. Null if these are v2 filters */
    private var pattern: Pattern? = null

    init {
        pattern = v1ContentFilters?.let { list ->
            makeFilter(list.filter { it.contexts.contains(filterContext) })
        }
    }

    /** @return the [ContentFilter.Action] that should be applied to this status */
    fun filterActionFor(status: Status): NetworkFilter.Action {
        pattern?.let { pat ->
            // Patterns are expensive and thread-safe, matchers are neither.
            val matcher = pat.matcher("") ?: return NetworkFilter.Action.NONE

            if (status.poll?.options?.any { matcher.reset(it.title).find() } == true) {
                return NetworkFilter.Action.HIDE
            }

            val spoilerText = status.actionableStatus.spoilerText
            val attachmentsDescriptions = status.attachments.mapNotNull { it.description }

            return if (
                matcher.reset(status.actionableStatus.content.parseAsMastodonHtml().toString()).find() ||
                (spoilerText.isNotEmpty() && matcher.reset(spoilerText).find()) ||
                (attachmentsDescriptions.isNotEmpty() && matcher.reset(attachmentsDescriptions.joinToString("\n")).find())
            ) {
                NetworkFilter.Action.HIDE
            } else {
                NetworkFilter.Action.NONE
            }
        }

        val matchingKind = status.filtered?.filter { result ->
            result.filter.contexts.contains(filterContext)
        }

        return if (matchingKind.isNullOrEmpty()) {
            NetworkFilter.Action.NONE
        } else {
            matchingKind.maxOf { it.filter.action }
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
