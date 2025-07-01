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

package app.pachli.core.data.model

import app.pachli.core.database.model.StatusEntity
import app.pachli.core.model.ContentFilter
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterContext
import app.pachli.core.model.Status
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

    /** @return the [FilterAction] that should be applied to [status]. */
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
            result.filter.contexts.contains(filterContext)
        }

        return if (matchingKind.isNullOrEmpty()) {
            FilterAction.NONE
        } else {
            matchingKind.maxOf { it.filter.filterAction }
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
            result.filter.contexts.contains(filterContext)
        }

        return if (matchingKind.isNullOrEmpty()) {
            FilterAction.NONE
        } else {
            matchingKind.maxOf { it.filter.filterAction }
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
