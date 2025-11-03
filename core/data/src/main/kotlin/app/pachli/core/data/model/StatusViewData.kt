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
import app.pachli.core.common.util.shouldTrimStatus
import app.pachli.core.data.BuildConfig
import app.pachli.core.database.dao.TimelineStatusWithAccount
import app.pachli.core.database.model.TSQ
import app.pachli.core.database.model.TranslatedStatusEntity
import app.pachli.core.database.model.TranslationState
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.AttachmentDisplayReason
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterContext
import app.pachli.core.model.FilterResult
import app.pachli.core.model.IStatus
import app.pachli.core.model.MatchingFilter
import app.pachli.core.model.Status
import app.pachli.core.model.TimelineAccount
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.network.replaceCrashingCharacters

/**
 * Interface for the data shown when viewing a status, or something that wraps
 * a status, like [NotificationViewData] or
 * [app.pachli.components.conversation.ConversationViewData].
 */
interface IStatusViewData : IStatus {
    /** ID of the Pachli account that loaded this status. */
    val pachliAccountId: Long
    val username: String

    // TODO: rebloggedAvatar is the wrong name for this property. This is the avatar to show
    // inset in the main avatar view. When viewing a boosted status in a timeline this is
    // avatar that boosted it, but when viewing a notification about a boost or favourite
    // this the avatar that boosted/favourited it
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
//    val spoilerText: String

    /**
     * The content to show for this status. May be the original content, or
     * translated, depending on `translationState`.
     */
//    val content: CharSequence

    /** The underlying status */
    val status: Status

//    val quoteViewData: IStatusViewData?

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

    /** The [FilterAction] to apply, based on the status' content. */
    var contentFilterAction: FilterAction

    /** The current translation state */
    val translationState: TranslationState

    /** How to display attachments on this status. */
    val attachmentDisplayAction: AttachmentDisplayAction

    /**
     * If this is a reply, the account being replied to.
     *
     * Null in two cases:
     *
     * 1. The status is not a reply.
     * 2. The status is a reply, and we do not have a local copy of the account
     * details to show, and a generic "Reply" indicator should be shown.
     */
    val replyToAccount: TimelineAccount?

    /**
     * Specifies whether this status should be shown with the "detailed" layout, meaning it is
     * the status that has a focus when viewing a thread.
     */
    val isDetailed: Boolean
}

interface IStatusViewDataQ : IStatusViewData {
    val statusViewData: StatusViewData
    val quotedViewData: StatusViewData?
}

/**
 * Contains a status, and an optional status being quoted.
 */
// TODO: Better name for this is "StatusItemViewData" -- "item" implies UI based
data class StatusViewDataQ(
    override val statusViewData: StatusViewData,
    override val quotedViewData: StatusViewData? = null,
) : IStatusViewDataQ, IStatusViewData by statusViewData {

    companion object {
        fun from(
            pachliAccountId: Long,
            tsq: TSQ,
            isExpanded: Boolean,
            isDetailed: Boolean = false,
            contentFilterAction: FilterAction,
            translationState: TranslationState = TranslationState.SHOW_ORIGINAL,
            showSensitiveMedia: Boolean,
            filterContext: FilterContext?,
        ): StatusViewDataQ {
            return StatusViewDataQ(
                statusViewData = StatusViewData.from(
                    pachliAccountId,
                    tsq.timelineStatus.toStatus(),
                    translation = tsq.timelineStatus.translatedStatus,
                    isExpanded = tsq.timelineStatus.viewData?.expanded ?: isExpanded,
                    isCollapsed = tsq.timelineStatus.viewData?.contentCollapsed ?: true,
                    isDetailed = isDetailed,
                    contentFilterAction = contentFilterAction,
                    attachmentDisplayAction = tsq.timelineStatus.getAttachmentDisplayAction(
                        filterContext,
                        showSensitiveMedia,
                    ),
                    translationState = tsq.timelineStatus.viewData?.translationState ?: translationState,
                    replyToAccount = tsq.timelineStatus.replyAccount?.asModel(),
                ),
                quotedViewData = tsq.quotedStatus?.let { status ->
                    StatusViewData.from(
                        pachliAccountId,
                        status.toStatus(),
                        translation = status.translatedStatus,
                        isExpanded = status.viewData?.expanded ?: isExpanded,
                        isCollapsed = status.viewData?.contentCollapsed ?: true,
                        isDetailed = isDetailed,
                        contentFilterAction = contentFilterAction,
                        attachmentDisplayAction = status.getAttachmentDisplayAction(
                            filterContext,
                            showSensitiveMedia,
                        ),
                        translationState = status.viewData?.translationState ?: translationState,
                        replyToAccount = status.replyAccount?.asModel(),
                    )
                },
            )
        }
    }
}

