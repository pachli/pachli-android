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
import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.ui.SetStatusContent
import app.pachli.core.ui.StatusActionListener
import app.pachli.databinding.ItemStatusBinding
import app.pachli.databinding.ItemStatusWrapperBinding
import com.bumptech.glide.RequestManager

/**
 * Displays any notification of type
 * [NotificationViewData.WithStatus][app.pachli.core.data.model.NotificationViewData.WithStatus].
 */
internal class StatusViewHolder(
    binding: ItemStatusBinding,
    glide: RequestManager,
    setStatusContent: SetStatusContent,
    private val statusActionListener: NotificationActionListener,
) : NotificationsPagingAdapter.ViewHolder<WithStatus>, StatusViewHolder<WithStatus, IStatusViewData>(binding, glide, setStatusContent) {

    override fun bind(
        viewData: WithStatus,
        payloads: List<List<Any?>>?,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        if (payloads.isNullOrEmpty()) {
            showStatusContent(true)
        }
        setupWithStatus(
            viewData,
            statusActionListener,
            statusDisplayOptions,
            payloads,
        )
        if (viewData is WithStatus.PollNotificationViewData) {
            setPollInfo(viewData.isAboutSelf)
        } else {
            hideStatusInfo()
        }
    }
}

class FilterableStatusViewHolder(
    binding: ItemStatusWrapperBinding,
    glide: RequestManager,
    setStatusContent: SetStatusContent,
    private val statusActionListener: StatusActionListener<IStatusViewData>,
) : NotificationsPagingAdapter.ViewHolder<WithStatus>, FilterableStatusViewHolder<WithStatus, IStatusViewData>(binding, glide, setStatusContent) {
    // Note: Identical to bind() in StatusViewHolder above
    override fun bind(
        viewData: WithStatus,
        payloads: List<List<Any?>>?,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        if (payloads.isNullOrEmpty()) {
            showStatusContent(true)
        }
        setupWithStatus(
            viewData,
            statusActionListener,
            statusDisplayOptions,
            payloads,
        )
        if (viewData is WithStatus.PollNotificationViewData) {
            setPollInfo(viewData.isAboutSelf)
        } else {
            hideStatusInfo()
        }
    }
}
