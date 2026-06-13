/*
 * Copyright (c) 2026 Pachli Association
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
import androidx.core.text.htmlEncode
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.model.NotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithCollection.CollectionAddNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithCollection.CollectionUpdateNotificationViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.designsystem.R as DR
import app.pachli.core.preferences.PronounDisplay
import app.pachli.core.ui.LinkListener
import app.pachli.core.ui.SetContent
import app.pachli.core.ui.emojify
import app.pachli.core.ui.extensions.handleContentDescription
import app.pachli.core.ui.loadAvatar
import app.pachli.databinding.ItemNotificationCollectionBinding
import com.bumptech.glide.RequestManager

class CollectionNotificationViewHolder(
    private val binding: ItemNotificationCollectionBinding,
    private val glide: RequestManager,
    private val setContent: SetContent,
    private val linkListener: LinkListener,
) : NotificationsPagingAdapter.ViewHolder<NotificationViewData.WithCollection>, RecyclerView.ViewHolder(binding.root) {
    private val avatarRadius48dp = itemView.context.resources.getDimensionPixelSize(
        DR.dimen.avatar_radius_48dp,
    )

    override fun bind(
        viewData: NotificationViewData.WithCollection,
        payloads: List<List<Any?>>?,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        val account = viewData.account
        val context = binding.notificationText.context
        val displayName = account.name.htmlEncode().unicodeWrap()

        val msg = when (viewData) {
            is CollectionAddNotificationViewData -> {
                context.getString(
                    R.string.notification_collection_add_format,
                    displayName,
                )
            }

            is CollectionUpdateNotificationViewData -> {
                context.getString(R.string.notification_collection_update_format, displayName)
            }
        }

        val wholeMessage = HtmlCompat.fromHtml(msg, HtmlCompat.FROM_HTML_MODE_LEGACY)
        val emojifiedMessage = wholeMessage.emojify(
            glide,
            account.emojis,
            binding.notificationText,
            statusDisplayOptions.animateEmojis,
        )
        binding.notificationText.text = emojifiedMessage

        val username = context.getString(DR.string.post_username_format, account.username)
        binding.notificationUsername.text = username
        binding.notificationUsername.contentDescription = account.handleContentDescription(context)

        loadAvatar(
            glide,
            account.avatar,
            binding.notificationAvatar,
            avatarRadius48dp,
            statusDisplayOptions.animateAvatars,
        )

        val showPronouns = statusDisplayOptions.pronounDisplay == PronounDisplay.EVERYWHERE
        if (showPronouns) binding.accountPronouns.text = account.pronouns
        binding.accountPronouns.visible(showPronouns && account.pronouns?.isBlank() == false)

        binding.roleChipGroup.setRoles(account.roles)

        setContent(
            glide = glide,
            textView = binding.notificationAccountNote,
            content = account.note,
            emojis = account.emojis.orEmpty(),
            animateEmojis = statusDisplayOptions.animateEmojis,
            removeQuoteInline = false,
            linksToUnderline = statusDisplayOptions.linksToUnderline,
            linkListener = linkListener,
        )

        binding.notificationAccountNote.setOnClickListener { linkListener.onViewAccount(account.id) }
        itemView.setOnClickListener { linkListener.onViewAccount(account.id) }

        binding.collectionCard.bind(glide, viewData.collection, null)
    }
}
