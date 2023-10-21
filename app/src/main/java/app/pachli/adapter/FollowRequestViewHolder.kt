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

package app.pachli.adapter

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.components.notifications.NotificationsPagingAdapter
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.network.model.TimelineAccount
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.databinding.ItemFollowRequestBinding
import app.pachli.interfaces.AccountActionListener
import app.pachli.interfaces.LinkListener
import app.pachli.util.StatusDisplayOptions
import app.pachli.util.emojify
import app.pachli.util.hide
import app.pachli.util.loadAvatar
import app.pachli.util.setClickableText
import app.pachli.util.show
import app.pachli.util.visible
import app.pachli.viewdata.NotificationViewData

class FollowRequestViewHolder(
    private val binding: ItemFollowRequestBinding,
    private val accountActionListener: AccountActionListener,
    private val linkListener: LinkListener,
    private val showHeader: Boolean,
) : NotificationsPagingAdapter.ViewHolder, RecyclerView.ViewHolder(binding.root) {

    override fun bind(
        viewData: NotificationViewData,
        payloads: List<*>?,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        // Skip updates with payloads. That indicates a timestamp update, and
        // this view does not have timestamps.
        if (!payloads.isNullOrEmpty()) return

        setupWithAccount(
            viewData.account,
            statusDisplayOptions.animateAvatars,
            statusDisplayOptions.animateEmojis,
            statusDisplayOptions.showBotOverlay,
        )

        setupActionListener(accountActionListener, viewData.account.id)
    }

    fun setupWithAccount(
        account: TimelineAccount,
        animateAvatar: Boolean,
        animateEmojis: Boolean,
        showBotOverlay: Boolean,
    ) {
        val wrappedName = account.name.unicodeWrap()
        val emojifiedName: CharSequence = wrappedName.emojify(
            account.emojis,
            itemView,
            animateEmojis,
        )
        binding.displayNameTextView.text = emojifiedName
        if (showHeader) {
            val wholeMessage: String = itemView.context.getString(
                R.string.notification_follow_request_format,
                wrappedName,
            )
            binding.notificationTextView.text = SpannableStringBuilder(wholeMessage).apply {
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    wrappedName.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }.emojify(account.emojis, itemView, animateEmojis)
        }
        binding.notificationTextView.visible(showHeader)
        val formattedUsername = itemView.context.getString(R.string.post_username_format, account.username)
        binding.usernameTextView.text = formattedUsername
        if (account.note.isEmpty()) {
            binding.accountNote.hide()
        } else {
            binding.accountNote.show()

            val emojifiedNote = account.note.parseAsMastodonHtml()
                .emojify(account.emojis, binding.accountNote, animateEmojis)
            setClickableText(binding.accountNote, emojifiedNote, emptyList(), null, linkListener)
        }
        val avatarRadius = binding.avatar.context.resources.getDimensionPixelSize(R.dimen.avatar_radius_48dp)
        loadAvatar(account.avatar, binding.avatar, avatarRadius, animateAvatar)
        binding.avatarBadge.visible(showBotOverlay && account.bot)
    }

    fun setupActionListener(listener: AccountActionListener, accountId: String) {
        binding.acceptButton.setOnClickListener {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onRespondToFollowRequest(true, accountId, position)
            }
        }
        binding.rejectButton.setOnClickListener {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onRespondToFollowRequest(false, accountId, position)
            }
        }
        itemView.setOnClickListener { listener.onViewAccount(accountId) }
    }
}
