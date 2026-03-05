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
import android.widget.TextView
import androidx.core.util.TypedValueCompat.dpToPx
import app.pachli.R
import app.pachli.adapter.StatusViewDataDiffCallback
import app.pachli.adapter.StatusViewHolder
import app.pachli.core.common.extensions.show
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.model.NotificationViewData.WithStatus
import app.pachli.core.data.model.NotificationViewData.WithStatus.FavouriteNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.MentionNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.QuoteNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.QuotedUpdateNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.ReblogNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.StatusNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.UpdateNotificationViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.ui.SetContent
import app.pachli.core.ui.StatusActionListener
import app.pachli.core.ui.emojify
import app.pachli.databinding.ItemStatusBinding
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
    private val binding: ItemStatusBinding,
    glide: RequestManager,
    setContent: SetContent,
    private val notificationActionListener: NotificationActionListener,
) : NotificationsPagingAdapter.ViewHolder<WithStatus>, StatusViewHolder<WithStatus>(binding, glide, setContent) {
    val compoundDrawablePadding = dpToPx(10f, context.resources.displayMetrics).toInt()
    val relativePadding = dpToPx(28f, context.resources.displayMetrics).toInt()

    override fun bind(
        viewData: WithStatus,
        payloads: List<List<Any?>>?,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        if (payloads.isNullOrEmpty()) {
            binding.statusInfo.setOnClickListener {
                notificationActionListener.onViewAccount(viewData.account.id)
            }
            showStatusContent(true)
        } else {
            payloads.flatten().forEach { item ->
                if (item == StatusViewDataDiffCallback.Payload.CREATED) {
                    binding.statusView.setMetaData(viewData, statusDisplayOptions, notificationActionListener)
                }
            }
        }
        setupWithStatus(
            viewData,
            notificationActionListener,
            statusDisplayOptions,
            payloads,
        )

        val statusContentDescription = binding.statusView.getContentDescription(viewData, statusDisplayOptions)

        val contentDescriptionPrefix = binding.statusInfo.text

        binding.root.contentDescription = "$contentDescriptionPrefix.\n\n$statusContentDescription"
    }

    override fun setStatusInfo(
        statusInfo: TextView,
        viewData: WithStatus,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener,
    ) {
        // The poll notification info doesn't interpolate an account name, so handle
        // this separately.
        if (viewData is WithStatus.PollNotificationViewData) {
            statusInfo.setText(if (viewData.isAboutSelf) R.string.poll_ended_created else R.string.poll_ended_voted)
            statusInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_poll_24dp, 0, 0, 0)
            statusInfo.compoundDrawablePadding = compoundDrawablePadding
            statusInfo.setPaddingRelative(relativePadding, 0, 0, 0)
            statusInfo.show()
            return
        }

        val displayName = viewData.account.name.unicodeWrap()
        val icon = viewData.icon(context)
        val format = when (viewData) {
            is FavouriteNotificationViewData -> context.getString(R.string.notification_favourite_format)
            is ReblogNotificationViewData -> context.getString(R.string.notification_reblog_format)
            is StatusNotificationViewData -> context.getString(R.string.notification_subscription_format)
            is UpdateNotificationViewData -> context.getString(R.string.notification_update_format)
            is QuoteNotificationViewData -> context.getString(R.string.notification_quote_format)
            is QuotedUpdateNotificationViewData -> context.getString(R.string.notification_quoted_update_format)
            is MentionNotificationViewData -> context.getString(R.string.notification_mention_format)
        }

        statusInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        statusInfo.compoundDrawablePadding = compoundDrawablePadding
        statusInfo.setPaddingRelative(relativePadding, 0, 0, 0)

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
            statusInfo,
            statusDisplayOptions.animateEmojis,
        )
        statusInfo.text = emojifiedText
    }
}
