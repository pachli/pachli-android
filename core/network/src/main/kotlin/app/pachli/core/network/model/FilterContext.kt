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

import app.pachli.core.model.Timeline
import app.pachli.core.network.json.Default
import app.pachli.core.network.json.HasDefault
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * The contexts in which a filter should be applied, for both a
 * [v2](https://docs.joinmastodon.org/entities/Filter/#context) and
 * [v1](https://docs.joinmastodon.org/entities/V1_Filter/#context) Mastodon
 * filter. The API versions have identical contexts.
 */
@JsonClass(generateAdapter = false)
@HasDefault
enum class FilterContext {
    /** Filter applies to home timeline and lists */
    @Json(name = "home")
    HOME,

    /** Filter applies to notifications */
    @Json(name = "notifications")
    NOTIFICATIONS,

    /** Filter applies to public timelines */
    @Default
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
        @Deprecated("Use app.pachli.core.model.FilterContext instead")
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

        fun from(filterContext: app.pachli.core.model.FilterContext) = when (filterContext) {
            app.pachli.core.model.FilterContext.HOME -> HOME
            app.pachli.core.model.FilterContext.NOTIFICATIONS -> NOTIFICATIONS
            app.pachli.core.model.FilterContext.PUBLIC -> PUBLIC
            app.pachli.core.model.FilterContext.THREAD -> THREAD
            app.pachli.core.model.FilterContext.ACCOUNT -> ACCOUNT
        }
    }
}
