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

import app.pachli.core.data.CollectionCardViewData
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
import app.pachli.core.model.collection.make

/**
 * Returns a [NotificationViewData] from a [NotificationData] and
 * other information.
 *
 * May return null if the [NotificationData.notification.type][NotificationEntity.type]
 * specifies that particular information should be present but is
 * missing (e.g., a [NotificationEntity.Type.MENTION] notification where
 * [NotificationData.status] is null).
 *
 * @param pachliAccount
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
    accountFilterDecision: AccountFilterDecision,
    isAboutSelf: Boolean,
) = when (data.notification.type) {
    NotificationEntity.Type.UNKNOWN -> UnknownNotificationViewData(
        pachliAccountId = pachliAccount.id,
        localDomain = pachliAccount.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision,
    )

    NotificationEntity.Type.COLLECTION_ADD -> NotificationViewData.WithCollection.CollectionAddNotificationViewData(
        pachliAccountId = pachliAccount.id,
        localDomain = pachliAccount.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision,
        collectionCardViewData = CollectionCardViewData(
            timelineCollection = data.timelineCollection!!.asModel(),
            displayAction = data.collectionViewData?.displayAction.make(data.timelineCollection!!.sensitive, showSensitiveMedia),
        ),
    )

    NotificationEntity.Type.COLLECTION_UPDATE -> NotificationViewData.WithCollection.CollectionUpdateNotificationViewData(
        pachliAccountId = pachliAccount.id,
        localDomain = pachliAccount.domain,
        notificationId = data.notification.serverId,
        account = data.account.asModel(),
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision,
        collectionCardViewData = CollectionCardViewData(
            timelineCollection = data.timelineCollection!!.asModel(),
            displayAction = data.collectionViewData?.displayAction.make(data.timelineCollection!!.sensitive, showSensitiveMedia),
        ),
    )

    NotificationEntity.Type.MENTION -> data.status?.let { status ->
        MentionNotificationViewData(
            pachliAccountId = pachliAccount.id,
            localDomain = pachliAccount.domain,
            notificationId = data.notification.serverId,
            account = data.account.asModel(),
            isAboutSelf = isAboutSelf,
            accountFilterDecision = accountFilterDecision,
            statusItemViewData = StatusItemViewData.from(
                pachliAccount = pachliAccount,
                status,
                isExpanded = isExpanded,
                isDetailed = false,
                contentFilterAction = contentFilterAction,
                quoteContentFilterAction = quoteContentFilterAction,
                showSensitiveMedia = showSensitiveMedia,
                filterContext = FilterContext.NOTIFICATIONS,
            ),
        )
    }

    NotificationEntity.Type.REBLOG -> data.status?.let { status ->
        ReblogNotificationViewData(
            pachliAccountId = pachliAccount.id,
            localDomain = pachliAccount.domain,
            notificationId = data.notification.serverId,
            account = data.account.asModel(),
            isAboutSelf = isAboutSelf,
            accountFilterDecision = accountFilterDecision,
            statusItemViewData = StatusItemViewData.from(
                pachliAccount = pachliAccount,
                status,
                isExpanded = isExpanded,
                isDetailed = false,
                contentFilterAction = contentFilterAction,
                quoteContentFilterAction = quoteContentFilterAction,
                showSensitiveMedia = showSensitiveMedia,
                filterContext = FilterContext.NOTIFICATIONS,
            ),
        )
    }

    NotificationEntity.Type.FAVOURITE -> data.status?.let { status ->
        FavouriteNotificationViewData(
            pachliAccountId = pachliAccount.id,
            localDomain = pachliAccount.domain,
            notificationId = data.notification.serverId,
            account = data.account.asModel(),
            isAboutSelf = isAboutSelf,
            accountFilterDecision = accountFilterDecision,
            statusItemViewData = StatusItemViewData.from(
                pachliAccount = pachliAccount,
                status,
                isExpanded = isExpanded,
                isDetailed = false,
                contentFilterAction = contentFilterAction,
                quoteContentFilterAction = quoteContentFilterAction,
                showSensitiveMedia = showSensitiveMedia,
                filterContext = FilterContext.NOTIFICATIONS,
            ),
        )
    }

    NotificationEntity.Type.FOLLOW -> FollowNotificationViewData(
        pachliAccountId = pachliAccount.id,
        localDomain = pachliAccount.domain,
        notificationId = data.notification.serverId,
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision,
        account = data.account.asModel(),
        note = data.notification.note.orEmpty(),
    )

    NotificationEntity.Type.FOLLOW_REQUEST -> FollowRequestNotificationViewData(
        pachliAccountId = pachliAccount.id,
        localDomain = pachliAccount.domain,
        notificationId = data.notification.serverId,
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision,
        account = data.account.asModel(),
        note = data.notification.note.orEmpty(),
    )

    NotificationEntity.Type.POLL -> data.status?.let { status ->
        PollNotificationViewData(
            pachliAccountId = pachliAccount.id,
            localDomain = pachliAccount.domain,
            notificationId = data.notification.serverId,
            account = data.account.asModel(),
            isAboutSelf = isAboutSelf,
            accountFilterDecision = accountFilterDecision,
            statusItemViewData = StatusItemViewData.from(
                pachliAccount = pachliAccount,
                status,
                isExpanded = isExpanded,
                isDetailed = false,
                contentFilterAction = contentFilterAction,
                quoteContentFilterAction = quoteContentFilterAction,
                showSensitiveMedia = showSensitiveMedia,
                filterContext = FilterContext.NOTIFICATIONS,
            ),
        )
    }

    NotificationEntity.Type.STATUS -> data.status?.let { status ->
        StatusNotificationViewData(
            pachliAccountId = pachliAccount.id,
            localDomain = pachliAccount.domain,
            notificationId = data.notification.serverId,
            account = data.account.asModel(),
            isAboutSelf = isAboutSelf,
            accountFilterDecision = accountFilterDecision,
            statusItemViewData = StatusItemViewData.from(
                pachliAccount = pachliAccount,
                status,
                isExpanded = isExpanded,
                isDetailed = false,
                contentFilterAction = contentFilterAction,
                quoteContentFilterAction = quoteContentFilterAction,
                showSensitiveMedia = showSensitiveMedia,
                filterContext = FilterContext.NOTIFICATIONS,
            ),
        )
    }

    NotificationEntity.Type.SIGN_UP -> SignupNotificationViewData(
        pachliAccountId = pachliAccount.id,
        localDomain = pachliAccount.domain,
        notificationId = data.notification.serverId,
        isAboutSelf = isAboutSelf,
        accountFilterDecision = accountFilterDecision,
        account = data.account.asModel(),
    )

    NotificationEntity.Type.UPDATE -> data.status?.let { status ->
        UpdateNotificationViewData(
            pachliAccountId = pachliAccount.id,
            localDomain = pachliAccount.domain,
            notificationId = data.notification.serverId,
            account = data.account.asModel(),
            isAboutSelf = isAboutSelf,
            accountFilterDecision = accountFilterDecision,
            statusItemViewData = StatusItemViewData.from(
                pachliAccount = pachliAccount,
                status,
                isExpanded = isExpanded,
                isDetailed = false,
                contentFilterAction = contentFilterAction,
                quoteContentFilterAction = quoteContentFilterAction,
                showSensitiveMedia = showSensitiveMedia,
                filterContext = FilterContext.NOTIFICATIONS,
            ),
        )
    }

    NotificationEntity.Type.REPORT -> data.notification.report?.let {
        ReportNotificationViewData(
            pachliAccountId = pachliAccount.id,
            localDomain = pachliAccount.domain,
            notificationId = data.notification.serverId,
            account = data.account.asModel(),
            isAboutSelf = isAboutSelf,
            accountFilterDecision = accountFilterDecision,
            report = it.asModel(),
        )
    }

    NotificationEntity.Type.SEVERED_RELATIONSHIPS -> data.notification.relationshipSeveranceEvent?.let {
        SeveredRelationshipsNotificationViewData(
            pachliAccountId = pachliAccount.id,
            localDomain = pachliAccount.domain,
            notificationId = data.notification.serverId,
            account = data.account.asModel(),
            isAboutSelf = isAboutSelf,
            accountFilterDecision = accountFilterDecision,
            relationshipSeveranceEvent = it.asModel(),
        )
    }

    NotificationEntity.Type.MODERATION_WARNING -> data.notification.accountWarning?.let {
        ModerationWarningNotificationViewData(
            pachliAccountId = pachliAccount.id,
            localDomain = pachliAccount.domain,
            notificationId = data.notification.serverId,
            account = data.account.asModel(),
            isAboutSelf = isAboutSelf,
            accountFilterDecision = accountFilterDecision,
            accountWarning = it.asModel(),
        )
    }

    NotificationEntity.Type.QUOTE -> data.status?.let { status ->
        QuoteNotificationViewData(
            pachliAccountId = pachliAccount.id,
            localDomain = pachliAccount.domain,
            notificationId = data.notification.serverId,
            account = data.account.asModel(),
            isAboutSelf = isAboutSelf,
            accountFilterDecision = accountFilterDecision,
            statusItemViewData = StatusItemViewData.from(
                pachliAccount = pachliAccount,
                status,
                isExpanded = isExpanded,
                isDetailed = false,
                contentFilterAction = contentFilterAction,
                quoteContentFilterAction = quoteContentFilterAction,
                showSensitiveMedia = showSensitiveMedia,
                filterContext = FilterContext.NOTIFICATIONS,
            ),
        )
    }

    NotificationEntity.Type.QUOTED_UPDATE -> data.status?.let { status ->
        QuotedUpdateNotificationViewData(
            pachliAccountId = pachliAccount.id,
            localDomain = pachliAccount.domain,
            notificationId = data.notification.serverId,
            account = data.account.asModel(),
            isAboutSelf = isAboutSelf,
            accountFilterDecision = accountFilterDecision,
            statusItemViewData = StatusItemViewData.from(
                pachliAccount = pachliAccount,
                status,
                isExpanded = isExpanded,
                isDetailed = false,
                contentFilterAction = contentFilterAction,
                quoteContentFilterAction = quoteContentFilterAction,
                showSensitiveMedia = showSensitiveMedia,
                filterContext = FilterContext.NOTIFICATIONS,
            ),
        )
    }
}
