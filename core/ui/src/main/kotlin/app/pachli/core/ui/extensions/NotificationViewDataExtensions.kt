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

package app.pachli.core.ui.extensions

import app.pachli.core.data.model.NotificationViewData
import app.pachli.core.data.model.NotificationViewData.FollowNotificationViewData
import app.pachli.core.data.model.NotificationViewData.FollowRequestNotificationViewData
import app.pachli.core.data.model.NotificationViewData.ModerationWarningNotificationViewData
import app.pachli.core.data.model.NotificationViewData.ReportNotificationViewData
import app.pachli.core.data.model.NotificationViewData.SeveredRelationshipsNotificationViewData
import app.pachli.core.data.model.NotificationViewData.SignupNotificationViewData
import app.pachli.core.data.model.NotificationViewData.UnknownNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.FavouriteNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.MentionNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.PollNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.ReblogNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.StatusNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.UpdateNotificationViewData
import app.pachli.core.data.model.StatusViewDataQ
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.NotificationData
import app.pachli.core.database.model.NotificationEntity
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterContext

/**
 *
 * @param pachliAccountEntity
 * @param data
 * @param showSensitiveMedia
 * @param isExpanded
 * @param contentFilterAction
 * @param accountFilterDecision
 * @param isAboutSelf
 */
fun NotificationViewData.Companion.make(
    pachliAccountEntity: AccountEntity,
    data: NotificationData,
    showSensitiveMedia: Boolean,
    isExpanded: Boolean,
    contentFilterAction: FilterAction,
    quoteContentFilterAction: FilterAction?,
    accountFilterDecision: AccountFilterDecision?,
    isAboutSelf: Boolean,
) = when (data.notification.type) {
    NotificationEntity.Type.UNKNOWN -> UnknownNotificationViewData(
        pachliAccountId = pachliAccountEntity.id,
        localDomain = pachliAccountEntity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
    )

    NotificationEntity.Type.MENTION -> MentionNotificationViewData(
        pachliAccountId = pachliAccountEntity.id,
        localDomain = pachliAccountEntity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        statusViewDataQ = data.status!!.let {
            StatusViewDataQ.from(
                pachliAccountId = pachliAccountEntity.id,
                it,
                isExpanded = isExpanded,
                isDetailed = false,
                contentFilterAction = contentFilterAction,
                quoteContentFilterAction = quoteContentFilterAction,
                showSensitiveMedia = showSensitiveMedia,
                filterContext = FilterContext.NOTIFICATIONS,
            )
        },
    )

    NotificationEntity.Type.REBLOG -> ReblogNotificationViewData(
        pachliAccountId = pachliAccountEntity.id,
        localDomain = pachliAccountEntity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        statusViewDataQ = data.status!!.let {
            StatusViewDataQ.from(
                pachliAccountId = pachliAccountEntity.id,
                it,
                isExpanded = isExpanded,
                isDetailed = false,
                contentFilterAction = contentFilterAction,
                quoteContentFilterAction = quoteContentFilterAction,
                showSensitiveMedia = showSensitiveMedia,
                filterContext = FilterContext.NOTIFICATIONS,
            )
        },
    )

    NotificationEntity.Type.FAVOURITE -> FavouriteNotificationViewData(
        pachliAccountId = pachliAccountEntity.id,
        localDomain = pachliAccountEntity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        statusViewDataQ = data.status!!.let {
            StatusViewDataQ.from(
                pachliAccountId = pachliAccountEntity.id,
                it,
                isExpanded = isExpanded,
                isDetailed = false,
                contentFilterAction = contentFilterAction,
                quoteContentFilterAction = quoteContentFilterAction,
                showSensitiveMedia = showSensitiveMedia,
                filterContext = FilterContext.NOTIFICATIONS,
            )
        },
    )

    NotificationEntity.Type.FOLLOW -> FollowNotificationViewData(
        pachliAccountId = pachliAccountEntity.id,
        localDomain = pachliAccountEntity.domain,
        notificationId = data.notification.serverId,
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        account = data.account.asModel(),
    )

    NotificationEntity.Type.FOLLOW_REQUEST -> FollowRequestNotificationViewData(
        pachliAccountId = pachliAccountEntity.id,
        localDomain = pachliAccountEntity.domain,
        notificationId = data.notification.serverId,
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        account = data.account.asModel(),
    )

    NotificationEntity.Type.POLL -> PollNotificationViewData(
        pachliAccountId = pachliAccountEntity.id,
        localDomain = pachliAccountEntity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        statusViewDataQ = data.status!!.let {
            StatusViewDataQ.from(
                pachliAccountId = pachliAccountEntity.id,
                it,
                isExpanded = isExpanded,
                isDetailed = false,
                contentFilterAction = contentFilterAction,
                quoteContentFilterAction = quoteContentFilterAction,
                showSensitiveMedia = showSensitiveMedia,
                filterContext = FilterContext.NOTIFICATIONS,
            )
        },
    )

    NotificationEntity.Type.STATUS -> StatusNotificationViewData(
        pachliAccountId = pachliAccountEntity.id,
        localDomain = pachliAccountEntity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        statusViewDataQ = data.status!!.let {
            StatusViewDataQ.from(
                pachliAccountId = pachliAccountEntity.id,
                it,
                isExpanded = isExpanded,
                isDetailed = false,
                contentFilterAction = contentFilterAction,
                quoteContentFilterAction = quoteContentFilterAction,
                showSensitiveMedia = showSensitiveMedia,
                filterContext = FilterContext.NOTIFICATIONS,
            )
        },
    )

    NotificationEntity.Type.SIGN_UP -> SignupNotificationViewData(
        pachliAccountId = pachliAccountEntity.id,
        localDomain = pachliAccountEntity.domain,
        notificationId = data.notification.serverId,
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        account = data.account.asModel(),
    )

    NotificationEntity.Type.UPDATE -> UpdateNotificationViewData(
        pachliAccountId = pachliAccountEntity.id,
        localDomain = pachliAccountEntity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        statusViewDataQ = data.status!!.let {
            StatusViewDataQ.from(
                pachliAccountId = pachliAccountEntity.id,
                it,
                isExpanded = isExpanded,
                isDetailed = false,
                contentFilterAction = contentFilterAction,
                quoteContentFilterAction = quoteContentFilterAction,
                showSensitiveMedia = showSensitiveMedia,
                filterContext = FilterContext.NOTIFICATIONS,
            )
        },
    )

    NotificationEntity.Type.REPORT -> ReportNotificationViewData(
        pachliAccountId = pachliAccountEntity.id,
        localDomain = pachliAccountEntity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        report = data.report!!,
    )

    NotificationEntity.Type.SEVERED_RELATIONSHIPS -> SeveredRelationshipsNotificationViewData(
        pachliAccountId = pachliAccountEntity.id,
        localDomain = pachliAccountEntity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        relationshipSeveranceEvent = data.relationshipSeveranceEvent!!.asModel(),
    )

    NotificationEntity.Type.MODERATION_WARNING -> ModerationWarningNotificationViewData(
        pachliAccountId = pachliAccountEntity.id,
        localDomain = pachliAccountEntity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        accountWarning = data.accountWarning!!.asModel(),
    )
}