/**
 * Data required to display a status.
 */
data class StatusViewData(
    override val pachliAccountId: Long,
    override val status: Status,
//    override val quoteViewData: StatusViewData? = null,
    override var translation: TranslatedStatusEntity? = null,
    override val isExpanded: Boolean,
    override val isCollapsed: Boolean,
    override var contentFilterAction: FilterAction = FilterAction.NONE,
    override val translationState: TranslationState,
    override val attachmentDisplayAction: AttachmentDisplayAction,
    override val replyToAccount: TimelineAccount?,
    override val isDetailed: Boolean = false,
) : IStatusViewData, IStatus by status {
    override val isCollapsible: Boolean

    private val _content: CharSequence

    @Suppress("ktlint:standard:property-naming")
    private val _translatedContent: CharSequence

    override val content: CharSequence
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
        get() = status.actionableStatus.statusId

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
            this._content = replaceCrashingCharacters(status.actionableStatus.content) ?: ""
            this._spoilerText =
                replaceCrashingCharacters(status.actionableStatus.spoilerText).toString()
            this.username =
                replaceCrashingCharacters(status.actionableStatus.account.username).toString()
            this._translatedContent = (translation?.content?.let { replaceCrashingCharacters(it) } ?: "")
            this._translatedSpoilerText = translation?.spoilerText?.let {
                replaceCrashingCharacters(it).toString()
            } ?: ""
        } else {
            this._content = status.actionableStatus.content
            this._translatedContent = translation?.content ?: ""
            this._spoilerText = status.actionableStatus.spoilerText
            this._translatedSpoilerText = translation?.spoilerText ?: ""
            this.username = status.actionableStatus.account.username
        }
        this.isCollapsible = shouldTrimStatus(this.content.parseAsMastodonHtml())
    }

    companion object {
        fun from(
            pachliAccountId: Long,
            status: Status,
            isExpanded: Boolean,
            isCollapsed: Boolean,
            isDetailed: Boolean = false,
            contentFilterAction: FilterAction = FilterAction.NONE,
            attachmentDisplayAction: AttachmentDisplayAction = AttachmentDisplayAction.Show(),
            translationState: TranslationState = TranslationState.SHOW_ORIGINAL,
            translation: TranslatedStatusEntity? = null,
            replyToAccount: TimelineAccount?,
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

//            return StatusViewData(
//                pachliAccountId = pachliAccountId,
//                status = status,
//                isCollapsed = isCollapsed,
//                isExpanded = isExpanded,
//                isDetailed = isDetailed,
//                contentFilterAction = contentFilterAction,
//                attachmentDisplayAction = attachmentDisplayAction,
//                translationState = translationState,
//                translation = translation,
//            )

            return StatusViewData(
                pachliAccountId = pachliAccountId,
                status = status,
//                quoteViewData = (status.quote as? Status.Quote.FullQuote)?.status?.let { q ->
//                    StatusViewData.from(
//                        pachliAccountId = pachliAccountId,
//                        status = q,
//                    )
//                },
                isCollapsed = isCollapsed,
                isExpanded = isExpanded,
                isDetailed = isDetailed,
                contentFilterAction = contentFilterAction,
                attachmentDisplayAction = attachmentDisplayAction,
                translationState = translationState,
                translation = translation,
                replyToAccount = replyToAccount,
            )
        }

        fun from(
            pachliAccountId: Long,
            tsq: TSQ,
            isExpanded: Boolean,
            isDetailed: Boolean = false,
            contentFilterAction: FilterAction,
            translationState: TranslationState = TranslationState.SHOW_ORIGINAL,
            showSensitiveMedia: Boolean,
            filterContext: FilterContext?,
        ): StatusViewData {
            val status = tsq.timelineStatus
            val quote = tsq.quotedStatus
            return StatusViewData(
                pachliAccountId = pachliAccountId,
                status = status.toStatus(),
//                quoteViewData = quote?.let {
//                    StatusViewData.from(
//                        pachliAccountId,
//                        status = it.toStatus(),
//                        isExpanded = isExpanded,
//                        isCollapsed = it.viewData?.contentCollapsed ?: true,
//                        isDetailed = false,
//                        contentFilterAction = contentFilterAction,
//                        attachmentDisplayAction = it.getAttachmentDisplayAction(
//                            filterContext,
//                            showSensitiveMedia,
//                        ),
//                        translationState = it.viewData?.translationState ?: TranslationState.SHOW_ORIGINAL,
//                    )
//                },
                translation = tsq.timelineStatus.translatedStatus,
                isExpanded = tsq.timelineStatus.viewData?.expanded ?: isExpanded,
                isCollapsed = tsq.timelineStatus.viewData?.contentCollapsed ?: true,
                isDetailed = isDetailed,
                contentFilterAction = contentFilterAction,
                attachmentDisplayAction = status.getAttachmentDisplayAction(
                    filterContext,
                    showSensitiveMedia,
                ),
                translationState = tsq.timelineStatus.viewData?.translationState ?: translationState,
                replyToAccount = tsq.timelineStatus.replyAccount?.asModel(),
            )
        }

//        /**
//         *
//         * @param tsq
//         * @param isExpanded Default expansion behaviour for a status with a content
//         * warning. Used if the status viewdata is null
//         * @param isDetailed True if the status should be shown with the detailed
//         * layout, false otherwise.
//         * @param contentFilterAction
//         * @param attachmentDisplayAction
//         * @param translationState Default translation state for this status. Used if
//         * the status viewdata is null.
//         */
//        fun from(
//            pachliAccountId: Long,
//            tsq: TSQ,
//            isExpanded: Boolean,
//            isDetailed: Boolean = false,
//            contentFilterAction: FilterAction,
//            attachmentDisplayAction: AttachmentDisplayAction = AttachmentDisplayAction.Show(),
//            translationState: TranslationState = TranslationState.SHOW_ORIGINAL,
//            showSensitiveMedia: Boolean,
//            filterContext: FilterContext?,
//        ): StatusViewData {
//            val status = tsq.timelineStatus.toStatus()
//            val quote = tsq.quotedStatus?.toStatus()
//            return StatusViewData(
//                pachliAccountId = pachliAccountId,
//                status = status,
//                quoteViewData = quote?.let {
//                    StatusViewData.from(
//                        pachliAccountId,
//                        status = it,
//                        isExpanded = isExpanded,
//                        isCollapsed = tsq.quotedStatus?.viewData?.contentCollapsed ?: true,
//                        isDetailed = false,
//                        contentFilterAction = contentFilterAction,
//                        attachmentDisplayAction = it.getAttachmentDisplayAction(
//                            filterContext,
//                            showSensitiveMedia,
//                            tsq.quotedStatus?.viewData?.attachmentDisplayAction,
//                        ),
//                        translationState = translationState,
//                    )
//                },
//                translation = tsq.timelineStatus.translatedStatus,
//                isExpanded = tsq.timelineStatus.viewData?.expanded ?: isExpanded,
//                isCollapsed = tsq.timelineStatus.viewData?.contentCollapsed ?: true,
//                isDetailed = isDetailed,
//                contentFilterAction = contentFilterAction,
//                attachmentDisplayAction = attachmentDisplayAction,
//                translationState = tsq.timelineStatus.viewData?.translationState ?: translationState,
//            )
//        }
    }
}

