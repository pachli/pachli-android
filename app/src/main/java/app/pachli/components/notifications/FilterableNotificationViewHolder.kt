/*
 * Copyright 2024 Pachli Association
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

import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.database.model.NotificationType
import app.pachli.databinding.ItemNotificationFilteredBinding
import app.pachli.viewdata.NotificationViewData

/**
 * Viewholder for a notification that has been filtered to "warn".
 *
 * Displays:
 *
 * - The notification type and icon.
 * - The domain the notification is from.
 * - The reason the notification is filtered.
 * - Buttons to edit the filter or show the notification.
 */
class FilterableNotificationViewHolder(
    private val binding: ItemNotificationFilteredBinding,
    private val localDomain: String,
    private val notificationActionListener: NotificationActionListener,
) : NotificationsPagingAdapter.ViewHolder, RecyclerView.ViewHolder(binding.root) {
    private val context = binding.root.context

    lateinit var viewData: NotificationViewData

    private val notFollowing = HtmlCompat.fromHtml(
        context.getString(R.string.account_filter_placeholder_label_not_following),
        HtmlCompat.FROM_HTML_MODE_LEGACY,
    )

    private val younger30d = HtmlCompat.fromHtml(
        context.getString(R.string.account_filter_placeholder_label_younger_30d),
        HtmlCompat.FROM_HTML_MODE_LEGACY,
    )

    private val limitedByServer = HtmlCompat.fromHtml(
        context.getString(R.string.account_filter_placeholder_label_limited_by_server),
        HtmlCompat.FROM_HTML_MODE_LEGACY,
    )

    init {
        binding.accountFilterShowAnyway.setOnClickListener {
            notificationActionListener.clearAccountFilter(viewData)
        }

        binding.accountFilterEditFilter.setOnClickListener {
            notificationActionListener.editAccountNotificationFilter()
        }
    }

    override fun bind(pachliAccountId: Long, viewData: NotificationViewData, payloads: List<*>?, statusDisplayOptions: StatusDisplayOptions) {
        this.viewData = viewData

        val icon = viewData.type.icon(context)

        // Labels for different notification types filtered by account.
        val label = when (viewData.type) {
            NotificationType.MENTION -> R.string.account_filter_placeholder_type_mention_fmt
            NotificationType.REBLOG -> R.string.account_filter_placeholder_type_reblog_fmt
            NotificationType.FAVOURITE -> R.string.account_filter_placeholder_type_favourite_fmt
            NotificationType.FOLLOW -> R.string.account_filter_placeholder_type_follow_fmt
            NotificationType.FOLLOW_REQUEST -> R.string.account_filter_placeholder_type_follow_request_fmt
            else -> R.string.account_filter_placeholder_label_domain
        }

        binding.accountFilterDomain.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
        binding.accountFilterDomain.text = HtmlCompat.fromHtml(
            context.getString(
                label,
                viewData.account.domain.ifEmpty { localDomain },
            ),
            HtmlCompat.FROM_HTML_MODE_LEGACY,
        )

        val reason = when (viewData.accountFilterDecision?.reason) {
            AccountFilterReason.NOT_FOLLOWING -> notFollowing
            AccountFilterReason.YOUNGER_30D -> younger30d
            AccountFilterReason.LIMITED_BY_SERVER -> limitedByServer
            null -> ""
        }
        binding.accountFilterReason.text = reason
    }
}
