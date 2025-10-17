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
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.model.NotificationViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.database.model.NotificationEntity
import app.pachli.core.ui.SetStatusContent
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
    private val setStatusContent: SetStatusContent,
    private val notificationActionListener: NotificationActionListener,
) : NotificationsPagingAdapter.ViewHolder, RecyclerView.ViewHolder(binding.root) {
    override fun bind(
        viewData: NotificationViewData,
        payloads: List<List<Any?>>?,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        val statusViewData = viewData.statusViewData
        if (payloads.isNullOrEmpty()) {
            // Hide null statuses. Shouldn't happen according to the spec, but some servers
            // have been seen to do this (https://github.com/tuskyapp/Tusky/issues/2252)
            if (statusViewData == null) {
                itemView.hide()
                return
            } else {
                binding.statusView.setupWithStatus(
                    setStatusContent,
                    glide,
                    viewData,
                    notificationActionListener,
                    statusDisplayOptions,
                )
                itemView.show()
            }
            setMessage(viewData, statusDisplayOptions)
        } else {
            payloads.flatten().forEach { item ->
                if (item == StatusViewDataDiffCallback.Payload.CREATED == item && statusViewData != null) {
                    binding.statusView.setMetaData(viewData, statusDisplayOptions, notificationActionListener)
                }
            }
        }
    }

    fun setMessage(
        viewData: NotificationViewData,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        val displayName = viewData.account.name.unicodeWrap()
        val type = viewData.type
        val context = binding.notificationTopText.context
        val icon = type.icon(context)
        val format = when (type) {
            NotificationEntity.Type.FAVOURITE -> context.getString(R.string.notification_favourite_format)
            NotificationEntity.Type.REBLOG -> context.getString(R.string.notification_reblog_format)
            NotificationEntity.Type.STATUS -> context.getString(R.string.notification_subscription_format)
            NotificationEntity.Type.UPDATE -> context.getString(R.string.notification_update_format)
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
