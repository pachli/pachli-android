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

package app.pachli.components.timeline

import app.pachli.db.TimelineAccountEntity
import app.pachli.db.TimelineStatusEntity
import app.pachli.db.TimelineStatusWithAccount
import app.pachli.entity.Attachment
import app.pachli.entity.Card
import app.pachli.entity.Emoji
import app.pachli.entity.HashTag
import app.pachli.entity.Poll
import app.pachli.entity.Status
import app.pachli.entity.TimelineAccount
import app.pachli.viewdata.StatusViewData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

private const val TAG = "TimelineTypeMappers"

private val attachmentArrayListType = object : TypeToken<ArrayList<Attachment>>() {}.type
private val emojisListType = object : TypeToken<List<Emoji>>() {}.type
private val mentionListType = object : TypeToken<List<Status.Mention>>() {}.type
private val tagListType = object : TypeToken<List<HashTag>>() {}.type

fun TimelineAccount.toEntity(accountId: Long, gson: Gson): TimelineAccountEntity {
    return TimelineAccountEntity(
        serverId = id,
        timelineUserId = accountId,
        localUsername = localUsername,
        username = username,
        displayName = name,
        url = url,
        avatar = avatar,
        emojis = gson.toJson(emojis),
        bot = bot,
    )
}

fun TimelineAccountEntity.toAccount(gson: Gson): TimelineAccount {
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

fun Status.toEntity(
    timelineUserId: Long,
    gson: Gson,
): TimelineStatusEntity {
    return TimelineStatusEntity(
        serverId = this.id,
        url = actionableStatus.url,
        timelineUserId = timelineUserId,
        authorServerId = actionableStatus.account.id,
        inReplyToId = actionableStatus.inReplyToId,
        inReplyToAccountId = actionableStatus.inReplyToAccountId,
        content = actionableStatus.content,
        createdAt = actionableStatus.createdAt.time,
        editedAt = actionableStatus.editedAt?.time,
        emojis = actionableStatus.emojis.let(gson::toJson),
        reblogsCount = actionableStatus.reblogsCount,
        favouritesCount = actionableStatus.favouritesCount,
        reblogged = actionableStatus.reblogged,
        favourited = actionableStatus.favourited,
        bookmarked = actionableStatus.bookmarked,
        sensitive = actionableStatus.sensitive,
        spoilerText = actionableStatus.spoilerText,
        visibility = actionableStatus.visibility,
        attachments = actionableStatus.attachments.let(gson::toJson),
        mentions = actionableStatus.mentions.let(gson::toJson),
        tags = actionableStatus.tags.let(gson::toJson),
        application = actionableStatus.application.let(gson::toJson),
        reblogServerId = reblog?.id,
        reblogAccountId = reblog?.let { this.account.id },
        poll = actionableStatus.poll.let(gson::toJson),
        muted = actionableStatus.muted,
        pinned = actionableStatus.pinned == true,
        card = actionableStatus.card?.let(gson::toJson),
        repliesCount = actionableStatus.repliesCount,
        language = actionableStatus.language,
        filtered = actionableStatus.filtered,
    )
}

fun TimelineStatusWithAccount.toViewData(gson: Gson, alwaysOpenSpoiler: Boolean, alwaysShowSensitiveMedia: Boolean, isDetailed: Boolean = false): StatusViewData {
    val attachments: ArrayList<Attachment> = gson.fromJson(
        status.attachments,
        attachmentArrayListType,
    ) ?: arrayListOf()
    val mentions: List<Status.Mention> = gson.fromJson(
        status.mentions,
        mentionListType,
    ) ?: emptyList()
    val tags: List<HashTag>? = gson.fromJson(
        status.tags,
        tagListType,
    )
    val application = gson.fromJson(status.application, Status.Application::class.java)
    val emojis: List<Emoji> = gson.fromJson(
        status.emojis,
        emojisListType,
    ) ?: emptyList()
    val poll: Poll? = gson.fromJson(status.poll, Poll::class.java)
    val card: Card? = gson.fromJson(status.card, Card::class.java)

    val reblog = status.reblogServerId?.let { id ->
        Status(
            id = id,
            url = status.url,
            account = account.toAccount(gson),
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
    val status = if (reblog != null) {
        Status(
            id = status.serverId,
            url = null, // no url for reblogs
            account = this.reblogAccount!!.toAccount(gson),
            inReplyToId = null,
            inReplyToAccountId = null,
            reblog = reblog,
            content = "",
            createdAt = Date(status.createdAt), // lie but whatever?
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
            attachments = ArrayList(),
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
            account = account.toAccount(gson),
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

    return StatusViewData(
        status = status,
        isExpanded = this.viewData?.expanded ?: alwaysOpenSpoiler,
        isShowingContent = this.viewData?.contentShowing ?: (alwaysShowSensitiveMedia || !status.actionableStatus.sensitive),
        isCollapsed = this.viewData?.contentCollapsed ?: true,
        isDetailed = isDetailed,
    )
}
