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
 * You should have received a copy of the GNU General Public License along with Pachli; if not,
 * see <http://www.gnu.org/licenses>.
 */
package app.pachli.viewdata

import android.os.Build
import android.text.Spanned
import android.text.SpannedString
import app.pachli.core.database.model.ConversationAccountEntity
import app.pachli.core.database.model.ConversationStatusEntity
import app.pachli.core.database.model.TimelineStatusWithAccount
import app.pachli.core.database.model.TranslatedStatusEntity
import app.pachli.core.database.model.TranslationState
import app.pachli.core.network.model.Filter
import app.pachli.core.network.model.Poll
import app.pachli.core.network.model.Status
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.network.replaceCrashingCharacters
import app.pachli.util.shouldTrimStatus
import com.google.gson.Gson

/**
 * Data required to display a status.
 */
data class StatusViewData(
    var status: Status,
    var translation: TranslatedStatusEntity? = null,

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

    /** True if the translated content should be shown (if it exists) */
    val translationState: TranslationState,
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

    private val _content: Spanned

    private val _translatedContent: Spanned

    val content: Spanned
        get() = if (translationState == TranslationState.SHOW_TRANSLATION) _translatedContent else _content

    private val _spoilerText: String
    private val _translatedSpoilerText: String

    /** The content warning, may be the empty string */
    val spoilerText: String
        get() = if (translationState == TranslationState.SHOW_TRANSLATION) _translatedSpoilerText else _spoilerText

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
            this._content = replaceCrashingCharacters(status.actionableStatus.content.parseAsMastodonHtml())
            this._spoilerText =
                replaceCrashingCharacters(status.actionableStatus.spoilerText).toString()
            this.username =
                replaceCrashingCharacters(status.actionableStatus.account.username).toString()
            this._translatedContent = translation?.content?.let {
                replaceCrashingCharacters(it.parseAsMastodonHtml())
            } ?: SpannedString("")
            this._translatedSpoilerText = translation?.spoilerText?.let {
                replaceCrashingCharacters(it).toString()
            } ?: ""
        } else {
            this._content = status.actionableStatus.content.parseAsMastodonHtml()
            this._translatedContent = translation?.content?.parseAsMastodonHtml() ?: SpannedString("")
            this._spoilerText = status.actionableStatus.spoilerText
            this._translatedSpoilerText = translation?.spoilerText ?: ""
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
            filterAction: Filter.Action = Filter.Action.NONE,
            translationState: TranslationState = TranslationState.SHOW_ORIGINAL,
            translation: TranslatedStatusEntity? = null,
        ) = StatusViewData(
            status = status,
            isShowingContent = isShowingContent,
            isCollapsed = isCollapsed,
            isExpanded = isExpanded,
            isDetailed = isDetailed,
            filterAction = filterAction,
            translationState = translationState,
            translation = translation,
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
            translationState = TranslationState.SHOW_ORIGINAL, // TODO: Include this in conversationStatusEntity
        )

        fun from(
            timelineStatusWithAccount: TimelineStatusWithAccount,
            gson: Gson,
            isExpanded: Boolean,
            isShowingContent: Boolean,
            isDetailed: Boolean = false,
            translationState: TranslationState = TranslationState.SHOW_ORIGINAL,
        ): StatusViewData {
            val status = timelineStatusWithAccount.toStatus(gson)
            return StatusViewData(
                status = status,
                translation = timelineStatusWithAccount.translatedStatus,
                isExpanded = timelineStatusWithAccount.viewData?.expanded ?: isExpanded,
                isShowingContent = timelineStatusWithAccount.viewData?.contentShowing ?: (isShowingContent || !status.actionableStatus.sensitive),
                isCollapsed = timelineStatusWithAccount.viewData?.contentCollapsed ?: true,
                isDetailed = isDetailed,
                translationState = timelineStatusWithAccount.viewData?.translationState ?: translationState,
            )
        }
    }
}
