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
import app.pachli.components.timeline.emojisListType
import app.pachli.entity.FilterResult
import app.pachli.entity.Status
import app.pachli.entity.TimelineAccount
import com.google.gson.Gson

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
) {
    companion object {
        fun from(status: Status, timelineUserId: Long, gson: Gson) = TimelineStatusEntity(
            serverId = status.id,
            url = status.actionableStatus.url,
            timelineUserId = timelineUserId,
            authorServerId = status.actionableStatus.account.id,
            inReplyToId = status.actionableStatus.inReplyToId,
            inReplyToAccountId = status.actionableStatus.inReplyToAccountId,
            content = status.actionableStatus.content,
            createdAt = status.actionableStatus.createdAt.time,
            editedAt = status.actionableStatus.editedAt?.time,
            emojis = status.actionableStatus.emojis.let(gson::toJson),
            reblogsCount = status.actionableStatus.reblogsCount,
            favouritesCount = status.actionableStatus.favouritesCount,
            reblogged = status.actionableStatus.reblogged,
            favourited = status.actionableStatus.favourited,
            bookmarked = status.actionableStatus.bookmarked,
            sensitive = status.actionableStatus.sensitive,
            spoilerText = status.actionableStatus.spoilerText,
            visibility = status.actionableStatus.visibility,
            attachments = status.actionableStatus.attachments.let(gson::toJson),
            mentions = status.actionableStatus.mentions.let(gson::toJson),
            tags = status.actionableStatus.tags.let(gson::toJson),
            application = status.actionableStatus.application.let(gson::toJson),
            reblogServerId = status.reblog?.id,
            reblogAccountId = status.reblog?.let { status.account.id },
            poll = status.actionableStatus.poll.let(gson::toJson),
            muted = status.actionableStatus.muted,
            pinned = status.actionableStatus.pinned == true,
            card = status.actionableStatus.card?.let(gson::toJson),
            repliesCount = status.actionableStatus.repliesCount,
            language = status.actionableStatus.language,
            filtered = status.actionableStatus.filtered,
        )
    }
}

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
) {
    fun toTimelineAccount(gson: Gson): TimelineAccount {
        return TimelineAccount(
            id = serverId,
            localUsername = localUsername,
            username = username,
            displayName = displayName,
            note = "",
            url = url,
            avatar = avatar,
            bot = bot,
            emojis = gson.fromJson(emojis, emojisListType),
        )
    }

    companion object {
        fun from(timelineAccount: TimelineAccount, accountId: Long, gson: Gson) = TimelineAccountEntity(
            serverId = timelineAccount.id,
            timelineUserId = accountId,
            localUsername = timelineAccount.localUsername,
            username = timelineAccount.username,
            displayName = timelineAccount.name,
            url = timelineAccount.url,
            avatar = timelineAccount.avatar,
            emojis = gson.toJson(timelineAccount.emojis),
            bot = timelineAccount.bot,
        )
    }
}

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
