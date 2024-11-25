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

import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.core.activity.emojify
import app.pachli.core.activity.loadAvatar
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.database.model.NotificationType
import app.pachli.core.designsystem.R as DR
import app.pachli.core.network.model.TimelineAccount
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.ui.LinkListener
import app.pachli.core.ui.setClickableText
import app.pachli.databinding.ItemFollowBinding
import app.pachli.viewdata.NotificationViewData

class FollowViewHolder(
    private val binding: ItemFollowBinding,
    private val notificationActionListener: NotificationActionListener,
    private val linkListener: LinkListener,
) : NotificationsPagingAdapter.ViewHolder, RecyclerView.ViewHolder(binding.root) {
    private val avatarRadius42dp = itemView.context.resources.getDimensionPixelSize(
        DR.dimen.avatar_radius_42dp,
    )

    override fun bind(
        pachliAccountId: Long,
        viewData: NotificationViewData,
        payloads: List<*>?,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        // Skip updates with payloads. That indicates a timestamp update, and
        // this view does not have timestamps.
        if (!payloads.isNullOrEmpty()) return

        setMessage(
            viewData.account,
            viewData.type === NotificationType.SIGN_UP,
            statusDisplayOptions.animateAvatars,
            statusDisplayOptions.animateEmojis,
        )
        setupButtons(notificationActionListener, viewData.account.id)
    }

    private fun setMessage(
        account: TimelineAccount,
        isSignUp: Boolean,
        animateAvatars: Boolean,
        animateEmojis: Boolean,
    ) {
        val context = binding.notificationText.context
        val format =
            context.getString(
                if (isSignUp) {
                    R.string.notification_sign_up_format
                } else {
                    R.string.notification_follow_format
                },
            )
        val wrappedDisplayName = account.name.unicodeWrap()
        val wholeMessage = String.format(format, wrappedDisplayName)
        val emojifiedMessage =
            wholeMessage.emojify(
                account.emojis,
                binding.notificationText,
                animateEmojis,
            )
        binding.notificationText.text = emojifiedMessage
        val username = context.getString(DR.string.post_username_format, account.username)
        binding.notificationUsername.text = username
        val emojifiedDisplayName = wrappedDisplayName.emojify(
            account.emojis,
            binding.notificationUsername,
            animateEmojis,
        )
        binding.notificationDisplayName.text = emojifiedDisplayName
        loadAvatar(
            account.avatar,
            binding.notificationAvatar,
            avatarRadius42dp,
            animateAvatars,
        )

        val emojifiedNote = account.note.parseAsMastodonHtml().emojify(
            account.emojis,
            binding.notificationAccountNote,
            animateEmojis,
        )
        setClickableText(binding.notificationAccountNote, emojifiedNote, emptyList(), null, linkListener)
    }

    private fun setupButtons(listener: NotificationActionListener, accountId: String) {
        binding.root.setOnClickListener { listener.onViewAccount(accountId) }
    }
}
