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
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.model.NotificationViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.TimelineAccount
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.ui.LinkListener
import app.pachli.core.ui.emojify
import app.pachli.core.ui.extensions.handleContentDescription
import app.pachli.core.ui.loadAvatar
import app.pachli.core.ui.setClickableText
import app.pachli.databinding.ItemFollowBinding
import com.bumptech.glide.RequestManager

class FollowViewHolder(
    private val binding: ItemFollowBinding,
    private val glide: RequestManager,
    private val linkListener: LinkListener,
) : NotificationsPagingAdapter.ViewHolder<NotificationViewData>, RecyclerView.ViewHolder(binding.root) {
    private val avatarRadius42dp = itemView.context.resources.getDimensionPixelSize(
        DR.dimen.avatar_radius_42dp,
    )

    override fun bind(
        viewData: NotificationViewData,
        payloads: List<List<Any?>>?,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        // Skip updates with payloads. That indicates a timestamp update, and
        // this view does not have timestamps.
        if (!payloads.isNullOrEmpty()) return

        setMessage(
            viewData.account,
            viewData is NotificationViewData.SignupNotificationViewData,
            statusDisplayOptions.animateAvatars,
            statusDisplayOptions.animateEmojis,
        )
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
        val wholeMessage = SpannableStringBuilder(String.format(format, wrappedDisplayName))
        val displayNameIndex = format.indexOf("%s")
        wholeMessage.setSpan(
            StyleSpan(Typeface.BOLD),
            displayNameIndex,
            displayNameIndex + wrappedDisplayName.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        val emojifiedMessage =
            wholeMessage.emojify(
                glide,
                account.emojis,
                binding.notificationText,
                animateEmojis,
            )
        binding.notificationText.text = emojifiedMessage
        val username = context.getString(DR.string.post_username_format, account.username)
        binding.notificationUsername.text = username
        binding.notificationUsername.contentDescription = account.handleContentDescription(context)
        loadAvatar(
            glide,
            account.avatar,
            binding.notificationAvatar,
            avatarRadius42dp,
            animateAvatars,
        )
        binding.accountPronouns.text = account.pronouns

        binding.roleChipGroup.setRoles(account.roles)

        val emojifiedNote = account.note.parseAsMastodonHtml().emojify(
            glide,
            account.emojis,
            binding.notificationAccountNote,
            animateEmojis,
        )
        setClickableText(binding.notificationAccountNote, emojifiedNote, emptyList(), null, linkListener)
        binding.notificationAccountNote.setOnClickListener { linkListener.onViewAccount(account.id) }
        itemView.setOnClickListener { linkListener.onViewAccount(account.id) }
    }
}
