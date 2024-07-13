/*
 * Copyright 2024 Pachli Association
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

import android.os.Parcelable
import app.pachli.core.network.model.Filter.Action
import app.pachli.core.network.model.FilterContext
import app.pachli.core.network.model.FilterKeyword
import java.util.Date
import kotlinx.parcelize.Parcelize

/** Reasons why a filter might be invalid */
enum class FilterValidationError {
    /** Filter title is empty or blank */
    NO_TITLE,

    /** Filter has no keywords */
    NO_KEYWORDS,

    /** Filter has no contexts */
    NO_CONTEXT,
}

/**
 * Internal representation of a Mastodon filter, whether v1 or v2.
 *
 * @param id The server's ID for this filter
 * @param title Filter's title (label to use in the UI)
 * @param contexts One or more [FilterContext] the filter is applied to
 * @param expiresAt Date the filter expires, null if the filter does not expire
 * @param action Action to take if the filter matches a status
 * @param keywords One or more [FilterKeyword] the filter matches against a status
 */
@Parcelize
data class Filter(
    val id: String,
    val title: String,
    val contexts: Set<FilterContext> = emptySet(),
    val expiresAt: Date? = null,
    val action: Action,
    val keywords: List<FilterKeyword> = emptyList(),
) : Parcelable {
    /**
     * @return Set of [FilterValidationError] given the current state of the
     * filter. Empty if there are no validation errors.
     */
    fun validate() = buildSet {
        if (title.isBlank()) add(FilterValidationError.NO_TITLE)
        if (keywords.isEmpty()) add(FilterValidationError.NO_KEYWORDS)
        if (contexts.isEmpty()) add(FilterValidationError.NO_CONTEXT)
    }

    companion object {
        /**
         * Returns a [Filter] from a
         * [v2 Mastodon filter][app.pachli.core.network.model.Filter].
         */
        fun from(filter: app.pachli.core.network.model.Filter) = Filter(
            id = filter.id,
            title = filter.title,
            contexts = filter.contexts,
            expiresAt = filter.expiresAt,
            action = filter.action,
            keywords = filter.keywords,
        )

        /**
         * Returns a [Filter] from a
         * [v1 Mastodon filter][app.pachli.core.network.model.Filter].
         *
         * There are some restrictions imposed by the v1 filter;
         * - it can only have a single entry in the [keywords] list
         * - the [title] is identical to the keyword
         */
        fun from(filter: app.pachli.core.network.model.FilterV1) = Filter(
            id = filter.id,
            title = filter.phrase,
            contexts = filter.contexts,
            expiresAt = filter.expiresAt,
            action = Action.WARN,
            keywords = listOf(
                FilterKeyword(
                    id = filter.id,
                    keyword = filter.phrase,
                    wholeWord = filter.wholeWord,
                ),
            ),
        )
    }
}

/** A new filter keyword; has no ID as it has not been saved to the server. */
data class NewFilterKeyword(
    val keyword: String,
    val wholeWord: Boolean,
)
