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

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.adapter.StatusViewDataDiffCallback
import app.pachli.core.common.extensions.show
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.model.NotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.FavouriteNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.QuoteNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.QuotedUpdateNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.ReblogNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.StatusNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.UpdateNotificationViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.ui.SetContent
import app.pachli.core.ui.emojify
import app.pachli.databinding.ItemStatusNotificationBinding
import com.bumptech.glide.RequestManager

/**
 * View holder for a status with an activity to be notified about (posted, boosted,
 * favourited, or edited, per [NotificationViewKind.from]).
 *
 * Shows a line with the activity, and who initiated the activity. Clicking this should
 * go to the profile page for the initiator.
 *
 * Displays the original status below that. Clicking this should go to the original
 * status in context.
 */
internal class StatusNotificationViewHolder(
    private val binding: ItemStatusNotificationBinding,
    private val glide: RequestManager,
    private val setContent: SetContent,
    private val notificationActionListener: NotificationActionListener,
) : NotificationsPagingAdapter.ViewHolder<NotificationViewData.WithStatus>, RecyclerView.ViewHolder(binding.root) {
    override fun bind(
        viewData: NotificationViewData.WithStatus,
        payloads: List<List<Any?>>?,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        if (payloads.isNullOrEmpty()) {
            binding.notificationTopText.setOnClickListener {
                notificationActionListener.onViewAccount(viewData.account.id)
            }

            binding.statusView.setupWithStatus(
                setContent,
                glide,
                viewData,
                notificationActionListener,
                statusDisplayOptions,
            )
            itemView.show()
            setMessage(viewData, statusDisplayOptions)
        } else {
            payloads.flatten().forEach { item ->
                if (item == StatusViewDataDiffCallback.Payload.CREATED) {
                    binding.statusView.setMetaData(viewData, statusDisplayOptions, notificationActionListener)
                }
            }
        }
    }

    fun setMessage(
        viewData: NotificationViewData.WithStatus,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        val displayName = viewData.account.name.unicodeWrap()
        val context = binding.notificationTopText.context
        val icon = viewData.icon(context)
        val format = when (viewData) {
            is FavouriteNotificationViewData -> context.getString(R.string.notification_favourite_format)
            is ReblogNotificationViewData -> context.getString(R.string.notification_reblog_format)
            is StatusNotificationViewData -> context.getString(R.string.notification_subscription_format)
            is UpdateNotificationViewData -> context.getString(R.string.notification_update_format)
            is QuoteNotificationViewData -> context.getString(R.string.notification_quote_format)
            is QuotedUpdateNotificationViewData -> context.getString(R.string.notification_quoted_update_format)
            else -> context.getString(R.string.notification_favourite_format)
        }
        binding.notificationTopText.setCompoundDrawablesWithIntrinsicBounds(
            icon,
            null,
            null,
            null,
        )
        val wholeMessage = String.format(format, displayName)
        val str = SpannableStringBuilder(wholeMessage)
        val displayNameIndex = format.indexOf("%s")
        str.setSpan(
            StyleSpan(Typeface.BOLD),
            displayNameIndex,
            displayNameIndex + displayName.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        val emojifiedText = str.emojify(
            glide,
            viewData.account.emojis,
            binding.notificationTopText,
            statusDisplayOptions.animateEmojis,
        )
        binding.notificationTopText.text = emojifiedText
    }
}
