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

import androidx.room3.ColumnInfo
import androidx.room3.ColumnTypeConverters
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Ignore
import androidx.room3.Index
import app.pachli.core.database.Converters
import app.pachli.core.database.dao.TimelineStatusWithAccount
import app.pachli.core.model.Attachment
import app.pachli.core.model.Card
import app.pachli.core.model.Collection
import app.pachli.core.model.Emoji
import app.pachli.core.model.FilterResult
import app.pachli.core.model.Hashtag
import app.pachli.core.model.Poll
import app.pachli.core.model.Status
import java.util.Date

/**
 * We're trying to play smart here. Server sends us reblogs as two entities one embedded into
 * another (reblogged status is a field inside of "reblog" status). But it's really inefficient from
 * the DB perspective and doesn't matter much for the display/interaction purposes.
 * What if when we store reblog we don't store almost empty "reblog status" but we store
 * *reblogged* status and we embed "reblog status" into reblogged status. This reversed
 * relationship takes much less space and is much faster to fetch (no N+1 type queries or JSON
 * serialization).
 * "Reblog status", if present, is marked by [reblogStatusId], and [reblogAccountId]
 * fields.
 *
 * @property statusId Status ID (see [reblogStatusId])
 * @property reblogStatusId If this is a reblog, the ID of the status being reblogged (*not
 * the ID of the reblog status*, that is still [statusId]). Also referred to as the
 * *actionable* ID.
 * @property reblogAccountId If this is a reblog, the ID of the account doing the reblogging.
 * @property reblogged True if [pachliAccountId] reblogged this status.
 * @property isReblog True if this status is a reblog of another status (see
 * [reblogStatusId] and [reblogAccountId])
 * @property isReply True if this status is a reply to another status (see
 * [inReplyToId] and [inReplyToAccountId])
 */
@Entity(
    primaryKeys = ["pachliAccountId", "statusId"],
    foreignKeys = (
        [
            ForeignKey(
                entity = PachliAccountEntity::class,
                parentColumns = ["pachliAccountId"],
                childColumns = ["pachliAccountId"],
                onDelete = ForeignKey.CASCADE,
                deferred = true,
            ),
            ForeignKey(
                entity = TimelineAccountEntity::class,
                parentColumns = ["pachliAccountId", "accountId"],
                childColumns = ["pachliAccountId", "accountId"],
                deferred = true,
            ),
        ]
        ),
    // Avoiding rescanning status table when accounts table changes. Recommended by Room(c).
    indices = [
        Index("pachliAccountId", "accountId"),
        Index("pachliAccountId"),
    ],
)
@ColumnTypeConverters(Converters::class)
data class StatusEntity(
    val pachliAccountId: Long,
    val statusId: String,
    val url: String?,
    val accountId: String,
    val inReplyToId: String?,
    val inReplyToAccountId: String?,
    val content: String?,
    val createdAt: Long,
    val editedAt: Long?,
    val emojis: List<Emoji>,
    val reblogsCount: Int,
    val favouritesCount: Int,
    val repliesCount: Int,
    @ColumnInfo(defaultValue = "0")
    val quotesCount: Int,
    val reblogged: Boolean,
    val bookmarked: Boolean,
    val favourited: Boolean,
    val sensitive: Boolean,
    val spoilerText: String,
    val visibility: Status.Visibility,
    val attachments: List<Attachment>?,
    val mentions: List<Status.Mention>?,
    val tags: List<Hashtag>,
    val application: Status.Application?,
    // if it has a reblogged status, it's id is stored here
    val reblogStatusId: String?,
    val reblogAccountId: String?,
    val poll: Poll?,
    val muted: Boolean,
    val pinned: Boolean,
    val card: Card?,
    val quoteState: Status.QuoteState?,
    val quoteStatusId: String?,
    @ColumnInfo(defaultValue = "{\"automatic\":[], \"manual\":[], \"currentUser\":\"UNKNOWN\"}")
    val quoteApproval: Status.QuoteApproval,
    val language: String?,
    val filtered: List<FilterResult>,
    @ColumnInfo(defaultValue = "[]")
    val taggedCollections: List<Collection>,
) {
    @Ignore
    val actionableId = reblogStatusId ?: statusId

    @Ignore
    val isReblog = reblogStatusId != null

    @Ignore
    val isReply = inReplyToId != null
}

fun Status.asEntity(pachliAccountId: Long) = StatusEntity(
    pachliAccountId = pachliAccountId,
    statusId = statusId,
    url = actionableStatus.url,
    accountId = actionableStatus.account.accountId,
    inReplyToId = actionableStatus.inReplyToId,
    inReplyToAccountId = actionableStatus.inReplyToAccountId,
    content = actionableStatus.content,
    createdAt = actionableStatus.createdAt.time,
    editedAt = actionableStatus.editedAt?.time,
    emojis = actionableStatus.emojis,
    reblogsCount = actionableStatus.reblogsCount,
    favouritesCount = actionableStatus.favouritesCount,
    quotesCount = actionableStatus.quotesCount,
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
    reblogStatusId = reblog?.statusId,
    reblogAccountId = reblog?.let { account.accountId },
    poll = actionableStatus.poll,
    muted = actionableStatus.muted,
    pinned = actionableStatus.pinned,
    card = actionableStatus.card,
    quoteState = actionableStatus.quote?.state,
    quoteStatusId = when (actionableStatus.quote) {
        is Status.Quote.FullQuote -> (actionableStatus.quote as Status.Quote.FullQuote).statusId
        is Status.Quote.ShallowQuote -> (actionableStatus.quote as Status.Quote.ShallowQuote).statusId
        is Status.Quote.HiddenQuote -> null
        null -> null
    },
    quoteApproval = actionableStatus.quoteApproval,
    repliesCount = actionableStatus.repliesCount,
    language = actionableStatus.language,
    filtered = actionableStatus.filtered,
    taggedCollections = actionableStatus.taggedCollections,
)

