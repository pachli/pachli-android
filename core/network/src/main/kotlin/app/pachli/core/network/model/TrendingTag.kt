/*
 * Copyright 2023 Tusky Contributors
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

import com.squareup.moshi.JsonClass

/**
 * Mastodon API Documentation: https://docs.joinmastodon.org/methods/trends/#tags
 *
 * @param name The name of the hashtag (after the #). The "caturday" in "#caturday".
 * (@param url The URL to your mastodon instance list for this hashtag.)
 * @param history A list of [TrendingTagHistory]. Each element contains metrics per day for this hashtag.
 * (@param following This is not listed in the APIs at the time of writing, but an instance is delivering it.)
 */
@JsonClass(generateAdapter = true)
data class TrendingTag(
    val name: String,
    val history: List<TrendingTagHistory>,
) {
    fun asModel() = app.pachli.core.model.TrendingTag(
        name = name,
        history = history.asModel(),
    )
}

@JvmName("iterableTrendingTagAsModel")
fun Iterable<TrendingTag>.asModel() = map { it.asModel() }

/**
 * Mastodon API Documentation: https://docs.joinmastodon.org/methods/trends/#tags
 *
 * @param day The day that this was posted in Unix Epoch Seconds.
 * @param accounts The number of accounts that have posted with this hashtag.
 * @param uses The number of posts with this hashtag.
 */
@JsonClass(generateAdapter = true)
data class TrendingTagHistory(
    val day: String,
    val accounts: String,
    val uses: String,
) {
    fun asModel() = app.pachli.core.model.TrendingTagHistory(
        day = day,
        accounts = accounts,
        uses = uses,
    )
}

@JvmName("iterableTrendingTagHistoryAsModel")
fun Iterable<TrendingTagHistory>.asModel() = map { it.asModel() }
