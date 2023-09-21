/* Copyright 2017 Andrew Dawson
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
package app.pachli.viewdata

import android.os.Build
import android.text.Spanned
import app.pachli.components.conversation.ConversationAccountEntity
import app.pachli.components.conversation.ConversationStatusEntity
import app.pachli.db.TimelineStatusWithAccount
import app.pachli.entity.Attachment
import app.pachli.entity.Card
import app.pachli.entity.Emoji
import app.pachli.entity.Filter
import app.pachli.entity.HashTag
import app.pachli.entity.Poll
import app.pachli.entity.Status
import app.pachli.util.parseAsMastodonHtml
import app.pachli.util.replaceCrashingCharacters
import app.pachli.util.shouldTrimStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.Date

val attachmentArrayListType: Type = object : TypeToken<ArrayList<Attachment>>() {}.type
val emojisListType: Type = object : TypeToken<List<Emoji>>() {}.type
val mentionListType: Type = object : TypeToken<List<Status.Mention>>() {}.type
val tagListType: Type = object : TypeToken<List<HashTag>>() {}.type

/**
 * Data required to display a status.
 */
data class StatusViewData(
    var status: Status,
    /**
     * If the status includes a non-empty content warning ([spoilerText]), specifies whether
     * just the content warning is showing (false), or the whole status content is showing (true).
     *
     * Ignored if there is no content warning.
     */
    val isExpanded: Boolean,
    /**
     * If the status contains attached media, specifies whether whether the media is shown
     * (true), or not (false).
     */
    val isShowingContent: Boolean,

    /**
     * Specifies whether the content of this status is currently limited in visibility to the first
     * 500 characters or not.
     *
     * @return Whether the status is collapsed or fully expanded.
     */
    val isCollapsed: Boolean,

    /**
     * Specifies whether this status should be shown with the "detailed" layout, meaning it is
     * the status that has a focus when viewing a thread.
     */
    val isDetailed: Boolean = false,

    /** Whether this status should be filtered, and if so, how */
    // TODO: This means that null checks are required elsewhere in the code to deal with
    // the possibility that this might not be NONE, but that status.filtered is null or
    // empty (e.g., StatusBaseViewHolder.setupFilterPlaceholder()). It would be better
    // if the Filter.Action class subtypes carried the FilterResult information with them,
    // and it's impossible to construct them with an empty list.
    var filterAction: Filter.Action = Filter.Action.NONE,
) {
    val id: String
        get() = status.id

    /**
     * Specifies whether the content of this status is long enough to be automatically
     * collapsed or if it should show all content regardless.
     *
     * @return Whether the status is collapsible or never collapsed.
     */
    val isCollapsible: Boolean

    val content: Spanned

    /** The content warning, may be the empty string */
    val spoilerText: String
    val username: String

    val actionable: Status
        get() = status.actionableStatus

    val actionableId: String
        get() = status.actionableStatus.id

    val rebloggedAvatar: String?
        get() = if (status.reblog != null) {
            status.account.avatar
        } else {
            null
        }

    val rebloggingStatus: Status?
        get() = if (status.reblog != null) status else null

    init {
        if (Build.VERSION.SDK_INT == 23) {
            // https://github.com/tuskyapp/Tusky/issues/563
            this.content = replaceCrashingCharacters(status.actionableStatus.content.parseAsMastodonHtml())
            this.spoilerText =
                replaceCrashingCharacters(status.actionableStatus.spoilerText).toString()
            this.username =
                replaceCrashingCharacters(status.actionableStatus.account.username).toString()
        } else {
            this.content = status.actionableStatus.content.parseAsMastodonHtml()
            this.spoilerText = status.actionableStatus.spoilerText
            this.username = status.actionableStatus.account.username
        }
        this.isCollapsible = shouldTrimStatus(this.content)
    }

    /** Helper for Java */
    fun copyWithCollapsed(isCollapsed: Boolean) = copy(isCollapsed = isCollapsed)

    fun toConversationStatusEntity(
        favourited: Boolean = status.favourited,
        bookmarked: Boolean = status.bookmarked,
        muted: Boolean = status.muted ?: false,
        poll: Poll? = status.poll,
        expanded: Boolean = isExpanded,
        collapsed: Boolean = isCollapsed,
        showingHiddenContent: Boolean = isShowingContent,
    ) = ConversationStatusEntity(
        id = id,
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
        favourited = favourited,
        bookmarked = bookmarked,
        sensitive = status.sensitive,
        spoilerText = status.spoilerText,
        attachments = status.attachments,
        mentions = status.mentions,
        tags = status.tags,
        showingHiddenContent = showingHiddenContent,
        expanded = expanded,
        collapsed = collapsed,
        muted = muted,
        poll = poll,
        language = status.language,
    )

    companion object {
        fun from(
            status: Status,
            isShowingContent: Boolean,
            isExpanded: Boolean,
            isCollapsed: Boolean,
            isDetailed: Boolean = false,
            filterAction: Filter.Action = app.pachli.entity.Filter.Action.NONE,
        ) = StatusViewData(
            status = status,
            isShowingContent = isShowingContent,
            isCollapsed = isCollapsed,
            isExpanded = isExpanded,
            isDetailed = isDetailed,
            filterAction = filterAction,
        )

        fun from(conversationStatusEntity: ConversationStatusEntity) = StatusViewData(
            status = Status(
                id = conversationStatusEntity.id,
                url = conversationStatusEntity.url,
                account = conversationStatusEntity.account.toAccount(),
                inReplyToId = conversationStatusEntity.inReplyToId,
                inReplyToAccountId = conversationStatusEntity.inReplyToAccountId,
                content = conversationStatusEntity.content,
                reblog = null,
                createdAt = conversationStatusEntity.createdAt,
                editedAt = conversationStatusEntity.editedAt,
                emojis = conversationStatusEntity.emojis,
                reblogsCount = 0,
                favouritesCount = conversationStatusEntity.favouritesCount,
                repliesCount = conversationStatusEntity.repliesCount,
                reblogged = false,
                favourited = conversationStatusEntity.favourited,
                bookmarked = conversationStatusEntity.bookmarked,
                sensitive = conversationStatusEntity.sensitive,
                spoilerText = conversationStatusEntity.spoilerText,
                visibility = Status.Visibility.DIRECT,
                attachments = conversationStatusEntity.attachments,
                mentions = conversationStatusEntity.mentions,
                tags = conversationStatusEntity.tags,
                application = null,
                pinned = false,
                muted = conversationStatusEntity.muted,
                poll = conversationStatusEntity.poll,
                card = null,
                language = conversationStatusEntity.language,
                filtered = null,
            ),
            isExpanded = conversationStatusEntity.expanded,
            isShowingContent = conversationStatusEntity.showingHiddenContent,
            isCollapsed = conversationStatusEntity.collapsed,
        )

        fun from(
            timelineStatusWithAccount: TimelineStatusWithAccount,
            gson: Gson,
            alwaysOpenSpoiler: Boolean,
            alwaysShowSensitiveMedia: Boolean,
            isDetailed: Boolean = false,
        ): StatusViewData {
            val attachments: ArrayList<Attachment> = gson.fromJson(
                timelineStatusWithAccount.status.attachments,
                attachmentArrayListType,
            ) ?: arrayListOf()
            val mentions: List<Status.Mention> = gson.fromJson(
                timelineStatusWithAccount.status.mentions,
                mentionListType,
            ) ?: emptyList()
            val tags: List<HashTag>? = gson.fromJson(
                timelineStatusWithAccount.status.tags,
                tagListType,
            )
            val application = gson.fromJson(timelineStatusWithAccount.status.application, Status.Application::class.java)
            val emojis: List<Emoji> = gson.fromJson(
                timelineStatusWithAccount.status.emojis,
                emojisListType,
            ) ?: emptyList()
            val poll: Poll? = gson.fromJson(timelineStatusWithAccount.status.poll, Poll::class.java)
            val card: Card? = gson.fromJson(timelineStatusWithAccount.status.card, Card::class.java)

            val reblog = timelineStatusWithAccount.status.reblogServerId?.let { id ->
                Status(
                    id = id,
                    url = timelineStatusWithAccount.status.url,
                    account = timelineStatusWithAccount.account.toTimelineAccount(gson),
                    inReplyToId = timelineStatusWithAccount.status.inReplyToId,
                    inReplyToAccountId = timelineStatusWithAccount.status.inReplyToAccountId,
                    reblog = null,
                    content = timelineStatusWithAccount.status.content.orEmpty(),
                    createdAt = Date(timelineStatusWithAccount.status.createdAt),
                    editedAt = timelineStatusWithAccount.status.editedAt?.let { Date(it) },
                    emojis = emojis,
                    reblogsCount = timelineStatusWithAccount.status.reblogsCount,
                    favouritesCount = timelineStatusWithAccount.status.favouritesCount,
                    reblogged = timelineStatusWithAccount.status.reblogged,
                    favourited = timelineStatusWithAccount.status.favourited,
                    bookmarked = timelineStatusWithAccount.status.bookmarked,
                    sensitive = timelineStatusWithAccount.status.sensitive,
                    spoilerText = timelineStatusWithAccount.status.spoilerText,
                    visibility = timelineStatusWithAccount.status.visibility,
                    attachments = attachments,
                    mentions = mentions,
                    tags = tags,
                    application = application,
                    pinned = false,
                    muted = timelineStatusWithAccount.status.muted,
                    poll = poll,
                    card = card,
                    repliesCount = timelineStatusWithAccount.status.repliesCount,
                    language = timelineStatusWithAccount.status.language,
                    filtered = timelineStatusWithAccount.status.filtered,
                )
            }
            val status = if (reblog != null) {
                Status(
                    id = timelineStatusWithAccount.status.serverId,
                    url = null, // no url for reblogs
                    account = timelineStatusWithAccount.reblogAccount!!.toTimelineAccount(gson),
                    inReplyToId = null,
                    inReplyToAccountId = null,
                    reblog = reblog,
                    content = "",
                    createdAt = Date(timelineStatusWithAccount.status.createdAt), // lie but whatever?
                    editedAt = null,
                    emojis = listOf(),
                    reblogsCount = 0,
                    favouritesCount = 0,
                    reblogged = false,
                    favourited = false,
                    bookmarked = false,
                    sensitive = false,
                    spoilerText = "",
                    visibility = timelineStatusWithAccount.status.visibility,
                    attachments = ArrayList(),
                    mentions = listOf(),
                    tags = listOf(),
                    application = null,
                    pinned = timelineStatusWithAccount.status.pinned,
                    muted = timelineStatusWithAccount.status.muted,
                    poll = null,
                    card = null,
                    repliesCount = timelineStatusWithAccount.status.repliesCount,
                    language = timelineStatusWithAccount.status.language,
                    filtered = timelineStatusWithAccount.status.filtered,
                )
            } else {
                Status(
                    id = timelineStatusWithAccount.status.serverId,
                    url = timelineStatusWithAccount.status.url,
                    account = timelineStatusWithAccount.account.toTimelineAccount(gson),
                    inReplyToId = timelineStatusWithAccount.status.inReplyToId,
                    inReplyToAccountId = timelineStatusWithAccount.status.inReplyToAccountId,
                    reblog = null,
                    content = timelineStatusWithAccount.status.content.orEmpty(),
                    createdAt = Date(timelineStatusWithAccount.status.createdAt),
                    editedAt = timelineStatusWithAccount.status.editedAt?.let { Date(it) },
                    emojis = emojis,
                    reblogsCount = timelineStatusWithAccount.status.reblogsCount,
                    favouritesCount = timelineStatusWithAccount.status.favouritesCount,
                    reblogged = timelineStatusWithAccount.status.reblogged,
                    favourited = timelineStatusWithAccount.status.favourited,
                    bookmarked = timelineStatusWithAccount.status.bookmarked,
                    sensitive = timelineStatusWithAccount.status.sensitive,
                    spoilerText = timelineStatusWithAccount.status.spoilerText,
                    visibility = timelineStatusWithAccount.status.visibility,
                    attachments = attachments,
                    mentions = mentions,
                    tags = tags,
                    application = application,
                    pinned = timelineStatusWithAccount.status.pinned,
                    muted = timelineStatusWithAccount.status.muted,
                    poll = poll,
                    card = card,
                    repliesCount = timelineStatusWithAccount.status.repliesCount,
                    language = timelineStatusWithAccount.status.language,
                    filtered = timelineStatusWithAccount.status.filtered,
                )
            }

            return StatusViewData(
                status = status,
                isExpanded = timelineStatusWithAccount.viewData?.expanded ?: alwaysOpenSpoiler,
                isShowingContent = timelineStatusWithAccount.viewData?.contentShowing ?: (alwaysShowSensitiveMedia || !status.actionableStatus.sensitive),
                isCollapsed = timelineStatusWithAccount.viewData?.contentCollapsed ?: true,
                isDetailed = isDetailed,
            )
        }
    }
}
