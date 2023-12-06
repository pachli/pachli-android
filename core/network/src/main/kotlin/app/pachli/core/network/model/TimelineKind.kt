/*
 * Copyright 2023 Pachli Association
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

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** A timeline's type. Hold's data necessary to display that timeline. */
@Parcelize
sealed interface TimelineKind : Parcelable {
    @Parcelize
    data object Home : TimelineKind

    @Parcelize
    data object PublicFederated : TimelineKind

    @Parcelize
    data object PublicLocal : TimelineKind

    @Parcelize
    data class Tag(val tags: List<String>) : TimelineKind

    /** Any timeline showing statuses from a single user */
    @Parcelize
    sealed interface User : TimelineKind {
        val id: String

        /** Timeline showing just the user's statuses (no replies) */
        @Parcelize
        data class Posts(override val id: String) : User

        /** Timeline showing the user's pinned statuses */
        @Parcelize
        data class Pinned(override val id: String) : User

        /** Timeline showing the user's top-level statuses and replies they have made */
        @Parcelize
        data class Replies(override val id: String) : User
    }

    @Parcelize
    data object Favourites : TimelineKind

    @Parcelize
    data object Bookmarks : TimelineKind

    @Parcelize
    data class UserList(val id: String, val title: String) : TimelineKind

    @Parcelize
    data object TrendingStatuses : TimelineKind
}
