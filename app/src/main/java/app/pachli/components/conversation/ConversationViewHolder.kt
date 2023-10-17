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
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */
package app.pachli.components.conversation

import android.text.InputFilter
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.SmartLengthInputFilter
import app.pachli.util.StatusDisplayOptions
import app.pachli.util.loadAvatar

class ConversationViewHolder internal constructor(
    itemView: View,
    private val statusDisplayOptions: StatusDisplayOptions,
    private val listener: StatusActionListener,
) : StatusBaseViewHolder(itemView) {
    private val conversationNameTextView: TextView
    private val contentCollapseButton: Button
    private val avatars: Array<ImageView>

    init {
        conversationNameTextView = itemView.findViewById(R.id.conversation_name)
        contentCollapseButton = itemView.findViewById(R.id.button_toggle_content)
        avatars = arrayOf(
            avatar,
            itemView.findViewById(R.id.status_avatar_1),
            itemView.findViewById(R.id.status_avatar_2),
        )
    }

    fun setupWithConversation(
        conversation: ConversationViewData,
        payloads: Any?,
    ) {
        val statusViewData = conversation.lastStatus
        val (_, _, account, inReplyToId, _, _, _, _, _, _, _, _, _, _, favourited, bookmarked, sensitive, _, _, attachments) = statusViewData.status
        if (payloads == null) {
            setupCollapsedState(
                statusViewData.isCollapsible,
                statusViewData.isCollapsed,
                statusViewData.isExpanded,
                statusViewData.spoilerText,
                listener,
            )
            setDisplayName(account.name, account.emojis, statusDisplayOptions)
            setUsername(account.username)
            setMetaData(statusViewData, statusDisplayOptions, listener)
            setIsReply(inReplyToId != null)
            setFavourited(favourited)
            setBookmarked(bookmarked)
            if (statusDisplayOptions.mediaPreviewEnabled && hasPreviewableAttachment(attachments)) {
                setMediaPreviews(
                    attachments,
                    sensitive,
                    listener,
                    statusViewData.isShowingContent,
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
                setMediaLabel(attachments, sensitive, listener, statusViewData.isShowingContent)
                // Hide all unused views.
                mediaPreview.visibility = View.GONE
                hideSensitiveMediaWarning()
            }
            setupButtons(
                listener,
                account.id,
                statusDisplayOptions,
            )
            setSpoilerAndContent(statusViewData, statusDisplayOptions, listener)
            setConversationName(conversation.accounts)
            setAvatars(conversation.accounts)
        } else {
            if (payloads is List<*>) {
                for (item in payloads) {
                    if (Key.KEY_CREATED == item) {
                        setMetaData(statusViewData, statusDisplayOptions, listener)
                    }
                }
            }
        }
    }

    private fun setConversationName(accounts: List<ConversationAccountEntity>) {
        val context = conversationNameTextView.context
        var conversationName = ""
        if (accounts.size == 1) {
            conversationName =
                context.getString(R.string.conversation_1_recipients, accounts[0].username)
        } else if (accounts.size == 2) {
            conversationName = context.getString(
                R.string.conversation_2_recipients,
                accounts[0].username,
                accounts[1].username,
            )
        } else if (accounts.size > 2) {
            conversationName = context.getString(
                R.string.conversation_more_recipients,
                accounts[0].username,
                accounts[1].username,
                accounts.size - 2,
            )
        }
        conversationNameTextView.text = conversationName
    }

    private fun setAvatars(accounts: List<ConversationAccountEntity>) {
        for (i in avatars.indices) {
            val avatarView = avatars[i]
            if (i < accounts.size) {
                loadAvatar(
                    accounts[i].avatar,
                    avatarView,
                    avatarRadius48dp,
                    statusDisplayOptions.animateAvatars,
                    null,
                )
                avatarView.visibility = View.VISIBLE
            } else {
                avatarView.visibility = View.GONE
            }
        }
    }

    private fun setupCollapsedState(
        collapsible: Boolean,
        collapsed: Boolean,
        expanded: Boolean,
        spoilerText: String,
        listener: StatusActionListener,
    ) {
        /* input filter for TextViews have to be set before text */
        if (collapsible && (expanded || TextUtils.isEmpty(spoilerText))) {
            contentCollapseButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onContentCollapsedChange(
                        !collapsed,
                        position,
                    )
                }
            }
            contentCollapseButton.visibility = View.VISIBLE
            if (collapsed) {
                contentCollapseButton.setText(R.string.post_content_warning_show_more)
                content.filters = COLLAPSE_INPUT_FILTER
            } else {
                contentCollapseButton.setText(R.string.post_content_warning_show_less)
                content.filters = NO_INPUT_FILTER
            }
        } else {
            contentCollapseButton.visibility = View.GONE
            content.filters = NO_INPUT_FILTER
        }
    }

    companion object {
        private val COLLAPSE_INPUT_FILTER = arrayOf<InputFilter>(SmartLengthInputFilter)
        private val NO_INPUT_FILTER = arrayOfNulls<InputFilter>(0)
    }
}
