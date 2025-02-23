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
import androidx.core.text.HtmlCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.model.AccountFilterReason
import app.pachli.core.model.FilterAction
import app.pachli.databinding.ItemConversationBinding
import app.pachli.databinding.ItemConversationFilteredBinding
import app.pachli.interfaces.StatusActionListener
import timber.log.Timber

class ConversationAdapter(
    private var statusDisplayOptions: StatusDisplayOptions,
    private val listener: StatusActionListener<ConversationViewData>,
) : PagingDataAdapter<ConversationViewData, RecyclerView.ViewHolder>(CONVERSATION_COMPARATOR) {
    /** View holders in this adapter must implement this interface. */
    interface ViewHolder {
        /** Bind the data from the notification and payloads to the view. */
        fun bind(
            viewData: ConversationViewData,
            payloads: List<*>?,
            statusDisplayOptions: StatusDisplayOptions,
        )
    }

    var mediaPreviewEnabled: Boolean
        get() = statusDisplayOptions.mediaPreviewEnabled
        set(mediaPreviewEnabled) {
            statusDisplayOptions = statusDisplayOptions.copy(
                mediaPreviewEnabled = mediaPreviewEnabled,
            )
        }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        Timber.d("getItemViewType(): ${item?.lastStatus?.id}")
        Timber.d("  cfa: ${item?.lastStatus?.contentFilterAction}")
        Timber.d("  afa: ${item?.accountFilterDecision}")

        if (item?.lastStatus?.contentFilterAction == FilterAction.WARN) {
            return ConversationViewKind.STATUS_FILTERED.ordinal
        }

        if (item?.accountFilterDecision is AccountFilterDecision.Warn) {
            return ConversationViewKind.ACCOUNT_FILTERED.ordinal
        }

        return ConversationViewKind.STATUS.ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (ConversationViewKind.entries[viewType]) {
            ConversationViewKind.STATUS ->
                ConversationViewHolder(
                    ItemConversationBinding.inflate(inflater, parent, false),
                    listener,
                )
            ConversationViewKind.STATUS_FILTERED ->
                FilterableConversationStatusViewHolder(
                    // Wrong layout
                    ItemConversationBinding.inflate(inflater, parent, false),
                    listener,
                )
            ConversationViewKind.ACCOUNT_FILTERED ->
                FilterableConversationViewHolder(
                    ItemConversationFilteredBinding.inflate(inflater, parent, false),
                    listener,
                )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        onBindViewHolder(holder, position, emptyList())
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any>,
    ) {
        getItem(position)?.let { conversationViewData ->
            (holder as ViewHolder).bind(conversationViewData, payloads, statusDisplayOptions)
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

/** How to present the conversation in the UI. */
enum class ConversationViewKind {
    /** View as the original lastStatus. */
    STATUS,

    /**
     * Hide the lastStatus behind a warning message because the content matched
     * a content filter.
     */
    STATUS_FILTERED,

    /**
     * Hide the conversation behind a warning message because the account matched
     * an account filter.
     */
    ACCOUNT_FILTERED,
}

/**
 * View holder for conversations filtered because the status matches a content filter.
 */
class FilterableConversationStatusViewHolder(
    private val binding: ItemConversationBinding, // <-- wrong type, needs to be the right layout
    private val listener: StatusActionListener<ConversationViewData>,
) : ConversationAdapter.ViewHolder, RecyclerView.ViewHolder(binding.root) {
    override fun bind(viewData: ConversationViewData, payloads: List<*>?, statusDisplayOptions: StatusDisplayOptions) {
        TODO("Not yet implemented")
    }
}

/**
 * View holder for conversations filtered because the status matches an account filter.
 */
// Note: item_conversation_filtered.xml is identical to item_notification_filtered_xml
class FilterableConversationViewHolder(
    private val binding: ItemConversationFilteredBinding,
    private val listener: StatusActionListener<ConversationViewData>,

) : ConversationAdapter.ViewHolder, RecyclerView.ViewHolder(binding.root) {
    private val context = binding.root.context

    private val notFollowing = HtmlCompat.fromHtml(
        context.getString(R.string.account_filter_placeholder_label_not_following),
        HtmlCompat.FROM_HTML_MODE_LEGACY,
    )

    private val younger30d = HtmlCompat.fromHtml(
        context.getString(R.string.account_filter_placeholder_label_younger_30d),
        HtmlCompat.FROM_HTML_MODE_LEGACY,
    )

    private val limitedByServer = HtmlCompat.fromHtml(
        context.getString(R.string.account_filter_placeholder_label_limited_by_server),
        HtmlCompat.FROM_HTML_MODE_LEGACY,
    )

    init {
        binding.accountFilterShowAnyway.setOnClickListener {
            // Need to implement StatusActionListener.clearAccountFilter
        }

        binding.accountFilterEditFilter.setOnClickListener {
            // Need to implement StatusActionListener.editAccountConversationFilter()
        }
    }

    override fun bind(viewData: ConversationViewData, payloads: List<*>?, statusDisplayOptions: StatusDisplayOptions) {
        // this.viewData = viewData
        binding.accountFilterDomain.text = HtmlCompat.fromHtml(
            context.getString(
                R.string.account_filter_placeholder_type_conversation,
                viewData.lastStatus.status.account.domain.ifEmpty { viewData.localDomain },
            ),
            HtmlCompat.FROM_HTML_MODE_LEGACY,
        )

        if (viewData.accountFilterDecision is AccountFilterDecision.Warn) {
            binding.accountFilterReason.text = when (viewData.accountFilterDecision.reason) {
                AccountFilterReason.NOT_FOLLOWING -> notFollowing
                AccountFilterReason.YOUNGER_30D -> younger30d
                AccountFilterReason.LIMITED_BY_SERVER -> limitedByServer
            }
        }
    }
}
