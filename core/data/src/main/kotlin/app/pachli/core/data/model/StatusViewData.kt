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
package app.pachli.core.data.model

import android.os.Build
import android.text.Spanned
import android.text.SpannedString
import app.pachli.core.common.util.shouldTrimStatus
import app.pachli.core.data.BuildConfig
import app.pachli.core.database.model.TimelineStatusWithAccount
import app.pachli.core.database.model.TranslatedStatusEntity
import app.pachli.core.database.model.TranslationState
import app.pachli.core.model.FilterAction
import app.pachli.core.network.model.Status
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.network.replaceCrashingCharacters

/**
 * Interface for the data shown when viewing a status, or something that wraps
 * a status, like [NotificationViewData] or
 * [app.pachli.components.conversation.ConversationViewData].
 */
interface IStatusViewData {
    /** ID of the Pachli account that loaded this status. */
    val pachliAccountId: Long
    val username: String
    val rebloggedAvatar: String?

    var translation: TranslatedStatusEntity?

    /**
     * If the status includes a non-empty content warning ([spoilerText]), specifies whether
     * just the content warning is showing (false), or the whole status content is showing (true).
     *
     * Ignored if there is no content warning.
     */
    val isExpanded: Boolean

    /**
     * If the status contains attached media, specifies whether whether the media is shown
     * (true), or not (false).
     */
    val isShowingContent: Boolean

    /**
     * Specifies whether the content of this status is long enough to be automatically
     * collapsed or if it should show all content regardless.
     *
     * @return Whether the status is collapsible or never collapsed.
     */
    val isCollapsible: Boolean

    /**
     * Specifies whether the content of this status is currently limited in visibility to the first
     * 500 characters or not.
     *
     * @return Whether the status is collapsed or fully expanded.
     */
    val isCollapsed: Boolean

    /** The content warning, may be the empty string */
    val spoilerText: String

    /**
     * The content to show for this status. May be the original content, or
     * translated, depending on `translationState`.
     */
    val content: Spanned

    /** The underlying network status */
    val status: Status

    /**
     * The "actionable" status; the one on which the user can perform actions
     * (reblog, favourite, reply, etc).
     *
     * A status may refer to another status. For example, if this is status `B`,
     * and `B` is a reblog of status `A`, then `A` is the "actionable" status.
     *
     * If this is a top-level status (e.g., it's not a reblog, etc) then `status`
     * and `actionable` are the same.
     */
    val actionable: Status

    /**
     * The ID of the [actionable] status.
     */
    val actionableId: String

    val rebloggingStatus: Status?

    // TODO: This means that null checks are required elsewhere in the code to deal with
    // the possibility that this might not be NONE, but that status.filtered is null or
    // empty (e.g., StatusBaseViewHolder.setupFilterPlaceholder()). It would be better
    // if the Filter.Action class subtypes carried the FilterResult information with them,
    // and it's impossible to construct them with an empty list.
    /** The [FilterAction] to apply, based on the status' content. */
    var contentFilterAction: FilterAction

    /** The current translation state */
    val translationState: TranslationState
}

/**
 * Data required to display a status.
 */
data class StatusViewData(
    override val pachliAccountId: Long,
    override var status: Status,
    override var translation: TranslatedStatusEntity? = null,
    override val isExpanded: Boolean,
    override val isShowingContent: Boolean,
    override val isCollapsed: Boolean,
    override var contentFilterAction: FilterAction = FilterAction.NONE,
    override val translationState: TranslationState,

    /**
     * Specifies whether this status should be shown with the "detailed" layout, meaning it is
     * the status that has a focus when viewing a thread.
     */
    val isDetailed: Boolean = false,
) : IStatusViewData {
    val id: String
        get() = status.id

    override val isCollapsible: Boolean

    private val _content: Spanned

    @Suppress("ktlint:standard:property-naming")
    private val _translatedContent: Spanned

    override val content: Spanned
        get() = if (translationState == TranslationState.SHOW_TRANSLATION) _translatedContent else _content

    private val _spoilerText: String

    @Suppress("ktlint:standard:property-naming")
    private val _translatedSpoilerText: String

    override val spoilerText: String
        get() = if (translationState == TranslationState.SHOW_TRANSLATION) _translatedSpoilerText else _spoilerText

    override val username: String

    override val actionable: Status
        get() = status.actionableStatus

    override val actionableId: String
        get() = status.actionableStatus.id

    override val rebloggedAvatar: String?
        get() = if (status.reblog != null) {
            status.account.avatar
        } else {
            null
        }

    override val rebloggingStatus: Status?
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

    companion object {
        fun from(
            pachliAccountId: Long,
            status: Status,
            isShowingContent: Boolean,
            isExpanded: Boolean,
            isCollapsed: Boolean,
            isDetailed: Boolean = false,
            contentFilterAction: FilterAction = FilterAction.NONE,
            translationState: TranslationState = TranslationState.SHOW_ORIGINAL,
            translation: TranslatedStatusEntity? = null,
        ): StatusViewData {
            if (BuildConfig.DEBUG) {
                // TODO: Ensure that invalid state is not representable.
                // Currently the translation is encoded in both `translationState` and
                // `translation`. If they get out of sync then this will fire. It would be
                // better to encode the translation in a single type (enum, sealed class?)
                // that can only represent valid states, so this can't happen.
                if (translationState == TranslationState.SHOW_TRANSLATION && translation == null) {
                    throw IllegalStateException("trying to show a null translation")
                }
            }

            return StatusViewData(
                pachliAccountId = pachliAccountId,
                status = status,
                isShowingContent = isShowingContent,
                isCollapsed = isCollapsed,
                isExpanded = isExpanded,
                isDetailed = isDetailed,
                contentFilterAction = contentFilterAction,
                translationState = translationState,
                translation = translation,
            )
        }

        /**
         *
         * @param timelineStatusWithAccount
         * @param isExpanded Default expansion behaviour for a status with a content
         * warning. Used if the status viewdata is null
         * @param isShowingContent Default behaviour for a status with attached media.
         * Used if the status viewdata is null.
         * @param isDetailed True if the status should be shown with the detailed
         * layout, false otherwise.
         * @param contentFilterAction
         * @param translationState Default translation state for this status. Used if
         * the status viewdata is null.
         */
        fun from(
            pachliAccountId: Long,
            timelineStatusWithAccount: TimelineStatusWithAccount,
            isExpanded: Boolean,
            isShowingContent: Boolean,
            isDetailed: Boolean = false,
            contentFilterAction: FilterAction,
            translationState: TranslationState = TranslationState.SHOW_ORIGINAL,
        ): StatusViewData {
            val status = timelineStatusWithAccount.toStatus()
            return StatusViewData(
                pachliAccountId = pachliAccountId,
                status = status,
                translation = timelineStatusWithAccount.translatedStatus,
                isExpanded = timelineStatusWithAccount.viewData?.expanded ?: isExpanded,
                isShowingContent = timelineStatusWithAccount.viewData?.contentShowing ?: (isShowingContent || !status.actionableStatus.sensitive),
                isCollapsed = timelineStatusWithAccount.viewData?.contentCollapsed ?: true,
                isDetailed = isDetailed,
                contentFilterAction = contentFilterAction,
                translationState = timelineStatusWithAccount.viewData?.translationState ?: translationState,
            )
        }
    }
}
