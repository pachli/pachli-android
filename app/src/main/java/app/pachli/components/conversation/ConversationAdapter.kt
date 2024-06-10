/* Copyright 2021 Tusky Contributors
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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import app.pachli.R
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.interfaces.StatusActionListener

class ConversationAdapter(
    private var statusDisplayOptions: StatusDisplayOptions,
    private val listener: StatusActionListener<ConversationViewData>,
) : PagingDataAdapter<ConversationViewData, ConversationViewHolder>(CONVERSATION_COMPARATOR) {

    var mediaPreviewEnabled: Boolean
        get() = statusDisplayOptions.mediaPreviewEnabled
        set(mediaPreviewEnabled) {
            statusDisplayOptions = statusDisplayOptions.copy(
                mediaPreviewEnabled = mediaPreviewEnabled,
            )
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view, statusDisplayOptions, listener)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        onBindViewHolder(holder, position, emptyList())
    }

    override fun onBindViewHolder(
        holder: ConversationViewHolder,
        position: Int,
        payloads: List<Any>,
    ) {
        getItem(position)?.let { conversationViewData ->
            holder.setupWithConversation(conversationViewData, payloads.firstOrNull())
        }
    }

    companion object {
        val CONVERSATION_COMPARATOR = object : DiffUtil.ItemCallback<ConversationViewData>() {
            override fun areItemsTheSame(oldItem: ConversationViewData, newItem: ConversationViewData): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ConversationViewData, newItem: ConversationViewData): Boolean {
                return false // Items are different always. It allows to refresh timestamp on every view holder update
            }

            override fun getChangePayload(oldItem: ConversationViewData, newItem: ConversationViewData): Any? {
                return if (oldItem == newItem) {
                    // If items are equal - update timestamp only
                    listOf(StatusBaseViewHolder.Key.KEY_CREATED)
                } else {
                    // If items are different - update the whole view holder
                    null
                }
            }
        }
    }
}
