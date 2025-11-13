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

package app.pachli.core.testing.fakes

import app.pachli.core.data.model.StatusViewData
import app.pachli.core.data.model.StatusViewDataQ
import app.pachli.core.data.repository.notifications.asEntity
import app.pachli.core.database.dao.TimelineStatusWithAccount
import app.pachli.core.database.model.StatusViewDataEntity
import app.pachli.core.database.model.TimelineStatusWithQuote
import app.pachli.core.database.model.TranslationState
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.AttachmentDisplayReason
import app.pachli.core.network.model.Attachment
import app.pachli.core.network.model.Poll
import app.pachli.core.network.model.PollOption
import app.pachli.core.network.model.Status
import app.pachli.core.network.model.TimelineAccount
import java.util.Date

private val fixedDate = Date(1638889052000)

fun fakeAccount() = TimelineAccount(
    id = "1",
    localUsername = "connyduck",
    username = "connyduck@mastodon.example",
    displayName = "Conny Duck",
    note = "This is their bio",
    url = "https://mastodon.example/@ConnyDuck",
    avatar = "https://mastodon.example/system/accounts/avatars/000/150/486/original/ab27d7ddd18a10ea.jpg",
    createdAt = null,
)

fun fakeStatus(
    id: String = "100",
    inReplyToId: String? = null,
    inReplyToAccountId: String? = null,
    spoilerText: String = "",
    reblogged: Boolean = false,
    favourited: Boolean = true,
    bookmarked: Boolean = true,
    content: String = "Test",
    pollOptions: List<String>? = null,
    attachmentsDescriptions: List<String>? = null,
    makeFakeAccount: () -> TimelineAccount = ::fakeAccount,
) = Status(
    id = id,
    url = "https://mastodon.example/@ConnyDuck/$id",
    account = makeFakeAccount(),
    inReplyToId = inReplyToId,
    inReplyToAccountId = inReplyToAccountId,
    reblog = null,
    content = content,
    createdAt = fixedDate,
    editedAt = null,
    emojis = emptyList(),
    reblogsCount = 1,
    favouritesCount = 2,
    repliesCount = 3,
    reblogged = reblogged,
    favourited = favourited,
    bookmarked = bookmarked,
    sensitive = true,
    spoilerText = spoilerText,
    visibility = Status.Visibility.PUBLIC,
    attachments = attachmentsDescriptions?.let {
        it.mapIndexed { index, description ->
            Attachment(
                id = index.toString(),
                url = "",
                previewUrl = "",
                meta = null,
                type = Attachment.Type.IMAGE,
                description = description,
                blurhash = null,
            )
        }
    } ?: emptyList(),
    mentions = emptyList(),
    tags = emptyList(),
    application = Status.Application("Pachli", "https://pachli.app"),
    pinned = false,
    muted = false,
    poll = pollOptions?.let {
        Poll(
            id = "1234",
            expiresAt = null,
            expired = false,
            multiple = false,
            votesCount = 0,
            votersCount = 0,
            options = it.map {
                PollOption(it, 0)
            },
            voted = false,
            ownVotes = null,
        )
    },
    card = null,
    language = null,
    filtered = null,
    quotesCount = 0,
    quote = null,
    quoteApproval = Status.QuoteApproval(),
)

fun fakeStatusViewData(
    id: String = "100",
    inReplyToId: String? = null,
    inReplyToAccountId: String? = null,
    isDetailed: Boolean = false,
    spoilerText: String = "",
    isExpanded: Boolean = false,
    isShowingContent: Boolean = false,
    isCollapsed: Boolean = true,
    reblogged: Boolean = false,
    favourited: Boolean = true,
    bookmarked: Boolean = true,
) = StatusViewDataQ(
    statusViewData = StatusViewData(
        pachliAccountId = 1L,
        status = fakeStatus(
            id = id,
            inReplyToId = inReplyToId,
            inReplyToAccountId = inReplyToAccountId,
            spoilerText = spoilerText,
            reblogged = reblogged,
            favourited = favourited,
            bookmarked = bookmarked,
        ).asModel(),
        isExpanded = isExpanded,
        isCollapsed = isCollapsed,
        isDetailed = isDetailed,
        translationState = TranslationState.SHOW_ORIGINAL,
        attachmentDisplayAction = if (isShowingContent) {
            AttachmentDisplayAction.Show(originalAction = AttachmentDisplayAction.Hide(AttachmentDisplayReason.Sensitive))
        } else {
            AttachmentDisplayAction.Hide(reason = AttachmentDisplayReason.Sensitive)
        },
        replyToAccount = null,
    ),
)

fun fakeStatusEntityWithAccount(
    id: String = "100",
    pachliAccountId: Long = 1,
    expanded: Boolean = false,
    makeFakeStatus: () -> Status = { fakeStatus(id) },
): TimelineStatusWithQuote {
    val status = makeFakeStatus()

    return TimelineStatusWithQuote(
        timelineStatus = TimelineStatusWithAccount(
            status = status.asEntity(pachliAccountId),
            account = status.account.asEntity(pachliAccountId),
            viewData = StatusViewDataEntity(
                serverId = status.id,
                pachliAccountId = pachliAccountId,
                expanded = expanded,
                contentCollapsed = true,
                translationState = TranslationState.SHOW_ORIGINAL,
                attachmentDisplayAction = AttachmentDisplayAction.Show(),
            ),
        ),
    )
}
