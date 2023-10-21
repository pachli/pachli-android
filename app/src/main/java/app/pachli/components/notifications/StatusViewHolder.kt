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

package app.pachli.components.notifications

import androidx.viewbinding.ViewBinding
import app.pachli.adapter.StatusViewHolder
import app.pachli.core.network.model.Notification
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.StatusDisplayOptions
import app.pachli.viewdata.NotificationViewData

internal class StatusViewHolder(
    binding: ViewBinding,
    private val statusActionListener: StatusActionListener,
    private val accountId: String,
) : NotificationsPagingAdapter.ViewHolder, StatusViewHolder(binding.root) {

    override fun bind(
        viewData: NotificationViewData,
        payloads: List<*>?,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        val statusViewData = viewData.statusViewData
        if (statusViewData == null) {
            // Hide null statuses. Shouldn't happen according to the spec, but some servers
            // have been seen to do this (https://github.com/tuskyapp/Tusky/issues/2252)
            showStatusContent(false)
        } else {
            if (payloads.isNullOrEmpty()) {
                showStatusContent(true)
            }
            setupWithStatus(
                statusViewData,
                statusActionListener,
                statusDisplayOptions,
                payloads?.firstOrNull(),
            )
        }
        if (viewData.type == Notification.Type.POLL) {
            setPollInfo(accountId == viewData.account.id)
        } else {
            hideStatusInfo()
        }
    }
}
