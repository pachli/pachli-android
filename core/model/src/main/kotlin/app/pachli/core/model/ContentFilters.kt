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

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date
import kotlinx.parcelize.Parcelize

enum class ContentFilterVersion {
    V1,
    V2,
}

/**
 * Internal representation of a Mastodon filter, whether v1 or v2.
 *
 * This is a *content* filter, to distinguish it from filters that operate on
 * accounts, domains, or other data.
 *
 * @param id The server's ID for this filter
 * @param title Filter's title (label to use in the UI)
 * @param contexts One or more [FilterContext] the filter is applied to
 * @param expiresAt Date the filter expires, null if the filter does not expire
 * @param filterAction Action to take if the filter matches a status
 * @param keywords One or more [FilterKeyword] the filter matches against a status
 */
// The @JsonClass annotations are used when this is serialized to the database.
@Parcelize
@JsonClass(generateAdapter = true)
data class ContentFilter(
    val id: String,
    val title: String,
    val contexts: Set<FilterContext> = emptySet(),
    val expiresAt: Date? = null,
    val filterAction: FilterAction,
    val keywords: List<FilterKeyword> = emptyList(),
) : Parcelable {
    companion object
}

/** A filter choice, either content filter or account filter. */
// The @Json annotations are used when this is serialized by NewContentFilterConverterFactory.
enum class FilterAction {
    /** No filtering, show item as normal. */
    @Json(name = "none")
    NONE,

    /** Replace the item with a warning, allowing the user to click through. */
    @Json(name = "warn")
    WARN,

    /** Remove the item, with no indication to the user it was present. */
    @Json(name = "hide")
    HIDE,

    ;

    companion object
}

// The @JsonClass annotations are used when this is serialized to the database.
@Parcelize
@JsonClass(generateAdapter = true)
data class FilterKeyword(
    val id: String,
    val keyword: String,
    @Json(name = "whole_word") val wholeWord: Boolean,
) : Parcelable {
    companion object
}

/**
 * The contexts in which a filter should be applied, for both a
 * [v2](https://docs.joinmastodon.org/entities/Filter/#context) and
 * [v1](https://docs.joinmastodon.org/entities/V1_Filter/#context) Mastodon
 * filter. The API versions have identical contexts.
 */
// The @Json annotations are used when this is serialized by NewContentFilterConverterFactory
enum class FilterContext {
    /** Filter applies to home timeline and lists */
    @Json(name = "home")
    HOME,

    /** Filter applies to notifications */
    @Json(name = "notifications")
    NOTIFICATIONS,

    /** Filter applies to public timelines */
    @Json(name = "public")
    PUBLIC,

    /** Filter applies to expanded thread */
    @Json(name = "thread")
    THREAD,

    /** Filter applies when viewing a profile */
    @Json(name = "account")
    ACCOUNT,

    ;

    companion object {
        /**
         * @return The filter context for [timeline], or null if filters are not applied
         *     to this timeline.
         */
        fun from(timeline: Timeline): FilterContext? = when (timeline) {
            is Timeline.Home, is Timeline.UserList -> HOME
            is Timeline.User -> ACCOUNT
            Timeline.Notifications -> NOTIFICATIONS
            Timeline.Bookmarks,
            Timeline.Favourites,
            Timeline.PublicFederated,
            Timeline.PublicLocal,
            is Timeline.Hashtags,
            Timeline.TrendingStatuses,
            Timeline.TrendingHashtags,
            Timeline.TrendingLinks,
            -> PUBLIC
            Timeline.Conversations -> null
        }
    }
}
