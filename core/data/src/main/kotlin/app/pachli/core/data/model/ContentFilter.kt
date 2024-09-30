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

import app.pachli.core.model.ContentFilter
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterAction.HIDE
import app.pachli.core.model.FilterAction.NONE
import app.pachli.core.model.FilterAction.WARN
import app.pachli.core.model.FilterContext
import app.pachli.core.model.FilterContext.ACCOUNT
import app.pachli.core.model.FilterContext.HOME
import app.pachli.core.model.FilterContext.NOTIFICATIONS
import app.pachli.core.model.FilterContext.PUBLIC
import app.pachli.core.model.FilterContext.THREAD
import app.pachli.core.model.FilterKeyword

/**
 * Returns a [ContentFilter] from a
 * [v2 Mastodon filter][app.pachli.core.network.model.Filter].
 */
fun ContentFilter.Companion.from(filter: app.pachli.core.network.model.Filter) = ContentFilter(
    id = filter.id,
    title = filter.title,
    contexts = filter.contexts.map { FilterContext.from(it) }.toSet(),
    expiresAt = filter.expiresAt,
    filterAction = FilterAction.from(filter.filterAction),
    keywords = filter.keywords.map { FilterKeyword.from(it) },
)

fun FilterContext.Companion.from(networkFilter: app.pachli.core.network.model.FilterContext) = when (networkFilter) {
    app.pachli.core.network.model.FilterContext.HOME -> HOME
    app.pachli.core.network.model.FilterContext.NOTIFICATIONS -> NOTIFICATIONS
    app.pachli.core.network.model.FilterContext.PUBLIC -> PUBLIC
    app.pachli.core.network.model.FilterContext.THREAD -> THREAD
    app.pachli.core.network.model.FilterContext.ACCOUNT -> ACCOUNT
}

fun FilterAction.Companion.from(networkAction: app.pachli.core.network.model.FilterAction) = when (networkAction) {
    app.pachli.core.network.model.FilterAction.NONE -> NONE
    app.pachli.core.network.model.FilterAction.WARN -> WARN
    app.pachli.core.network.model.FilterAction.HIDE -> HIDE
}

fun FilterKeyword.Companion.from(networkKeyword: app.pachli.core.network.model.FilterKeyword) = FilterKeyword(
    id = networkKeyword.id,
    keyword = networkKeyword.keyword,
    wholeWord = networkKeyword.wholeWord,
)

/**
 * Returns a [ContentFilter] from a
 * [v1 Mastodon filter][app.pachli.core.network.model.Filter].
 *
 * There are some restrictions imposed by the v1 filter;
 * - it can only have a single entry in the [keywords] list
 * - the [title] is identical to the keyword
 */
fun ContentFilter.Companion.from(filter: app.pachli.core.network.model.FilterV1) = ContentFilter(
    id = filter.id,
    title = filter.phrase,
    contexts = filter.contexts.map { FilterContext.from(it) }.toSet(),
    expiresAt = filter.expiresAt,
    filterAction = WARN,
    keywords = listOf(
        FilterKeyword(
            id = filter.id,
            keyword = filter.phrase,
            wholeWord = filter.wholeWord,
        ),
    ),
)
