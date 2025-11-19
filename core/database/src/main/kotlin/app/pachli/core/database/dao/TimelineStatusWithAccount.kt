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

package app.pachli.core.database.dao

import androidx.room.DatabaseView
import androidx.room.Embedded
import app.pachli.core.database.model.StatusEntity
import app.pachli.core.database.model.StatusViewDataEntity
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.database.model.TranslatedStatusEntity
import app.pachli.core.model.Attachment
import app.pachli.core.model.Card
import app.pachli.core.model.Emoji
import app.pachli.core.model.HashTag
import app.pachli.core.model.Poll
import app.pachli.core.model.Status
import java.util.Date

@DatabaseView(
    """
SELECT
    s.serverId,
    s.url,
    s.timelineUserId,
    s.authorServerId,
    s.inReplyToId,
    s.inReplyToAccountId,
    s.createdAt,
    s.editedAt,
    s.emojis,
    s.reblogsCount,
    s.favouritesCount,
    s.repliesCount,
    s.quotesCount,
    s.reblogged,
    s.favourited,
    s.bookmarked,
    s.sensitive,
    s.spoilerText,
    s.visibility,
    s.mentions,
    s.tags,
    s.application,
    s.reblogServerId,
    s.reblogAccountId,
    s.content,
    s.attachments,
    s.poll,
    s.card,
    s.muted,
    s.pinned,
    s.language,
    s.filtered,
    s.quoteState,
    s.quoteServerId,
    s.quoteApproval,
    a.serverId AS 'a_serverId',
    a.timelineUserId AS 'a_timelineUserId',
    a.localUsername AS 'a_localUsername',
    a.username AS 'a_username',
    a.displayName AS 'a_displayName',
    a.url AS 'a_url',
    a.avatar AS 'a_avatar',
    a.emojis AS 'a_emojis',
    a.bot AS 'a_bot',
    a.createdAt AS 'a_createdAt',
    a.limited AS 'a_limited',
    a.note AS 'a_note',
    a.roles AS 'a_roles',
    a.pronouns AS 'a_pronouns',
    rb.serverId AS 'rb_serverId',
    rb.timelineUserId AS 'rb_timelineUserId',
    rb.localUsername AS 'rb_localUsername',
    rb.username AS 'rb_username',
    rb.displayName AS 'rb_displayName',
    rb.url AS 'rb_url',
    rb.avatar AS 'rb_avatar',
    rb.emojis AS 'rb_emojis',
    rb.bot AS 'rb_bot',
    rb.createdAt AS 'rb_createdAt',
    rb.limited AS 'rb_limited',
    rb.note AS 'rb_note',
    rb.roles AS 'rb_roles',
    rb.pronouns AS 'rb_pronouns',
    svd.serverId AS 'svd_serverId',
    svd.pachliAccountId AS 'svd_pachliAccountId',
    svd.expanded AS 'svd_expanded',
    svd.contentCollapsed AS 'svd_contentCollapsed',
    svd.translationState AS 'svd_translationState',
    svd.attachmentDisplayAction AS 'svd_attachmentDisplayAction',
    tr.serverId AS 't_serverId',
    tr.timelineUserId AS 't_timelineUserId',
    tr.content AS 't_content',
    tr.spoilerText AS 't_spoilerText',
    tr.poll AS 't_poll',
    tr.attachments AS 't_attachments',
    tr.provider AS 't_provider',
    reply.serverId AS 'reply_serverId',
    reply.timelineUserId AS 'reply_timelineUserId',
    reply.localUsername AS 'reply_localUsername',
    reply.username AS 'reply_username',
    reply.displayName AS 'reply_displayName',
    reply.url AS 'reply_url',
    reply.avatar AS 'reply_avatar',
    reply.emojis AS 'reply_emojis',
    reply.bot AS 'reply_bot',
    reply.createdAt AS 'reply_createdAt',
    reply.limited AS 'reply_limited',
    reply.note AS 'reply_note',
    reply.roles AS 'reply_roles',
    reply.pronouns AS 'reply_pronouns'
FROM StatusEntity AS s
LEFT JOIN TimelineAccountEntity AS a ON (s.timelineUserId = a.timelineUserId AND s.authorServerId = a.serverId)
LEFT JOIN TimelineAccountEntity AS rb ON (s.timelineUserId = rb.timelineUserId AND s.reblogAccountId = rb.serverId)
LEFT JOIN
    StatusViewDataEntity AS svd
    ON (s.timelineUserId = svd.pachliAccountId AND (s.serverId = svd.serverId OR s.reblogServerId = svd.serverId))
LEFT JOIN
    TranslatedStatusEntity AS tr
    ON (s.timelineUserId = tr.timelineUserId AND (s.serverId = tr.serverId OR s.reblogServerId = tr.serverId))
LEFT JOIN TimelineAccountEntity AS reply ON (s.timelineUserId = reply.timelineUserId AND s.inReplyToAccountId = reply.serverId)
""",
)
data class TimelineStatusWithAccount(
    @Embedded
    val status: StatusEntity,
    @Embedded(prefix = "a_")
    val account: TimelineAccountEntity,
    @Embedded(prefix = "rb_")
    val reblogAccount: TimelineAccountEntity? = null,
    @Embedded(prefix = "svd_")
    val viewData: StatusViewDataEntity? = null,
    @Embedded(prefix = "t_")
    val translatedStatus: TranslatedStatusEntity? = null,
    @Embedded(prefix = "reply_")
    val replyAccount: TimelineAccountEntity? = null,
) {
    /**
     * Returns a [app.pachli.core.model.Status] from [this].
     *
     * Any embedded quotes are returned as a [app.pachli.core.model.Status.Quote.ShallowQuote]. Use
     * [app.pachli.core.database.model.TimelineStatusWithQuote] to retain quotes.
     */
    fun toStatus(): Status {
        val attachments: List<Attachment> = status.attachments.orEmpty()
        val mentions: List<Status.Mention> = status.mentions.orEmpty()
        val tags: List<HashTag>? = status.tags
        val application = status.application
        val emojis: List<Emoji> = status.emojis.orEmpty()
        val poll: Poll? = status.poll
        val card: Card? = status.card

        val reblog = status.reblogServerId?.let { actionableId ->
            Status(
                statusId = actionableId,
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
                quote = status.quoteState?.let { quoteState ->
                    status.quoteServerId?.let { quoteServerId ->
                        Status.Quote.ShallowQuote(quoteState, quoteServerId)
                    }
                },
                quoteApproval = status.quoteApproval,
                repliesCount = status.repliesCount,
                language = status.language,
                filtered = status.filtered,
            )
        }
        return if (reblog != null) {
            Status(
                statusId = status.serverId,
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
                quote = status.quoteState?.let { quoteState ->
                    status.quoteServerId?.let { quoteServerId ->
                        Status.Quote.ShallowQuote(quoteState, quoteServerId)
                    }
                },
                quoteApproval = status.quoteApproval,
                repliesCount = status.repliesCount,
                language = status.language,
                filtered = status.filtered,
            )
        } else {
            Status(
                statusId = status.serverId,
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
                quote = status.quoteState?.let { quoteState ->
                    status.quoteServerId?.let { quoteServerId ->
                        Status.Quote.ShallowQuote(quoteState, quoteServerId)
                    }
                },
                quoteApproval = status.quoteApproval,
                repliesCount = status.repliesCount,
                language = status.language,
                filtered = status.filtered,
            )
        }
    }
}
