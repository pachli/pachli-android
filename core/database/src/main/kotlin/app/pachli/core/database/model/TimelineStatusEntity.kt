/*
 * Copyright 2021 Tusky Contributors
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

package app.pachli.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverters
import app.pachli.core.database.Converters
import app.pachli.core.network.model.Attachment
import app.pachli.core.network.model.Card
import app.pachli.core.network.model.Emoji
import app.pachli.core.network.model.FilterResult
import app.pachli.core.network.model.HashTag
import app.pachli.core.network.model.Poll
import app.pachli.core.network.model.Status
import app.pachli.core.network.model.TimelineAccount
import java.time.Instant
import java.util.Date

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
                deferred = true,
            ),
        ]
        ),
    // Avoiding rescanning status table when accounts table changes. Recommended by Room(c).
    indices = [Index("authorServerId", "timelineUserId")],
)
@TypeConverters(Converters::class)
data class TimelineStatusEntity(
    // id never flips: we need it for sorting so it's a real id
    val serverId: String,
    val url: String?,
    // our local id for the logged in user in case there are multiple accounts per instance
    val timelineUserId: Long,
    val authorServerId: String,
    val inReplyToId: String?,
    val inReplyToAccountId: String?,
    val content: String?,
    val createdAt: Long,
    val editedAt: Long?,
    val emojis: List<Emoji>?,
    val reblogsCount: Int,
    val favouritesCount: Int,
    val repliesCount: Int,
    val reblogged: Boolean,
    val bookmarked: Boolean,
    val favourited: Boolean,
    val sensitive: Boolean,
    val spoilerText: String,
    val visibility: Status.Visibility,
    val attachments: List<Attachment>?,
    val mentions: List<Status.Mention>?,
    val tags: List<HashTag>?,
    val application: Status.Application?,
    // if it has a reblogged status, it's id is stored here
    val reblogServerId: String?,
    val reblogAccountId: String?,
    val poll: Poll?,
    val muted: Boolean?,
    val pinned: Boolean,
    val card: Card?,
    val language: String?,
    val filtered: List<FilterResult>?,
) {
    companion object {
        fun from(status: Status, timelineUserId: Long) = TimelineStatusEntity(
            serverId = status.id,
            url = status.actionableStatus.url,
            timelineUserId = timelineUserId,
            authorServerId = status.actionableStatus.account.id,
            inReplyToId = status.actionableStatus.inReplyToId,
            inReplyToAccountId = status.actionableStatus.inReplyToAccountId,
            content = status.actionableStatus.content,
            createdAt = status.actionableStatus.createdAt.time,
            editedAt = status.actionableStatus.editedAt?.time,
            emojis = status.actionableStatus.emojis,
            reblogsCount = status.actionableStatus.reblogsCount,
            favouritesCount = status.actionableStatus.favouritesCount,
            reblogged = status.actionableStatus.reblogged,
            favourited = status.actionableStatus.favourited,
            bookmarked = status.actionableStatus.bookmarked,
            sensitive = status.actionableStatus.sensitive,
            spoilerText = status.actionableStatus.spoilerText,
            visibility = status.actionableStatus.visibility,
            attachments = status.actionableStatus.attachments,
            mentions = status.actionableStatus.mentions,
            tags = status.actionableStatus.tags,
            application = status.actionableStatus.application,
            reblogServerId = status.reblog?.id,
            reblogAccountId = status.reblog?.let { status.account.id },
            poll = status.actionableStatus.poll,
            muted = status.actionableStatus.muted,
            pinned = status.actionableStatus.pinned == true,
            card = status.actionableStatus.card,
            repliesCount = status.actionableStatus.repliesCount,
            language = status.actionableStatus.language,
            filtered = status.actionableStatus.filtered,
        )
    }
}

/**
 * An account associated with a status on a timeline or similar (e.g., an
 * account the user is following).
 *
 * @param serverId
 * @param timelineUserId The pachliAccountId for the logged-in account related
 * to this account.
 * @param localUsername
 * @param username
 * @param displayName
 * @param url
 * @param avatar
 * @param emojis
 * @param bot
 * @param createdAt
 */
@Entity(
    primaryKeys = ["serverId", "timelineUserId"],
)
@TypeConverters(Converters::class)
data class TimelineAccountEntity(
    val serverId: String,
    val timelineUserId: Long,
    val localUsername: String,
    val username: String,
    val displayName: String,
    val url: String,
    val avatar: String,
    val emojis: List<Emoji>,
    val bot: Boolean,
    val createdAt: Instant?,
) {
    fun toTimelineAccount(): TimelineAccount {
        return TimelineAccount(
            id = serverId,
            localUsername = localUsername,
            username = username,
            displayName = displayName,
            note = "",
            url = url,
            avatar = avatar,
            bot = bot,
            emojis = emojis,
            createdAt = createdAt,
        )
    }

    companion object {
        fun from(timelineAccount: TimelineAccount, accountId: Long) = TimelineAccountEntity(
            serverId = timelineAccount.id,
            timelineUserId = accountId,
            localUsername = timelineAccount.localUsername,
            username = timelineAccount.username,
            displayName = timelineAccount.name,
            url = timelineAccount.url,
            avatar = timelineAccount.avatar,
            emojis = timelineAccount.emojis.orEmpty(),
            bot = timelineAccount.bot,
            createdAt = timelineAccount.createdAt,
        )
    }
}

