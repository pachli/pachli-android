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

package app.pachli.core.model

/**
 * A content filter to be created or updated.
 *
 * Same as [ContentFilter] except a [NewContentFilter] does not have an [id][ContentFilter.id], as it
 * has not been created on the server.
 */
data class NewContentFilter(
    val title: String,
    val contexts: Set<FilterContext>,
    val expiresIn: Int,
    val filterAction: FilterAction,
    val keywords: List<NewContentFilterKeyword>,
) {
    fun toNewContentFilterV1() = this.keywords.map { keyword ->
        NewContentFilterV1(
            phrase = keyword.keyword,
            contexts = this.contexts,
            expiresIn = this.expiresIn,
            irreversible = false,
            wholeWord = keyword.wholeWord,
        )
    }

    companion object {
        fun from(contentFilter: ContentFilter) = NewContentFilter(
            title = contentFilter.title,
            contexts = contentFilter.contexts,
            expiresIn = -1,
            filterAction = contentFilter.filterAction,
            keywords = contentFilter.keywords.map {
                NewContentFilterKeyword(
                    keyword = it.keyword,
                    wholeWord = it.wholeWord,
                )
            },
        )
    }
}

/** A new filter keyword; has no ID as it has not been saved to the server. */
data class NewContentFilterKeyword(
    val keyword: String,
    val wholeWord: Boolean,
)

data class NewContentFilterV1(
    val phrase: String,
    val contexts: Set<FilterContext>,
    val expiresIn: Int,
    val irreversible: Boolean,
    val wholeWord: Boolean,
)
