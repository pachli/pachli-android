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
        fun from(kind: TimelineKind): FilterContext = when (kind) {
            is TimelineKind.Home, is TimelineKind.UserList -> HOME
            is TimelineKind.PublicFederated,
            is TimelineKind.PublicLocal,
            is TimelineKind.Tag,
            is TimelineKind.Favourites,
            -> PUBLIC
            is TimelineKind.User -> ACCOUNT
            else -> PUBLIC
        }
    }
}
