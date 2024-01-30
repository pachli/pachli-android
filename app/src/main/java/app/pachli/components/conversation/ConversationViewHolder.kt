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
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import app.pachli.R
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.core.activity.loadAvatar
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.database.model.ConversationAccountEntity
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.SmartLengthInputFilter
import app.pachli.util.StatusDisplayOptions

class ConversationViewHolder internal constructor(
    itemView: View,
    private val statusDisplayOptions: StatusDisplayOptions,
    private val listener: StatusActionListener<ConversationViewData>,
) : StatusBaseViewHolder<ConversationViewData>(itemView) {
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
        viewData: ConversationViewData,
        payloads: Any?,
    ) {
        val (_, _, account, inReplyToId, _, _, _, _, _, _, _, _, _, _, favourited, bookmarked, sensitive, _, _, attachments) = viewData.status
        if (payloads == null) {
            setupCollapsedState(viewData, listener)
            setDisplayName(account.name, account.emojis, statusDisplayOptions)
            setUsername(account.username)
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
            setAvatars(viewData.accounts)
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

    private fun setConversationName(accounts: List<ConversationAccountEntity>) {
        conversationNameTextView.text = when (accounts.size) {
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

    private fun setAvatars(accounts: List<ConversationAccountEntity>) {
        avatars.withIndex().forEach { views ->
            accounts.getOrNull(views.index)?.also { account ->
                loadAvatar(
                    account.avatar,
                    views.value,
                    avatarRadius48dp,
                    statusDisplayOptions.animateAvatars,
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
            contentCollapseButton.setOnClickListener {
                listener.onContentCollapsedChange(viewData, !viewData.isCollapsed)
            }
            contentCollapseButton.show()
            if (viewData.isCollapsed) {
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
