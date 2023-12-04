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

import app.pachli.core.network.model.Filter
import app.pachli.core.network.model.Notification
import app.pachli.core.network.model.Report
import app.pachli.core.network.model.TimelineAccount

data class NotificationViewData(
    val type: Notification.Type,
    val id: String,
    val account: TimelineAccount,
    var statusViewData: StatusViewData?,
    val report: Report?,
) {
    companion object {
        fun from(
            notification: Notification,
            isShowingContent: Boolean,
            isExpanded: Boolean,
            isCollapsed: Boolean,
            filterAction: Filter.Action,
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
                    filterAction = filterAction,
                )
            },
            notification.report,
        )
    }
}
