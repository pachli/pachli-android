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

import android.text.Spanned
import app.pachli.core.database.model.TranslatedStatusEntity
import app.pachli.core.database.model.TranslationState
import app.pachli.core.model.FilterAction
import app.pachli.core.network.model.Notification
import app.pachli.core.network.model.RelationshipSeveranceEvent
import app.pachli.core.network.model.Report
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
 * @param type
 * @param id
 * @param account
 * @param statusViewData
 * @param report
 * @param relationshipSeveranceEvent
 * @param isAboutSelf True if this notification relates to something the user
 * posted (e.g., it's a boost, favourite, or poll ending), false otherwise
 * (e.g., it's a mention).
 */
data class NotificationViewData(
    val type: Notification.Type,
    val id: String,
    val account: TimelineAccount,
    var statusViewData: StatusViewData?,
    val report: Report?,
    val relationshipSeveranceEvent: RelationshipSeveranceEvent?,
    val isAboutSelf: Boolean,
) : IStatusViewData {
    companion object {
        fun from(
            notification: Notification,
            isShowingContent: Boolean,
            isExpanded: Boolean,
            isCollapsed: Boolean,
            contentFilterAction: FilterAction,
            isAboutSelf: Boolean,
        ) = NotificationViewData(
            notification.type,
            notification.id,
            notification.account,
            notification.status?.let { status ->
                StatusViewData.from(
                    status,
                    isShowingContent,
                    isExpanded,
                    isCollapsed,
                    contentFilterAction = contentFilterAction,
                )
            },
            notification.report,
            notification.relationshipSeveranceEvent,
            isAboutSelf,
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
    override val content: Spanned
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
