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

package app.pachli.core.model

/**
 * Sources of suggestions for followed accounts.
 *
 * Wraps and merges the different sources supplied across different Mastodon API
 * versions.
 */
enum class SuggestionSources {
    /** "Hand-picked by the {domain} team" */
    FEATURED,

    /** "This profile is one of the most followed on {domain}." */
    MOST_FOLLOWED,

    /** "This profile has been recently getting a lot of attention on {domain}." */
    MOST_INTERACTIONS,

    /** "This profile is similar to the profiles you have most recently followed." */
    SIMILAR_TO_RECENTLY_FOLLOWED,

    /** "Popular among people you follow" */
    FRIENDS_OF_FRIENDS,

    UNKNOWN,
}

data class Suggestion(
    val sources: List<SuggestionSources> = emptyList(),
    val account: Account,
)
