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

import android.graphics.drawable.Drawable
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.Px
import androidx.core.text.HtmlCompat
import androidx.core.text.htmlEncode
import androidx.core.util.TypedValueCompat.dpToPx
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.model.NotificationViewData
import app.pachli.core.data.model.NotificationViewData.FollowNotificationViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.ITimelineAccount
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.preferences.LinksToUnderline
import app.pachli.core.preferences.PronounDisplay
import app.pachli.core.ui.LinkListener
import app.pachli.core.ui.SetContent
import app.pachli.core.ui.emojify
import app.pachli.core.ui.extensions.handleContentDescription
import app.pachli.core.ui.loadAvatar
import app.pachli.databinding.ItemFollowBinding
import com.bumptech.glide.RequestManager
import kotlin.math.roundToInt

class FollowViewHolder(
    private val binding: ItemFollowBinding,
    private val glide: RequestManager,
    private val setContent: SetContent,
    private val linkListener: LinkListener,
) : NotificationsPagingAdapter.ViewHolder<NotificationViewData>, RecyclerView.ViewHolder(binding.root) {
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
        viewData: NotificationViewData,
        payloads: List<Any?>,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        // Skip updates with payloads. That indicates a timestamp update, and
        // this view does not have timestamps.
        if (payloads.isNotEmpty()) return

        setMessage(
            viewData.account,
            (viewData as? FollowNotificationViewData)?.note.orEmpty(),
            viewData is NotificationViewData.SignupNotificationViewData,
            statusDisplayOptions.animateAvatars,
            statusDisplayOptions.animateEmojis,
            statusDisplayOptions.pronounDisplay == PronounDisplay.EVERYWHERE,
            statusDisplayOptions.linksToUnderline,
        )
    }

    private fun setMessage(
        account: ITimelineAccount,
        note: String,
        isSignUp: Boolean,
        animateAvatars: Boolean,
        animateEmojis: Boolean,
        showPronouns: Boolean,
        linksToUnderline: Set<LinksToUnderline>,
    ) {
        val context = binding.notificationText.context
        val displayName = account.name.htmlEncode().unicodeWrap()
        val msg = context.getString(
            if (isSignUp) {
                R.string.notification_sign_up_format
            } else {
                R.string.notification_follow_format
            },
            displayName,
        )
        val wholeMessage = HtmlCompat.fromHtml(msg, HtmlCompat.FROM_HTML_MODE_LEGACY)
        val emojifiedMessage = wholeMessage.emojify(
            glide,
            account.emojis,
            binding.notificationText,
            animateEmojis,
        )
        binding.notificationText.text = emojifiedMessage

        setNotificationTextDrawableRes(app.pachli.core.ui.R.drawable.ic_person_add_24dp, binding.notificationText)

        val username = context.getString(DR.string.post_username_format, account.username)
        binding.notificationUsername.text = username
        binding.notificationUsername.contentDescription = account.handleContentDescription(context)
        loadAvatar(
            glide,
            account.avatar,
            binding.notificationAvatar,
            avatarRadius48dp,
            animateAvatars,
        )
        if (showPronouns) binding.accountPronouns.text = account.pronouns
        binding.accountPronouns.visible(showPronouns && account.pronouns?.isBlank() == false)

        binding.roleChipGroup.setRoles(account.roles)

        setContent(
            glide = glide,
            textView = binding.notificationAccountNote,
            content = note,
            emojis = account.emojis.orEmpty(),
            animateEmojis = animateEmojis,
            removeQuoteInline = false,
            linksToUnderline = linksToUnderline,
            linkListener = linkListener,
        )

        binding.notificationAccountNote.setOnClickListener { linkListener.onViewAccount(account.serverId) }
        itemView.setOnClickListener { linkListener.onViewAccount(account.serverId) }

        binding.root.contentDescription = "$emojifiedMessage.\n\n${account.handleContentDescription(context)}.\n\n${note.parseAsMastodonHtml()}"
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
