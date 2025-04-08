/*
 * Copyright 2023 Pachli Association
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

import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.NotificationData
import app.pachli.core.database.model.NotificationEntity
import app.pachli.core.database.model.NotificationReportEntity
import app.pachli.core.database.model.TranslatedStatusEntity
import app.pachli.core.database.model.TranslationState
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.model.FilterAction
import app.pachli.core.network.model.RelationshipSeveranceEvent
import app.pachli.core.network.model.Status
import app.pachli.core.network.model.TimelineAccount

/**
 * Data necessary to show a single notification.
 *
 * A notification may also need to display a status (e.g., if it is a notification
 * about boosting a status, the boosted status is also shown). However, not all
 * notifications are related to statuses (e.g., a "Someone has followed you"
 * notification) so `statusViewData` is nullable.
 *
 * @param pachliAccountId
 * @param localDomain Local domain of the logged in user's account (e.g., "mastodon.social")
 * @param type
 * @param id Notification's server ID
 * @param account Account that triggered the notification
 * @param statusViewData (optional) Viewdata for the status referenced by the notification
 * @param report
 * @param relationshipSeveranceEvent
 * @param isAboutSelf True if this notification relates to something the user
 * posted (e.g., it's a boost, favourite, or poll ending), false otherwise
 * (e.g., it's a mention).
 * @param accountFilterDecision Whether this notification should be filtered
 * because of the account that sent it, and why.
 */
data class NotificationViewData(
    override val pachliAccountId: Long,
    val localDomain: String,
    val type: NotificationEntity.Type,
    val id: String,
    val account: TimelineAccount,
    var statusViewData: StatusViewData?,
    val report: NotificationReportEntity?,
    val relationshipSeveranceEvent: RelationshipSeveranceEvent?,
    val isAboutSelf: Boolean,
    val accountFilterDecision: AccountFilterDecision,
) : IStatusViewData {
    companion object {
        /**
         *
         * @param pachliAccountEntity
         * @param data
         * @param isShowingContent
         * @param isExpanded
         * @param contentFilterAction
         * @param accountFilterDecision
         * @param isAboutSelf
         */
        fun make(
            pachliAccountEntity: AccountEntity,
            data: NotificationData,
            isShowingContent: Boolean,
            isExpanded: Boolean,
            contentFilterAction: FilterAction,
            accountFilterDecision: AccountFilterDecision?,
            isAboutSelf: Boolean,
        ) = NotificationViewData(
            pachliAccountId = pachliAccountEntity.id,
            localDomain = pachliAccountEntity.domain,
            type = data.notification.type,
            id = data.notification.serverId,
            account = data.account.toTimelineAccount(),
            statusViewData = data.status?.let {
                StatusViewData.from(
                    pachliAccountId = pachliAccountEntity.id,
                    it,
                    isExpanded = isExpanded,
                    isShowingContent = isShowingContent,
                    isDetailed = false,
                    contentFilterAction = contentFilterAction,
                )
            },
            report = data.report,
            relationshipSeveranceEvent = null,
            isAboutSelf = isAboutSelf,
            accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        )
    }

    // Implement properties for IStatusViewData. These can't be delegated to `statusViewData`
    // as that might be null. It's up to the calling code to only check these properties if
    // `statusViewData` is not null; not doing that is an illegal state, hence the exception.

    // TODO: Don't do this, it's a significant footgun, see
    // https://github.com/pachli/pachli-android/issues/669
    override val username: String
        get() = statusViewData?.username ?: throw IllegalStateException()
    override val rebloggedAvatar: String?
        get() = statusViewData?.rebloggedAvatar
    override var translation: TranslatedStatusEntity?
        get() = statusViewData?.translation
        set(value) {
            statusViewData?.translation = value
        }
    override val isExpanded: Boolean
        get() = statusViewData?.isExpanded ?: throw IllegalStateException()
    override val isShowingContent: Boolean
        get() = statusViewData?.isShowingContent ?: throw IllegalStateException()
    override val isCollapsible: Boolean
        get() = statusViewData?.isCollapsible ?: throw IllegalStateException()
    override val isCollapsed: Boolean
        get() = statusViewData?.isCollapsed ?: throw IllegalStateException()
    override val spoilerText: String
        get() = statusViewData?.spoilerText ?: throw IllegalStateException()
    override val content: CharSequence
        get() = statusViewData?.content ?: throw IllegalStateException()
    override val status: Status
        get() = statusViewData?.status ?: throw IllegalStateException()
    override val actionable: Status
        get() = statusViewData?.actionable ?: throw IllegalStateException()
    override val actionableId: String
        get() = statusViewData?.actionableId ?: throw IllegalStateException()
    override val rebloggingStatus: Status?
        get() = statusViewData?.rebloggingStatus
    override var contentFilterAction: FilterAction
        get() = statusViewData?.contentFilterAction ?: throw IllegalStateException()
        set(value) {
            statusViewData?.contentFilterAction = value
        }
    override val translationState: TranslationState
        get() = statusViewData?.translationState ?: throw IllegalStateException()
}
