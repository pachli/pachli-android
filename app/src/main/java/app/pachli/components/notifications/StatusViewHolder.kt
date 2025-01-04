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

import app.pachli.adapter.FilterableStatusViewHolder
import app.pachli.adapter.StatusViewHolder
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.database.model.NotificationEntity
import app.pachli.databinding.ItemStatusBinding
import app.pachli.databinding.ItemStatusWrapperBinding
import app.pachli.interfaces.StatusActionListener
import app.pachli.viewdata.NotificationViewData

internal class StatusViewHolder(
    binding: ItemStatusBinding,
    private val statusActionListener: StatusActionListener<NotificationViewData>,
) : NotificationsPagingAdapter.ViewHolder, StatusViewHolder<NotificationViewData>(binding) {

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
                viewData.pachliAccountId,
                viewData,
                statusActionListener,
                statusDisplayOptions,
                payloads?.firstOrNull(),
            )
        }
        if (viewData.type == NotificationEntity.Type.POLL) {
            setPollInfo(viewData.isAboutSelf)
        } else {
            hideStatusInfo()
        }
    }
}

class FilterableStatusViewHolder(
    binding: ItemStatusWrapperBinding,
    private val statusActionListener: StatusActionListener<NotificationViewData>,
) : NotificationsPagingAdapter.ViewHolder, FilterableStatusViewHolder<NotificationViewData>(binding) {
    // Note: Identical to bind() in StatusViewHolder above
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
                viewData.pachliAccountId,
                viewData,
                statusActionListener,
                statusDisplayOptions,
                payloads?.firstOrNull(),
            )
        }
        if (viewData.type == NotificationEntity.Type.POLL) {
            setPollInfo(viewData.isAboutSelf)
        } else {
            hideStatusInfo()
        }
    }
}
