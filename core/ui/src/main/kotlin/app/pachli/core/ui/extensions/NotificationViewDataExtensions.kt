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
import app.pachli.core.data.model.NotificationViewData.WithStatus.QuoteNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.QuotedUpdateNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.ReblogNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.StatusNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.UpdateNotificationViewData
import app.pachli.core.data.model.StatusItemViewData
import app.pachli.core.data.repository.PachliAccount
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
    pachliAccount: PachliAccount,
    data: NotificationData,
    showSensitiveMedia: Boolean,
    isExpanded: Boolean,
    contentFilterAction: FilterAction,
    quoteContentFilterAction: FilterAction?,
    accountFilterDecision: AccountFilterDecision?,
    isAboutSelf: Boolean,
) = when (data.notification.type) {
    NotificationEntity.Type.UNKNOWN -> UnknownNotificationViewData(
        pachliAccountId = pachliAccount.entity.id,
        localDomain = pachliAccount.entity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
    )

    NotificationEntity.Type.MENTION -> MentionNotificationViewData(
        pachliAccountId = pachliAccount.entity.id,
        localDomain = pachliAccount.entity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        statusItemViewData = data.status!!.let {
            StatusItemViewData.from(
                pachliAccount = pachliAccount,
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
        pachliAccountId = pachliAccount.entity.id,
        localDomain = pachliAccount.entity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        statusItemViewData = data.status!!.let {
            StatusItemViewData.from(
                pachliAccount = pachliAccount,
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
        pachliAccountId = pachliAccount.entity.id,
        localDomain = pachliAccount.entity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        statusItemViewData = data.status!!.let {
            StatusItemViewData.from(
                pachliAccount = pachliAccount,
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
        pachliAccountId = pachliAccount.entity.id,
        localDomain = pachliAccount.entity.domain,
        notificationId = data.notification.serverId,
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        account = data.account.asModel(),
    )

    NotificationEntity.Type.FOLLOW_REQUEST -> FollowRequestNotificationViewData(
        pachliAccountId = pachliAccount.entity.id,
        localDomain = pachliAccount.entity.domain,
        notificationId = data.notification.serverId,
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        account = data.account.asModel(),
    )

    NotificationEntity.Type.POLL -> PollNotificationViewData(
        pachliAccountId = pachliAccount.entity.id,
        localDomain = pachliAccount.entity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        statusItemViewData = data.status!!.let {
            StatusItemViewData.from(
                pachliAccount = pachliAccount,
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
        pachliAccountId = pachliAccount.entity.id,
        localDomain = pachliAccount.entity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        statusItemViewData = data.status!!.let {
            StatusItemViewData.from(
                pachliAccount = pachliAccount,
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
        pachliAccountId = pachliAccount.entity.id,
        localDomain = pachliAccount.entity.domain,
        notificationId = data.notification.serverId,
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        account = data.account.asModel(),
    )

    NotificationEntity.Type.UPDATE -> UpdateNotificationViewData(
        pachliAccountId = pachliAccount.entity.id,
        localDomain = pachliAccount.entity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        statusItemViewData = data.status!!.let {
            StatusItemViewData.from(
                pachliAccount = pachliAccount,
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
        pachliAccountId = pachliAccount.entity.id,
        localDomain = pachliAccount.entity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        report = data.report!!,
    )

    NotificationEntity.Type.SEVERED_RELATIONSHIPS -> SeveredRelationshipsNotificationViewData(
        pachliAccountId = pachliAccount.entity.id,
        localDomain = pachliAccount.entity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        relationshipSeveranceEvent = data.relationshipSeveranceEvent!!.asModel(),
    )

    NotificationEntity.Type.MODERATION_WARNING -> ModerationWarningNotificationViewData(
        pachliAccountId = pachliAccount.entity.id,
        localDomain = pachliAccount.entity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        accountWarning = data.accountWarning!!.asModel(),
    )

    NotificationEntity.Type.QUOTE -> QuoteNotificationViewData(
        pachliAccountId = pachliAccount.entity.id,
        localDomain = pachliAccount.entity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        statusItemViewData = data.status!!.let {
            StatusItemViewData.from(
                pachliAccount = pachliAccount,
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

    NotificationEntity.Type.QUOTED_UPDATE -> QuotedUpdateNotificationViewData(
        pachliAccountId = pachliAccount.entity.id,
        localDomain = pachliAccount.entity.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
        statusItemViewData = data.status!!.let {
            StatusItemViewData.from(
                pachliAccount = pachliAccount,
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
}
