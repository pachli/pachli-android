/* Copyright 2017 Andrew Dawson
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

package app.pachli.components.conversation

import android.text.InputFilter
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import app.pachli.R
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.core.activity.loadAvatar
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.util.SmartLengthInputFilter
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.database.model.ConversationAccount
import app.pachli.databinding.ItemConversationBinding
import app.pachli.interfaces.StatusActionListener
import timber.log.Timber

class ConversationViewHolder internal constructor(
    private val binding: ItemConversationBinding,
    private val listener: StatusActionListener<ConversationViewData>,
) : ConversationAdapter.ViewHolder, StatusBaseViewHolder<ConversationViewData>(binding.root) {
    private val avatars: Array<ImageView> = arrayOf(
        avatar,
        itemView.findViewById(R.id.status_avatar_1),
        itemView.findViewById(R.id.status_avatar_2),
    )

    override fun bind(viewData: ConversationViewData, payloads: List<*>?, statusDisplayOptions: StatusDisplayOptions) {
        Timber.d("binding: $viewData")
        val (_, _, account, inReplyToId, _, _, _, _, _, _, _, _, _, _, favourited, bookmarked, sensitive, _, _, attachments) = viewData.status
        Timber.d("  payloads: $payloads")
        if (payloads.isNullOrEmpty()) {// == null) {
            setupCollapsedState(viewData, listener)
            setDisplayName(account.name, account.emojis, statusDisplayOptions)
//            setUsername(account.username)
            if (viewData.isConversationStarter) {
                setUsername("!! Initial")
            } else {
                setUsername(account.username)
            }
            setMetaData(viewData, statusDisplayOptions, listener)
            setIsReply(inReplyToId != null)
            setFavourited(favourited)
            setBookmarked(bookmarked)
            if (statusDisplayOptions.mediaPreviewEnabled && hasPreviewableAttachment(attachments)) {
                setMediaPreviews(
                    viewData,
                    attachments,
                    sensitive,
                    listener,
                    viewData.isShowingContent,
                    statusDisplayOptions.useBlurhash,
                )
                if (attachments.isEmpty()) {
                    hideSensitiveMediaWarning()
                }
                // Hide the unused label.
                for (mediaLabel in mediaLabels) {
                    mediaLabel.visibility = View.GONE
                }
            } else {
                setMediaLabel(viewData, attachments, sensitive, listener, viewData.isShowingContent)
                // Hide all unused views.
                mediaPreview.visibility = View.GONE
                hideSensitiveMediaWarning()
            }
            setupButtons(
                viewData,
                listener,
                account.id,
                statusDisplayOptions,
            )
            setSpoilerAndContent(viewData, statusDisplayOptions, listener)
            setConversationName(viewData.accounts)
            setAvatars(viewData.accounts, statusDisplayOptions.animateAvatars)
        } else {
            if (payloads is List<*>) {
                for (item in payloads) {
                    if (Key.KEY_CREATED == item) {
                        setMetaData(viewData, statusDisplayOptions, listener)
                    }
                }
            }
        }
    }

    private fun setConversationName(accounts: List<ConversationAccount>) {
        binding.conversationName.text = when (accounts.size) {
            0 -> context.getString(R.string.conversation_0_recipients)
            1 -> context.getString(
                R.string.conversation_1_recipients,
                accounts[0].username,
            )
            2 -> context.getString(
                R.string.conversation_2_recipients,
                accounts[0].username,
                accounts[1].username,
            )
            else -> context.getString(
                R.string.conversation_more_recipients,
                accounts[0].username,
                accounts[1].username,
                accounts.size - 2,
            )
        }
    }

    private fun setAvatars(accounts: List<ConversationAccount>, animateAvatars: Boolean) {
        avatars.withIndex().forEach { views ->
            accounts.getOrNull(views.index)?.also { account ->
                loadAvatar(
                    account.avatar,
                    views.value,
                    avatarRadius48dp,
                    animateAvatars,
                )
                views.value.show()
            } ?: views.value.hide()
        }
    }

    private fun setupCollapsedState(
        viewData: ConversationViewData,
        listener: StatusActionListener<ConversationViewData>,
    ) {
        /* input filter for TextViews have to be set before text */
        if (viewData.isCollapsible && (viewData.isExpanded || TextUtils.isEmpty(viewData.spoilerText))) {
            binding.buttonToggleContent.setOnClickListener {
                listener.onContentCollapsedChange(viewData, !viewData.isCollapsed)
            }
            binding.buttonToggleContent.show()
            if (viewData.isCollapsed) {
                binding.buttonToggleContent.setText(R.string.post_content_warning_show_more)
                content.filters = COLLAPSE_INPUT_FILTER
            } else {
                binding.buttonToggleContent.setText(R.string.post_content_warning_show_less)
                content.filters = NO_INPUT_FILTER
            }
        } else {
            binding.buttonToggleContent.visibility = View.GONE
            content.filters = NO_INPUT_FILTER
        }
    }

    companion object {
        private val COLLAPSE_INPUT_FILTER = arrayOf<InputFilter>(SmartLengthInputFilter)
        private val NO_INPUT_FILTER = arrayOfNulls<InputFilter>(0)
    }
}
