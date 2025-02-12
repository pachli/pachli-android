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
import app.pachli.core.database.model.StatusEntity
import app.pachli.core.database.model.StatusViewDataEntity
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.database.model.TimelineStatusWithAccount
import app.pachli.core.database.model.TranslationState
import app.pachli.core.network.model.Status
import app.pachli.core.network.model.TimelineAccount
import java.util.Date

private val fixedDate = Date(1638889052000)

fun fakeStatus(
    id: String = "100",
    inReplyToId: String? = null,
    inReplyToAccountId: String? = null,
    spoilerText: String = "",
    reblogged: Boolean = false,
    favourited: Boolean = true,
    bookmarked: Boolean = true,
) = Status(
    id = id,
    url = "https://mastodon.example/@ConnyDuck/$id",
    account = TimelineAccount(
        id = "1",
        localUsername = "connyduck",
        username = "connyduck@mastodon.example",
        displayName = "Conny Duck",
        note = "This is their bio",
        url = "https://mastodon.example/@ConnyDuck",
        avatar = "https://mastodon.example/system/accounts/avatars/000/150/486/original/ab27d7ddd18a10ea.jpg",
        createdAt = null,
    ),
    inReplyToId = inReplyToId,
    inReplyToAccountId = inReplyToAccountId,
    reblog = null,
    content = "Test",
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
    attachments = ArrayList(),
    mentions = emptyList(),
    tags = emptyList(),
    application = Status.Application("Pachli", "https://pachli.app"),
    pinned = false,
    muted = false,
    poll = null,
    card = null,
    language = null,
    filtered = null,
)

fun fakeStatusViewData(
    id: String = "100",
    inReplyToId: String? = null,
    inReplyToAccountId: String? = null,
    isDetailed: Boolean = false,
    spoilerText: String = "",
    isExpanded: Boolean = false,
    isShowingContent: Boolean = false,
    isCollapsed: Boolean = !isDetailed,
    reblogged: Boolean = false,
    favourited: Boolean = true,
    bookmarked: Boolean = true,
) = StatusViewData(
    pachliAccountId = 1L,
    status = fakeStatus(
        id = id,
        inReplyToId = inReplyToId,
        inReplyToAccountId = inReplyToAccountId,
        spoilerText = spoilerText,
        reblogged = reblogged,
        favourited = favourited,
        bookmarked = bookmarked,
    ),
    isExpanded = isExpanded,
    isShowingContent = isShowingContent,
    isCollapsed = isCollapsed,
    isDetailed = isDetailed,
    translationState = TranslationState.SHOW_ORIGINAL,
)

fun fakeStatusEntityWithAccount(
    id: String = "100",
    userId: Long = 1,
    expanded: Boolean = false,
): TimelineStatusWithAccount {
    val status = fakeStatus(id)

    return TimelineStatusWithAccount(
        status = StatusEntity.from(
            status,
            timelineUserId = userId,
        ),
        account = TimelineAccountEntity.from(
            status.account,
            accountId = userId,
        ),
        viewData = StatusViewDataEntity(
            serverId = id,
            pachliAccountId = userId,
            expanded = expanded,
            contentShowing = false,
            contentCollapsed = true,
            translationState = TranslationState.SHOW_ORIGINAL,
        ),
    )
}
