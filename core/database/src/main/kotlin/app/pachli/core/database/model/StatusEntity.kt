/*
 * Copyright (c) 2025 Pachli Association
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
import app.pachli.core.model.Attachment
import app.pachli.core.model.Card
import app.pachli.core.model.Emoji
import app.pachli.core.model.FilterResult
import app.pachli.core.model.HashTag
import app.pachli.core.model.Poll
import app.pachli.core.model.Role
import app.pachli.core.model.Status
import app.pachli.core.model.TimelineAccount
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
                entity = AccountEntity::class,
                parentColumns = ["id"],
                childColumns = ["timelineUserId"],
                onDelete = ForeignKey.CASCADE,
                deferred = true,
            ),
            ForeignKey(
                entity = TimelineAccountEntity::class,
                parentColumns = ["serverId", "timelineUserId"],
                childColumns = ["authorServerId", "timelineUserId"],
                deferred = true,
            ),
        ]
        ),
    // Avoiding rescanning status table when accounts table changes. Recommended by Room(c).
    indices = [Index("authorServerId", "timelineUserId"), Index("timelineUserId")],
)
@TypeConverters(Converters::class)
data class StatusEntity(
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
        fun from(status: Status, timelineUserId: Long) = StatusEntity(
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

fun Status.asEntity(pachliAccountId: Long) = StatusEntity(
    serverId = id,
    url = actionableStatus.url,
    timelineUserId = pachliAccountId,
    authorServerId = actionableStatus.account.id,
    inReplyToId = actionableStatus.inReplyToId,
    inReplyToAccountId = actionableStatus.inReplyToAccountId,
    content = actionableStatus.content,
    createdAt = actionableStatus.createdAt.time,
    editedAt = actionableStatus.editedAt?.time,
    emojis = actionableStatus.emojis,
    reblogsCount = actionableStatus.reblogsCount,
    favouritesCount = actionableStatus.favouritesCount,
    reblogged = actionableStatus.reblogged,
    favourited = actionableStatus.favourited,
    bookmarked = actionableStatus.bookmarked,
    sensitive = actionableStatus.sensitive,
    spoilerText = actionableStatus.spoilerText,
    visibility = actionableStatus.visibility,
    attachments = actionableStatus.attachments,
    mentions = actionableStatus.mentions,
    tags = actionableStatus.tags,
    application = actionableStatus.application,
    reblogServerId = reblog?.id,
    reblogAccountId = reblog?.let { account.id },
    poll = actionableStatus.poll,
    muted = actionableStatus.muted,
    pinned = actionableStatus.pinned == true,
    card = actionableStatus.card,
    repliesCount = actionableStatus.repliesCount,
    language = actionableStatus.language,
    filtered = actionableStatus.filtered,
)

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
 * @param note
 */
@Entity(
    primaryKeys = ["serverId", "timelineUserId"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("timelineUserId"),
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index(value = ["timelineUserId"])],
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
    @ColumnInfo(defaultValue = "false")
    val limited: Boolean = false,
    @ColumnInfo(defaultValue = "")
    val note: String,
    @ColumnInfo(defaultValue = "")
    val roles: List<Role>?,
) {
    fun toTimelineAccount(): TimelineAccount {
        return TimelineAccount(
            id = serverId,
            localUsername = localUsername,
            username = username,
            displayName = displayName,
            note = note,
            url = url,
            avatar = avatar,
            bot = bot,
            emojis = emojis,
            createdAt = createdAt,
            limited = limited,
            roles = roles.orEmpty(),
        )
    }

    companion object {
        fun from(timelineAccount: TimelineAccount, accountId: Long) = TimelineAccountEntity(
            serverId = timelineAccount.id,
            timelineUserId = accountId,
            localUsername = timelineAccount.localUsername,
            username = timelineAccount.username,
            displayName = timelineAccount.name,
            note = timelineAccount.note,
            url = timelineAccount.url,
            avatar = timelineAccount.avatar,
            emojis = timelineAccount.emojis.orEmpty(),
            bot = timelineAccount.bot,
            createdAt = timelineAccount.createdAt,
            limited = timelineAccount.limited,
            roles = timelineAccount.roles,
        )
    }
}

fun TimelineAccount.asEntity(pachliAccountId: Long) = TimelineAccountEntity(
    serverId = id,
    timelineUserId = pachliAccountId,
    localUsername = localUsername,
    username = username,
    displayName = name,
    note = note,
    url = url,
    avatar = avatar,
    emojis = emojis.orEmpty(),
    bot = bot,
    createdAt = createdAt,
    limited = limited,
    roles = roles,
)

fun Iterable<TimelineAccount>.asEntity(pachliAccountId: Long) = map { it.asEntity(pachliAccountId) }

fun TimelineAccountEntity.asModel() = TimelineAccount(
    id = serverId,
    localUsername = localUsername,
    username = username,
    displayName = displayName,
    url = url,
    avatar = avatar,
    note = note,
    bot = bot,
    emojis = emojis,
    createdAt = createdAt,
    limited = limited,
    roles = roles.orEmpty(),
)

data class TimelineStatusWithAccount(
    @Embedded
    val status: StatusEntity,
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
