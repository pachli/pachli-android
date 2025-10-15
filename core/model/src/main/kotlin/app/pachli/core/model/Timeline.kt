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
import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.NestedSealed
import dev.zacsweers.moshix.sealed.annotations.TypeLabel
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/** A timeline's type. Hold's data necessary to display that timeline. */
@Parcelize
@JsonClass(generateAdapter = true, generator = "sealed:kind")
sealed class Timeline : Parcelable {
    /**
     * **If** this timeline restores the reading position then this is
     * the value to use for RemoteKeyEntity.timelineId when saving the
     * reading position.
     *
     * Null if this timeline does not support restoring the reading
     * position.
     *
     * This also needs to be extensible in the future to cover the case where
     * the user might have multiple timelines from the same base timeline, but
     * with different configurations. E.g., two home timelines, one with boosts
     * and replies turned off, and one with boosts and replies turned on.
     */
    @IgnoredOnParcel
    open val remoteKeyTimelineId: String? = null

    @TypeLabel("home")
    data object Home : Timeline() {
        @IgnoredOnParcel
        override val remoteKeyTimelineId: String = "HOME"
    }

    @TypeLabel("notifications")
    data object Notifications : Timeline() {
        @IgnoredOnParcel
        override val remoteKeyTimelineId: String = "NOTIFICATIONS"
    }

    /** Federated timeline */
    @TypeLabel("federated")
    data object PublicFederated : Timeline() {
        @IgnoredOnParcel
        override val remoteKeyTimelineId: String = "FEDERATED"
    }

    /** Local timeline of the user's server */
    @TypeLabel("local")
    data object PublicLocal : Timeline() {
        @IgnoredOnParcel
        override val remoteKeyTimelineId: String = "LOCAL"
    }

    // TODO: LOCAL_REMOTE

    @TypeLabel("hashtag")
    @JsonClass(generateAdapter = true)
    data class Hashtags(val tags: List<String>) : Timeline()

    @TypeLabel("direct")
    data object Conversations : Timeline()

    /**
     * Any timeline showing statuses from a single user.
     *
     * @property id ID of the account that posted the statuses.
     */
    @NestedSealed
    @Parcelize
    sealed class User : Timeline() {
        abstract val id: String

        /** Timeline showing just a user's statuses (no replies) */
        @TypeLabel("userPosts")
        @JsonClass(generateAdapter = true)
        data class Posts(override val id: String) : User()

        /** Timeline showing a user's pinned statuses */
        @TypeLabel("userPinned")
        @JsonClass(generateAdapter = true)
        data class Pinned(override val id: String) : User()

        /**
         * Timeline showing a user's top-level statuses and replies they have made.
         *
         * @property excludeReblogs If true, statuses the account has reblogged
         * are excluded from the timeline.
         */
        @TypeLabel("userReplies")
        @JsonClass(generateAdapter = true)
        data class Replies(
            override val id: String,
            val excludeReblogs: Boolean = false,
        ) : User()
    }

    @TypeLabel("favourites")
    data object Favourites : Timeline()

    @TypeLabel("bookmarks")
    data object Bookmarks : Timeline()

    @TypeLabel("list")
    @JsonClass(generateAdapter = true)
    data class UserList(
        val listId: String,
        val title: String,
    ) : Timeline() {
        @IgnoredOnParcel
        override val remoteKeyTimelineId: String = "USER_LIST:$listId"
    }

    @TypeLabel("trending_tags")
    data object TrendingHashtags : Timeline()

    @TypeLabel("trending_links")
    data object TrendingLinks : Timeline()

    @TypeLabel("trending_statuses")
    data object TrendingStatuses : Timeline()

    /** Timeline of statuses that mention [url] (which has [title]). */
    @TypeLabel("link")
    @JsonClass(generateAdapter = true)
    data class Link(val url: String, val title: String) : Timeline()

    // TODO: DRAFTS

    // TODO: SCHEDULED
}
