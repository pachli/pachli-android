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
import dev.zacsweers.moshix.sealed.annotations.NestedSealed
import dev.zacsweers.moshix.sealed.annotations.TypeLabel
import kotlinx.parcelize.Parcelize

/** A timeline's type. Hold's data necessary to display that timeline. */
@Parcelize
@JsonClass(generateAdapter = true, generator = "sealed:kind")
sealed interface Timeline : Parcelable {
    @Parcelize
    @TypeLabel("home")
    data object Home : Timeline

    @Parcelize
    @TypeLabel("notifications")
    data object Notifications : Timeline

    /** Federated timeline */
    @Parcelize
    @TypeLabel("federated")
    data object PublicFederated : Timeline

    /** Local timeline of the user's server */
    @Parcelize
    @TypeLabel("local")
    data object PublicLocal : Timeline

    // TODO: LOCAL_REMOTE

    @Parcelize
    @TypeLabel("hashtag")
    @JsonClass(generateAdapter = true)
    data class Hashtags(val tags: List<String>) : Timeline

    @Parcelize
    @TypeLabel("direct")
    data object Conversations : Timeline

    /** Any timeline showing statuses from a single user */
    @Parcelize
    @NestedSealed
    sealed interface User : Timeline {
        val id: String

        /** Timeline showing just a user's statuses (no replies) */
        @Parcelize
        @TypeLabel("userPosts")
        @JsonClass(generateAdapter = true)
        data class Posts(override val id: String) : User

        /** Timeline showing a user's pinned statuses */
        @Parcelize
        @TypeLabel("userPinned")
        @JsonClass(generateAdapter = true)
        data class Pinned(override val id: String) : User

        /** Timeline showing a user's top-level statuses and replies they have made */
        @Parcelize
        @TypeLabel("userReplies")
        @JsonClass(generateAdapter = true)
        data class Replies(override val id: String) : User
    }

    @Parcelize
    @TypeLabel("favourites")
    data object Favourites : Timeline

    @Parcelize
    @TypeLabel("bookmarks")
    data object Bookmarks : Timeline

    @Parcelize
    @TypeLabel("list")
    @JsonClass(generateAdapter = true)
    data class UserList(
        @Json(name = "listId")
        val listId: String,
        @Json(name = "title")
        val title: String,
    ) : Timeline

    @Parcelize
    @TypeLabel("trending_tags")
    data object TrendingHashtags : Timeline

    @Parcelize
    @TypeLabel("trending_links")
    data object TrendingLinks : Timeline

    @Parcelize
    @TypeLabel("trending_statuses")
    data object TrendingStatuses : Timeline

    // TODO: DRAFTS

    // TODO: SCHEDULED
}
