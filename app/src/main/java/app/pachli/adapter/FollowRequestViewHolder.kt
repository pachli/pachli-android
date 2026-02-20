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
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.model.NotificationViewData.FollowRequestNotificationViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.TimelineAccount
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.preferences.PronounDisplay
import app.pachli.core.ui.LinkListener
import app.pachli.core.ui.emojify
import app.pachli.core.ui.loadAvatar
import app.pachli.core.ui.setClickableText
import app.pachli.databinding.ItemFollowRequestBinding
import app.pachli.interfaces.AccountActionListener
import com.bumptech.glide.RequestManager

class FollowRequestViewHolder(
    private val binding: ItemFollowRequestBinding,
    private val glide: RequestManager,
    private val accountActionListener: AccountActionListener,
    private val linkListener: LinkListener,
    private val showHeader: Boolean,
) : NotificationsPagingAdapter.ViewHolder<FollowRequestNotificationViewData>, RecyclerView.ViewHolder(binding.root) {

    override fun bind(
        viewData: FollowRequestNotificationViewData,
        payloads: List<List<Any?>>?,
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
            when (statusDisplayOptions.pronounDisplay) {
                PronounDisplay.EVERYWHERE -> true
                PronounDisplay.WHEN_COMPOSING,
                PronounDisplay.HIDE,
                -> false
            },
        )

        setupActionListener(accountActionListener, viewData.account.id)
    }

    fun setupWithAccount(
        account: TimelineAccount,
        animateAvatar: Boolean,
        animateEmojis: Boolean,
        showBotOverlay: Boolean,
        showPronouns: Boolean,
    ) {
        val wrappedName = account.name.unicodeWrap()
        val emojifiedName: CharSequence = wrappedName.emojify(
            glide,
            account.emojis,
            binding.displayNameTextView,
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
            }.emojify(glide, account.emojis, binding.notificationTextView, animateEmojis)
        }
        binding.notificationTextView.visible(showHeader)

        if (showPronouns) binding.accountPronouns.text = account.pronouns
        binding.accountPronouns.visible(showPronouns && account.pronouns?.isBlank() == false)

        val formattedUsername = itemView.context.getString(DR.string.post_username_format, account.username)
        binding.usernameTextView.text = formattedUsername
        if (account.note.isEmpty()) {
            binding.accountNote.hide()
        } else {
            binding.accountNote.show()

            val emojifiedNote = account.note.parseAsMastodonHtml()
                .emojify(glide, account.emojis, binding.accountNote, animateEmojis)
            setClickableText(binding.accountNote, emojifiedNote)
        }
        val avatarRadius = binding.avatar.context.resources.getDimensionPixelSize(DR.dimen.avatar_radius_48dp)
        loadAvatar(glide, account.avatar, binding.avatar, avatarRadius, animateAvatar)
        binding.avatarBadge.visible(showBotOverlay && account.bot)

        binding.roleChipGroup.setRoles(account.roles)
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