/* ---- copied from StatusExtensions.kt ---- */

/**
 * Returns the [AttachmentDisplayAction] for [this] given the current [filterContext],
 * whether [showSensitiveMedia] is true.
 *
 * @param filterContext Applicable filter context. May be null for timelines that are
 * not filtered (e.g., private messages).
 * @param showSensitiveMedia True if the user's preference is to show attachments
 * marked sensitive.
 * @param cachedAction
 */
fun TimelineStatusWithAccount.getAttachmentDisplayAction(filterContext: FilterContext?, showSensitiveMedia: Boolean) = getAttachmentDisplayAction(
    filterContext,
    status.filtered,
    status.sensitive,
    showSensitiveMedia = showSensitiveMedia,
    cachedAction = viewData?.attachmentDisplayAction,
)

/**
 * Returns the [AttachmentDisplayAction] for given the current [filterContext], based on
 * the [matchingFilters], whether the content is [sensitive], if [showSensitiveMedia]
 * is true, and the [cachedAction] (if any).
 *
 * @param filterContext Applicable filter context. May be null for timelines that are
 * not filtered (e.g., private messages).
 * @param matchingFilters List of filters that matched the status.
 * @param sensitive True if the status was marked senstive.
 * @param showSensitiveMedia True if the user's preference is to show attachments
 * marked sensitive.
 * @param cachedAction
 */