enum class TranslationState {
    /** Show the original, untranslated status */
    SHOW_ORIGINAL,

    /** Show the original, untranslated status, but translation is happening */
    TRANSLATING,

    /** Show the translated status */
    SHOW_TRANSLATION,
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
    /** Corresponds to [app.pachli.viewdata.IStatusViewData.isExpanded] */
    val expanded: Boolean,
    /** Corresponds to [app.pachli.viewdata.IStatusViewData.isShowingContent] */
    val contentShowing: Boolean,
    /** Corresponds to [app.pachli.viewdata.IStatusViewData.isCollapsed] */
    val contentCollapsed: Boolean,
    /** Show the translated version of the status (if it exists) */
    @ColumnInfo(defaultValue = "SHOW_ORIGINAL")
    val translationState: TranslationState,
)

data class TimelineStatusWithAccount(
    @Embedded
    val status: TimelineStatusEntity,
    @Embedded(prefix = "a_")
    val account: TimelineAccountEntity,
    // null when no reblog
    @Embedded(prefix = "rb_")
    val reblogAccount: TimelineAccountEntity? = null,
    @Embedded(prefix = "svd_")
    val viewData: StatusViewDataEntity? = null,
    @Embedded(prefix = "t_")
    val translatedStatus: TranslatedStatusEntity? = null,
) {
    fun toStatus(): Status {
        val attachments: List<Attachment> = status.attachments.orEmpty()
        val mentions: List<Status.Mention> = status.mentions.orEmpty()
        val tags: List<HashTag>? = status.tags
        val application = status.application
        val emojis: List<Emoji> = status.emojis.orEmpty()
        val poll: Poll? = status.poll
        val card: Card? = status.card

        val reblog = status.reblogServerId?.let { id ->
            Status(
                id = id,
                url = status.url,
                account = account.toTimelineAccount(),
                inReplyToId = status.inReplyToId,
                inReplyToAccountId = status.inReplyToAccountId,
                reblog = null,
                content = status.content.orEmpty(),
                createdAt = Date(status.createdAt),
                editedAt = status.editedAt?.let { Date(it) },
                emojis = emojis,
                reblogsCount = status.reblogsCount,
                favouritesCount = status.favouritesCount,
                reblogged = status.reblogged,
                favourited = status.favourited,
                bookmarked = status.bookmarked,
                sensitive = status.sensitive,
                spoilerText = status.spoilerText,
                visibility = status.visibility,
                attachments = attachments,
                mentions = mentions,
                tags = tags,
                application = application,
                pinned = false,
                muted = status.muted,
                poll = poll,
                card = card,
                repliesCount = status.repliesCount,
                language = status.language,
                filtered = status.filtered,
            )
        }
        return if (reblog != null) {
            Status(
                id = status.serverId,
                // no url for reblogs
                url = null,
                account = reblogAccount!!.toTimelineAccount(),
                inReplyToId = null,
                inReplyToAccountId = null,
                reblog = reblog,
                content = "",
                // lie but whatever?
                createdAt = Date(status.createdAt),
                editedAt = null,
                emojis = listOf(),
                reblogsCount = 0,
                favouritesCount = 0,
                reblogged = false,
                favourited = false,
                bookmarked = false,
                sensitive = false,
                spoilerText = "",
                visibility = status.visibility,
                attachments = listOf(),
                mentions = listOf(),
                tags = listOf(),
                application = null,
                pinned = status.pinned,
                muted = status.muted,
                poll = null,
                card = null,
                repliesCount = status.repliesCount,
                language = status.language,
                filtered = status.filtered,
            )
        } else {
            Status(
                id = status.serverId,
                url = status.url,
                account = account.toTimelineAccount(),
                inReplyToId = status.inReplyToId,
                inReplyToAccountId = status.inReplyToAccountId,
                reblog = null,
                content = status.content.orEmpty(),
                createdAt = Date(status.createdAt),
                editedAt = status.editedAt?.let { Date(it) },
                emojis = emojis,
                reblogsCount = status.reblogsCount,
                favouritesCount = status.favouritesCount,
                reblogged = status.reblogged,
                favourited = status.favourited,
                bookmarked = status.bookmarked,
                sensitive = status.sensitive,
                spoilerText = status.spoilerText,
                visibility = status.visibility,
                attachments = attachments,
                mentions = mentions,
                tags = tags,
                application = application,
                pinned = status.pinned,
                muted = status.muted,
                poll = poll,
                card = card,
                repliesCount = status.repliesCount,
                language = status.language,
                filtered = status.filtered,
            )
        }
    }
}
