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

import androidx.core.text.HtmlCompat
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
import app.pachli.core.ui.SetContent
import app.pachli.core.ui.emojify
import app.pachli.core.ui.extensions.handleContentDescription
import app.pachli.core.ui.loadAvatar
import app.pachli.databinding.ItemFollowRequestBinding
import app.pachli.interfaces.AccountActionListener
import com.bumptech.glide.RequestManager

class FollowRequestViewHolder(
    private val binding: ItemFollowRequestBinding,
    private val glide: RequestManager,
    private val setContent: SetContent,
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
        val displayName = account.name.unicodeWrap()
        val emojifiedName: CharSequence = displayName.emojify(
            glide,
            account.emojis,
            binding.displayNameTextView,
            animateEmojis,
        )
        binding.displayNameTextView.text = emojifiedName
        if (showHeader) {
            val wholeMessage = HtmlCompat.fromHtml(
                itemView.context.getString(
                    R.string.notification_follow_request_format,
                    displayName,
                ),
                HtmlCompat.FROM_HTML_MODE_LEGACY,
            )
            val emojifiedMessage = wholeMessage.emojify(
                glide,
                account.emojis,
                binding.notificationTextView,
                animateEmojis,
            )

            binding.notificationTextView.text = emojifiedMessage

            binding.root.contentDescription = "$emojifiedMessage.\n\n${account.handleContentDescription(itemView.context)}.\n\n${account.note.parseAsMastodonHtml()}"
        } else {
            binding.root.contentDescription = "${account.handleContentDescription(itemView.context)}.\n\n${account.note.parseAsMastodonHtml()}"
        }
        binding.notificationTextView.visible(showHeader)

        if (showPronouns) binding.accountPronouns.text = account.pronouns
        binding.accountPronouns.visible(showPronouns && account.pronouns?.isBlank() == false)

        val formattedUsername = itemView.context.getString(DR.string.post_username_format, account.username)
        binding.usernameTextView.text = formattedUsername
        if (account.note.isBlank()) {
            binding.accountNote.hide()
        } else {
            setContent(
                glide = glide,
                textView = binding.accountNote,
                content = account.note,
                emojis = account.emojis.orEmpty(),
                animateEmojis = animateEmojis,
                removeQuoteInline = false,
                linkListener = linkListener,
            )

            binding.accountNote.show()
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
