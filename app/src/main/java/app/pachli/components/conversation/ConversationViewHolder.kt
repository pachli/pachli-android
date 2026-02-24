/*
 * Copyright (c) 2025 Pachli Association
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

import app.pachli.R
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.adapter.StatusViewDataDiffCallback
import app.pachli.core.data.model.ConversationViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.model.ConversationAccount
import app.pachli.core.ui.SetContent
import app.pachli.core.ui.StatusActionListener
import app.pachli.databinding.ItemConversationBinding
import com.bumptech.glide.RequestManager

class ConversationViewHolder internal constructor(
    private val binding: ItemConversationBinding,
    glide: RequestManager,
    setContent: SetContent,
    private val listener: StatusActionListener,
) : ConversationAdapter.ViewHolder, StatusBaseViewHolder<ConversationViewData>(binding.root, glide, setContent) {

    override fun bind(viewData: ConversationViewData, payloads: List<List<Any?>>?, statusDisplayOptions: StatusDisplayOptions) {
        if (payloads.isNullOrEmpty()) {
            val actionable = viewData.actionable

            binding.statusView.setupWithStatus(setContent, glide, viewData, listener, statusDisplayOptions)

            statusControls.bind(
                status = actionable,
                showCounts = statusDisplayOptions.showStatsInline,
                confirmReblog = statusDisplayOptions.confirmReblogs,
                confirmFavourite = statusDisplayOptions.confirmFavourites,
                isReply = actionable.inReplyToId != null,
                isReblogged = actionable.reblogged,
                isFavourited = actionable.favourited,
                isBookmarked = actionable.bookmarked,
                replyCount = actionable.repliesCount,
                reblogCount = actionable.reblogsCount,
                favouriteCount = actionable.favouritesCount,
                onReplyClick = { listener.onReply(viewData) },
                onQuoteClick = if (statusDisplayOptions.canQuote) {
                    { listener.onQuote(viewData) }
                } else {
                    null
                },
                onFavouriteClick = { favourite -> listener.onFavourite(viewData, favourite) },
                onBookmarkClick = { bookmark -> listener.onBookmark(viewData, bookmark) },
                onMoreClick = { view -> listener.onMore(view, viewData) },
            )
            setConversationName(viewData.accounts)
        } else {
            payloads.flatten().forEach { item ->
                if (item == StatusViewDataDiffCallback.Payload.CREATED) {
                    binding.statusView.setMetaData(viewData, statusDisplayOptions, listener)
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
}
