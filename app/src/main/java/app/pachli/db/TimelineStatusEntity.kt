/* Copyright 2021 Tusky Contributors
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
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package app.pachli.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverters
import app.pachli.entity.FilterResult
import app.pachli.entity.Status

/**
 * We're trying to play smart here. Server sends us reblogs as two entities one embedded into
 * another (reblogged status is a field inside of "reblog" status). But it's really inefficient from
 * the DB perspective and doesn't matter much for the display/interaction purposes.
 * What if when we store reblog we don't store almost empty "reblog status" but we store
 * *reblogged* status and we embed "reblog status" into reblogged status. This reversed
 * relationship takes much less space and is much faster to fetch (no N+1 type queries or JSON
 * serialization).
 * "Reblog status", if present, is marked by [reblogServerId], and [reblogAccountId]
 * fields.
 */
@Entity(
    primaryKeys = ["serverId", "timelineUserId"],
    foreignKeys = (
        [
            ForeignKey(
                entity = TimelineAccountEntity::class,
                parentColumns = ["serverId", "timelineUserId"],
                childColumns = ["authorServerId", "timelineUserId"],
            ),
        ]
        ),
    // Avoiding rescanning status table when accounts table changes. Recommended by Room(c).
    indices = [Index("authorServerId", "timelineUserId")],
)
@TypeConverters(Converters::class)
data class TimelineStatusEntity(
    val serverId: String, // id never flips: we need it for sorting so it's a real id
    val url: String?,
    // our local id for the logged in user in case there are multiple accounts per instance
    val timelineUserId: Long,
    val authorServerId: String,
    val inReplyToId: String?,
    val inReplyToAccountId: String?,
    val content: String?,
    val createdAt: Long,
    val editedAt: Long?,
    val emojis: String?,
    val reblogsCount: Int,
    val favouritesCount: Int,
    val repliesCount: Int,
    val reblogged: Boolean,
    val bookmarked: Boolean,
    val favourited: Boolean,
    val sensitive: Boolean,
    val spoilerText: String,
    val visibility: Status.Visibility,
    val attachments: String?,
    val mentions: String?,
    val tags: String?,
    val application: String?,
    val reblogServerId: String?, // if it has a reblogged status, it's id is stored here
    val reblogAccountId: String?,
    val poll: String?,
    val muted: Boolean?,
    val pinned: Boolean,
    val card: String?,
    val language: String?,
    val filtered: List<FilterResult>?,
)

@Entity(
    primaryKeys = ["serverId", "timelineUserId"],
)
data class TimelineAccountEntity(
    val serverId: String,
    val timelineUserId: Long,
    val localUsername: String,
    val username: String,
    val displayName: String,
    val url: String,
    val avatar: String,
    val emojis: String,
    val bot: Boolean,
)

/**
 * The local view data for a status.
 *
 * There is *no* foreignkey relationship between this and [TimelineStatusEntity], as the view
 * data is kept even if the status is deleted from the local cache (e.g., during a refresh
 * operation).
 */
@Entity(
    primaryKeys = ["serverId", "timelineUserId"],
)
data class StatusViewDataEntity(
    val serverId: String,
    val timelineUserId: Long,
    /** Corresponds to [app.pachli.viewdata.StatusViewData.isExpanded] */
    val expanded: Boolean,
    /** Corresponds to [app.pachli.viewdata.StatusViewData.isShowingContent] */
    val contentShowing: Boolean,
    /** Corresponds to [app.pachli.viewdata.StatusViewData.isCollapsed] */
    val contentCollapsed: Boolean,
)

data class TimelineStatusWithAccount(
    @Embedded
    val status: TimelineStatusEntity,
    @Embedded(prefix = "a_")
    val account: TimelineAccountEntity,
    @Embedded(prefix = "rb_")
    val reblogAccount: TimelineAccountEntity? = null, // null when no reblog
    @Embedded(prefix = "svd_")
    val viewData: StatusViewDataEntity? = null,
)