@JvmName("IterableStatus")
fun Iterable<Status>.asEntity(pachliAccountId: Long) = map { it.asEntity(pachliAccountId) }

/**
 * M:N association between [StatusEntity] and [TimelineCollectionEntity].
 */
@Entity(
    primaryKeys = ["pachliAccountId", "statusId", "collectionId"],
    foreignKeys = (
        [
            ForeignKey(
                entity = PachliAccountEntity::class,
                parentColumns = ["pachliAccountId"],
                childColumns = ["pachliAccountId"],
                onDelete = ForeignKey.CASCADE,
                deferred = true,
            ),
            ForeignKey(
                entity = StatusEntity::class,
                parentColumns = ["pachliAccountId", "statusId"],
                childColumns = ["pachliAccountId", "statusId"],
                onDelete = ForeignKey.CASCADE,
                deferred = true,
            ),
            ForeignKey(
                entity = TimelineCollectionEntity::class,
                parentColumns = ["pachliAccountId", "collectionId"],
                childColumns = ["pachliAccountId", "collectionId"],
                onDelete = ForeignKey.CASCADE,
                deferred = true,
            ),
        ]
        ),
)
data class StatusToTimelineCollectionEntity(
    val pachliAccountId: Long,
    val statusId: String,
    val collectionId: String,
)

/**
 * A complete [TimelineStatusWithAccount], and the (optional) status it quotes.
 *
 * @property timelineStatus The [TimelineStatusWithAccount].
 * @property quotedStatus The quoted status, if present. Null if [timelineStatus]
 * does not quote a post.
 */
data class TimelineStatusWithQuote(
    @Embedded(prefix = "s_")
    val timelineStatus: TimelineStatusWithAccount,
    @Embedded(prefix = "q_")
    val quotedStatus: TimelineStatusWithAccount? = null,
) {
    fun toStatus(): Status {
        val status = timelineStatus.status
        val account = timelineStatus.account
        val reblogAccount = timelineStatus.reblogAccount

        val attachments: List<Attachment> = status.attachments.orEmpty()
        val mentions: List<Status.Mention> = status.mentions.orEmpty()
        val tags: List<Hashtag> = status.tags
        val application = status.application
        val emojis: List<Emoji> = status.emojis
        val poll: Poll? = status.poll
        val card: Card? = status.card

        /**
         * If [this] is a reblog, this is the status being reblogged.
         */
        val reblog = status.reblogStatusId?.let { id ->
            Status(
                statusId = id,
                url = status.url,
                account = account.asModel(),
                inReplyToId = status.inReplyToId,
                inReplyToAccountId = status.inReplyToAccountId,
                reblog = null,
                content = status.content.orEmpty(),
                createdAt = Date(status.createdAt),
                editedAt = status.editedAt?.let { Date(it) },
                emojis = emojis,
                reblogsCount = status.reblogsCount,
                favouritesCount = status.favouritesCount,
                quotesCount = status.quotesCount,
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
                quote = quotedStatus?.let {
                    Status.Quote.FullQuote(
                        state = status.quoteState!!,
                        status = quotedStatus.toStatus(),
                    )
                },
                quoteApproval = status.quoteApproval,
                repliesCount = status.repliesCount,
                language = status.language,
                filtered = status.filtered,
                taggedCollections = status.taggedCollections,
            )
        }
        return if (reblog != null) {
            val status = timelineStatus.status

            Status(
                statusId = status.statusId,
                // no url for reblogs
                url = null,
                account = reblogAccount!!.asModel(),
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
                quotesCount = 0,
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
                quote = quotedStatus?.let {
                    Status.Quote.FullQuote(
                        state = status.quoteState!!,
                        status = quotedStatus.toStatus(),
                    )
                },
                quoteApproval = Status.QuoteApproval(),
                repliesCount = status.repliesCount,
                language = status.language,
                filtered = status.filtered,
                taggedCollections = status.taggedCollections,
            )
        } else {
            Status(
                statusId = status.statusId,
                url = status.url,
                account = account.asModel(),
                inReplyToId = status.inReplyToId,
                inReplyToAccountId = status.inReplyToAccountId,
                reblog = null,
                content = status.content.orEmpty(),
                createdAt = Date(status.createdAt),
                editedAt = status.editedAt?.let { Date(it) },
                emojis = emojis,
                reblogsCount = status.reblogsCount,
                favouritesCount = status.favouritesCount,
                quotesCount = 0,
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
                quote = quotedStatus?.let {
                    Status.Quote.FullQuote(
                        state = status.quoteState!!,
                        status = quotedStatus.toStatus(),
                    )
                },
                quoteApproval = Status.QuoteApproval(),
                repliesCount = status.repliesCount,
                language = status.language,
                filtered = status.filtered,
                taggedCollections = status.taggedCollections,
            )
        }
    }
}
