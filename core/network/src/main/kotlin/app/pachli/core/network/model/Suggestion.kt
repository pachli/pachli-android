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

package app.pachli.core.network.model

import app.pachli.core.network.json.Default
import app.pachli.core.network.json.HasDefault
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Sources for suggestions returned from Mastodon 3.x.
 *
 * See [https://docs.joinmastodon.org/entities/Suggestion/](https://docs.joinmastodon.org/entities/Suggestion/).
 */
@HasDefault
enum class SuggestionSource {
    /** "This account was manually recommended by your administration team" */
    @Json(name = "staff")
    STAFF,

    /** "You have interacted with this account previously" */
    @Json(name = "past_interactions")
    PAST_INTERACTIONS,

    /** "This account has many reblogs, favourites, and active local followers within the last 30 days" */
    @Json(name = "global")
    GLOBAL,

    @Default
    UNKNOWN,
}

/**
 * Sources for suggestions returned from Mastodon 4.x
 * ([https://github.com/mastodon/documentation/issues/1398](https://github.com/mastodon/documentation/issues/1398))
 */
@HasDefault
enum class SuggestionSources {
    /** "Hand-picked by the {domain} team" */
    @Json(name = "featured")
    FEATURED,

    /** "This profile is one of the most followed on {domain}." */
    @Json(name = "most_followed")
    MOST_FOLLOWED,

    /** "This profile has been recently getting a lot of attention on {domain}." */
    @Json(name = "most_interactions")
    MOST_INTERACTIONS,

    /** "This profile is similar to the profiles you have most recently followed." */
    @Json(name = "similar_to_recently_followed")
    SIMILAR_TO_RECENTLY_FOLLOWED,

    /** "Popular among people you follow" */
    @Json(name = "friends_of_friends")
    FRIENDS_OF_FRIENDS,

    @Default
    UNKNOWN,
}

/**
 * A suggested account to follow.
 *
 * @property source The single reason for this suggestion.
 * @property sources One or more reasons for this suggestion.
 * @property account The suggested account.
 */
@JsonClass(generateAdapter = true)
data class Suggestion(
    @Deprecated("Mastodon 4.x switched to sources")
    val source: SuggestionSource,
    val sources: List<SuggestionSources>? = null,
    val account: Account,
)
