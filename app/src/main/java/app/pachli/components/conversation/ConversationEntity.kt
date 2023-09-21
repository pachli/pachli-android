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

package app.pachli.components.conversation

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.TypeConverters
import app.pachli.db.Converters
import app.pachli.entity.Attachment
import app.pachli.entity.Conversation
import app.pachli.entity.Emoji
import app.pachli.entity.HashTag
import app.pachli.entity.Poll
import app.pachli.entity.Status
import app.pachli.entity.TimelineAccount
import app.pachli.viewdata.StatusViewData
import java.util.Date

@Entity(primaryKeys = ["id", "accountId"])
@TypeConverters(Converters::class)
data class ConversationEntity(
    val accountId: Long,
    val id: String,
    val order: Int,
    val accounts: List<ConversationAccountEntity>,
    val unread: Boolean,
    @Embedded(prefix = "s_") val lastStatus: ConversationStatusEntity,
)

data class ConversationAccountEntity(
    val id: String,
    val localUsername: String,
    val username: String,
    val displayName: String,
    val avatar: String,
    val emojis: List<Emoji>,
) {
    fun toAccount(): TimelineAccount {
        return TimelineAccount(
            id = id,
            localUsername = localUsername,
            username = username,
            displayName = displayName,
            note = "",
            url = "",
            avatar = avatar,
            emojis = emojis,
        )
    }

    companion object {
        fun from(timelineAccount: TimelineAccount) = ConversationAccountEntity(
            id = timelineAccount.id,
            localUsername = timelineAccount.localUsername,
            username = timelineAccount.username,
            displayName = timelineAccount.name,
            avatar = timelineAccount.avatar,
            emojis = timelineAccount.emojis.orEmpty(),
        )
    }
}

@TypeConverters(Converters::class)
data class ConversationStatusEntity(
    val id: String,
    val url: String?,
    val inReplyToId: String?,
    val inReplyToAccountId: String?,
    val account: ConversationAccountEntity,
    val content: String,
    val createdAt: Date,
    val editedAt: Date?,
    val emojis: List<Emoji>,
    val favouritesCount: Int,
    val repliesCount: Int,
    val favourited: Boolean,
    val bookmarked: Boolean,
    val sensitive: Boolean,
    val spoilerText: String,
    val attachments: List<Attachment>,
    val mentions: List<Status.Mention>,
    val tags: List<HashTag>?,
    val showingHiddenContent: Boolean,
    val expanded: Boolean,
    val collapsed: Boolean,
    val muted: Boolean,
    val poll: Poll?,
    val language: String?,
) {

    fun toViewData(): StatusViewData {
        return StatusViewData(
            status = Status(
                id = id,
                url = url,
                account = account.toAccount(),
                inReplyToId = inReplyToId,
                inReplyToAccountId = inReplyToAccountId,
                content = content,
                reblog = null,
                createdAt = createdAt,
                editedAt = editedAt,
                emojis = emojis,
                reblogsCount = 0,
                favouritesCount = favouritesCount,
                repliesCount = repliesCount,
                reblogged = false,
                favourited = favourited,
                bookmarked = bookmarked,
                sensitive = sensitive,
                spoilerText = spoilerText,
                visibility = Status.Visibility.DIRECT,
                attachments = attachments,
                mentions = mentions,
                tags = tags,
                application = null,
                pinned = false,
                muted = muted,
                poll = poll,
                card = null,
                language = language,
                filtered = null,
            ),
            isExpanded = expanded,
            isShowingContent = showingHiddenContent,
            isCollapsed = collapsed,
        )
    }

    companion object {
        fun from(
            status: Status,
            expanded: Boolean,
            contentShowing: Boolean,
            contentCollapsed: Boolean,
        ) = ConversationStatusEntity(
            id = status.id,
            url = status.url,
            inReplyToId = status.inReplyToId,
            inReplyToAccountId = status.inReplyToAccountId,
            account = ConversationAccountEntity.from(status.account),
            content = status.content,
            createdAt = status.createdAt,
            editedAt = status.editedAt,
            emojis = status.emojis,
            favouritesCount = status.favouritesCount,
            repliesCount = status.repliesCount,
            favourited = status.favourited,
            bookmarked = status.bookmarked,
            sensitive = status.sensitive,
            spoilerText = status.spoilerText,
            attachments = status.attachments,
            mentions = status.mentions,
            tags = status.tags,
            showingHiddenContent = contentShowing,
            expanded = expanded,
            collapsed = contentCollapsed,
            muted = status.muted ?: false,
            poll = status.poll,
            language = status.language,
        )
    }
}

fun Conversation.toConversationEntity(
    accountId: Long,
    order: Int,
    expanded: Boolean,
    contentShowing: Boolean,
    contentCollapsed: Boolean,
) =
    ConversationEntity(
        accountId = accountId,
        id = id,
        order = order,
        accounts = accounts.map { ConversationAccountEntity.from(it) },
        unread = unread,
        lastStatus = ConversationStatusEntity.from(
            lastStatus!!,
            expanded = expanded,
            contentShowing = contentShowing,
            contentCollapsed = contentCollapsed,
        ),
    )
