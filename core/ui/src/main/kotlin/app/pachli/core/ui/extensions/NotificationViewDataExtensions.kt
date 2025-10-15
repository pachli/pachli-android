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
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.NotificationData
import app.pachli.core.database.model.asModel
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
    accountFilterDecision: AccountFilterDecision?,
    isAboutSelf: Boolean,
) = NotificationViewData(
    pachliAccountId = pachliAccountEntity.id,
    localDomain = pachliAccountEntity.domain,
    type = data.notification.type,
    id = data.notification.serverId,
    account = data.account.asModel(),
    statusViewData = data.status?.let {
        StatusViewData.from(
            pachliAccountId = pachliAccountEntity.id,
            it,
            isExpanded = isExpanded,
            isDetailed = false,
            contentFilterAction = contentFilterAction,
            attachmentDisplayAction = it.getAttachmentDisplayAction(
                FilterContext.NOTIFICATIONS,
                showSensitiveMedia,
                it.viewData?.attachmentDisplayAction,
            ),
        )
    },
    report = data.report,
    relationshipSeveranceEvent = data.relationshipSeveranceEvent?.asModel(),
    isAboutSelf = isAboutSelf,
    accountFilterDecision = accountFilterDecision ?: AccountFilterDecision.None,
    accountWarning = data.accountWarning?.asModel(),
)
