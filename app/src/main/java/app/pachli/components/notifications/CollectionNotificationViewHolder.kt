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

import android.graphics.drawable.Drawable
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.Px
import androidx.core.text.HtmlCompat
import androidx.core.text.htmlEncode
import androidx.core.util.TypedValueCompat.dpToPx
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.model.NotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithCollection.CollectionAddNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithCollection.CollectionUpdateNotificationViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.designsystem.R as DR
import app.pachli.core.ui.CollectionCardActionListener
import app.pachli.core.ui.LinkListener
import app.pachli.core.ui.emojify
import app.pachli.core.ui.loadAvatar
import app.pachli.databinding.ItemNotificationCollectionBinding
import com.bumptech.glide.RequestManager
import kotlin.math.roundToInt

/**
 * Displays any [NotificationViewData.WithCollection].
 *
 * Shows the notification creator, and the collection card.
 */
class CollectionNotificationViewHolder(
    private val binding: ItemNotificationCollectionBinding,
    private val glide: RequestManager,
    private val linkListener: LinkListener,
    private val collectionListener: CollectionCardActionListener,
) : NotificationsPagingAdapter.ViewHolder<NotificationViewData.WithCollection>, RecyclerView.ViewHolder(binding.root) {
    private val avatarRadius48dp = itemView.context.resources.getDimensionPixelSize(
        DR.dimen.avatar_radius_48dp,
    )

    /**
     * The start padding (pixels) necessary to align the start of text in
     * [ItemNotificationCollectionBinding.notificationText] with the start of text in
     * [ItemNotificationCollectionBinding.collectionCard].
     *
     * See [setStatusInfoDrawableRes].
     */
    @Px
    private val defaultNotificationInfoPaddingStart: Int = dpToPx(56f, binding.root.context.resources.displayMetrics).roundToInt()

    override fun bind(
        viewData: NotificationViewData.WithCollection,
        payloads: List<Any?>,
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

        val emojifiedMessage = HtmlCompat.fromHtml(msg, HtmlCompat.FROM_HTML_MODE_LEGACY).emojify(
            glide,
            account.emojis,
            binding.notificationText,
            statusDisplayOptions.animateEmojis,
        )
        binding.notificationText.text = emojifiedMessage

        val iconResource = when (viewData) {
            is CollectionAddNotificationViewData -> app.pachli.core.ui.R.drawable.ic_group_add_24
            is CollectionUpdateNotificationViewData -> app.pachli.core.ui.R.drawable.ic_groups_24
        }

        setNotificationTextDrawableRes(iconResource, binding.notificationText)

        loadAvatar(
            glide,
            account.avatar,
            binding.notificationAvatar,
            avatarRadius48dp,
            statusDisplayOptions.animateAvatars,
        )

        binding.collectionCard.bind(
            glide,
            viewData.collectionCardViewData,
            statusDisplayOptions,
            showOwner = false,
            listener = collectionListener,
        )

        binding.root.contentDescription = buildString {
            append(HtmlCompat.fromHtml(msg, HtmlCompat.FROM_HTML_MODE_LEGACY))
            append("\n")
            append(binding.collectionCard.contentDescription)
        }

        binding.collectionCard.setOnClickListener { collectionListener.onViewCollection(viewData.collectionCardViewData.timelineCollection) }

        itemView.setOnClickListener { linkListener.onViewAccount(account.accountId) }
    }

    /**
     * Sets [drawableRes] as the "start" drawable in [textView], and
     * adjusts the textView's start padding as necessary.*
     */
    // TODO: Identical to setStatusInfoDrawableRes, consider removing the duplication.
    private fun setNotificationTextDrawableRes(@DrawableRes drawableRes: Int, textView: TextView) {
        setNotificationTextDrawable(getDrawableSizedForTextviewLineHeight(drawableRes, textView), textView)
    }

    // TODO: Identical to setStatusInfoDrawable, consider removing the duplication.
    private fun setNotificationTextDrawable(drawable: Drawable?, textView: TextView) {
        textView.setPaddingRelative(
            defaultNotificationInfoPaddingStart - (drawable?.bounds?.width() ?: 0),
            textView.paddingTop,
            textView.paddingEnd,
            textView.paddingBottom,
        )
        textView.setCompoundDrawablesRelative(drawable, null, null, null)
    }
}