private fun getAttachmentDisplayAction(
    filterContext: FilterContext?,
    matchingFilters: List<FilterResult>?,
    sensitive: Boolean,
    showSensitiveMedia: Boolean,
    cachedAction: AttachmentDisplayAction?,
): AttachmentDisplayAction {
    // Hide attachments if there is any matching "blur" filter.
    val matchingBlurFilters = filterContext?.let {
        matchingFilters
            ?.filter { it.filter.filterAction == FilterAction.BLUR }
            ?.filter { it.filter.contexts.contains(filterContext) }
            ?.map { MatchingFilter(filterId = it.filter.id, title = it.filter.title) }
    }.orEmpty()

    // Any matching filters probably hides the attachment.
    if (matchingBlurFilters.isNotEmpty()) {
        val hideAction = AttachmentDisplayAction.Hide(
            reason = AttachmentDisplayReason.BlurFilter(matchingBlurFilters),
        )

        // If the cached decision is a Show then return the Show, but with an updated
        // originalDecision. This ensures that if the user then hides the attachment
        // the description that shows which filters matched reflects the user's latest
        // set of filters.
        //
        // The filters changing *doesn't* cause this to reset to Hide because the
        // user has already seen the attachment, and decided to keep seeing it.
        // Hiding it from them again isn't helpful.
        (cachedAction as? AttachmentDisplayAction.Show)?.let {
            return it.copy(originalAction = hideAction)
        }

        // Otherwise, the decision to hide is good.
        return hideAction
    }

    // Now safe to use the cached decision, if present. If the user overrode a Hide with
    // a Show this will be returned here.
    cachedAction?.let { return it }

    // Hide attachments if the status is marked sensitive and the user doesn't want to
    // see them.
    if (sensitive && !showSensitiveMedia) {
        return AttachmentDisplayAction.Hide(reason = AttachmentDisplayReason.Sensitive)
    }

    // Attachment is OK, and can be shown.
    return AttachmentDisplayAction.Show()
}
