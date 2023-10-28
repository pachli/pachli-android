package app.pachli.network

import app.pachli.entity.Filter
import app.pachli.entity.FilterV1
import app.pachli.entity.Status
import app.pachli.util.parseAsMastodonHtml
import java.util.Date
import java.util.regex.Pattern

/**
 * Filter statuses using V1 or V2 filters.
 *
 * Construct with [filterKind] that corresponds to the kind of timeline, and optionally the set
 * of v1 filters that should be applied.
 */
class FilterModel constructor(private val filterKind: Filter.Kind, v1filters: List<FilterV1>? = null) {
    /** Pattern to use when matching v1 filters against a status. Null if these are v2 filters */
    private var pattern: Pattern? = null

    init {
        pattern = v1filters?.let { list ->
            makeFilter(list.filter { it.context.contains(filterKind.kind) })
        }
    }

    /** @return the [Filter.Action] that should be applied to this status */
    fun filterActionFor(status: Status): Filter.Action {
        pattern?.let { pat ->
            // Patterns are expensive and thread-safe, matchers are neither.
            val matcher = pat.matcher("") ?: return Filter.Action.NONE

            if (status.poll?.options?.any { matcher.reset(it.title).find() } == true) {
                return Filter.Action.HIDE
            }

            val spoilerText = status.actionableStatus.spoilerText
            val attachmentsDescriptions = status.attachments.mapNotNull { it.description }

            return if (
                matcher.reset(status.actionableStatus.content.parseAsMastodonHtml().toString()).find() ||
                (spoilerText.isNotEmpty() && matcher.reset(spoilerText).find()) ||
                (attachmentsDescriptions.isNotEmpty() && matcher.reset(attachmentsDescriptions.joinToString("\n")).find())
            ) {
                Filter.Action.HIDE
            } else {
                Filter.Action.NONE
            }
        }

        val matchingKind = status.filtered?.filter { result ->
            result.filter.kinds.contains(filterKind)
        }

        return if (matchingKind.isNullOrEmpty()) {
            Filter.Action.NONE
        } else {
            matchingKind.maxOf { it.filter.action }
        }
    }

    private fun filterToRegexToken(filter: FilterV1): String? {
        val phrase = filter.phrase
        val quotedPhrase = Pattern.quote(phrase)
        return if (filter.wholeWord && ALPHANUMERIC.matcher(phrase).matches()) {
            String.format("(^|\\W)%s($|\\W)", quotedPhrase)
        } else {
            quotedPhrase
        }
    }

    private fun makeFilter(filters: List<FilterV1>): Pattern? {
        val now = Date()
        val nonExpiredFilters = filters.filter { it.expiresAt?.before(now) != true }
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
