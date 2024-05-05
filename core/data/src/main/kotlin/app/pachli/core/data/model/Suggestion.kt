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

import app.pachli.core.network.json.Default
import app.pachli.core.network.model.Account
import app.pachli.core.network.model.SuggestionSource

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

    @Default
    UNKNOWN,

    ;

    companion object {
        fun from(networkSuggestionSources: app.pachli.core.network.model.SuggestionSources) = when (networkSuggestionSources) {
            app.pachli.core.network.model.SuggestionSources.FEATURED -> FEATURED
            app.pachli.core.network.model.SuggestionSources.MOST_FOLLOWED -> MOST_FOLLOWED
            app.pachli.core.network.model.SuggestionSources.MOST_INTERACTIONS -> MOST_INTERACTIONS
            app.pachli.core.network.model.SuggestionSources.SIMILAR_TO_RECENTLY_FOLLOWED -> SIMILAR_TO_RECENTLY_FOLLOWED
            app.pachli.core.network.model.SuggestionSources.FRIENDS_OF_FRIENDS -> FRIENDS_OF_FRIENDS
            app.pachli.core.network.model.SuggestionSources.UNKNOWN -> UNKNOWN
        }

        fun from(networkSuggestionSource: SuggestionSource) = when (networkSuggestionSource) {
            SuggestionSource.STAFF -> FEATURED
            SuggestionSource.PAST_INTERACTIONS -> SIMILAR_TO_RECENTLY_FOLLOWED
            SuggestionSource.GLOBAL -> MOST_FOLLOWED
            SuggestionSource.UNKNOWN -> UNKNOWN
        }
    }
}

data class Suggestion(
    val sources: List<SuggestionSources> = emptyList(),
    val account: Account,
) {
    companion object {
        fun from(networkSuggestion: app.pachli.core.network.model.Suggestion): Suggestion {
            networkSuggestion.sources?.let { sources ->
                return Suggestion(
                    sources = sources.map { SuggestionSources.from(it) },
                    account = networkSuggestion.account,
                )
            }

            return Suggestion(
                sources = listOf(SuggestionSources.from(networkSuggestion.source)),
                account = networkSuggestion.account,
            )
        }
    }
}
