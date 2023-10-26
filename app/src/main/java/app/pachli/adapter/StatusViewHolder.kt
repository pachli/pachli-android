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
package app.pachli.adapter

import android.text.InputFilter
import android.text.TextUtils
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.databinding.ItemStatusBinding
import app.pachli.databinding.ItemStatusWrapperBinding
import app.pachli.entity.Emoji
import app.pachli.entity.Filter
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.SmartLengthInputFilter
import app.pachli.util.StatusDisplayOptions
import app.pachli.util.emojify
import app.pachli.util.formatNumber
import app.pachli.util.hide
import app.pachli.util.unicodeWrap
import app.pachli.util.visible
import app.pachli.viewdata.StatusViewData
import at.connyduck.sparkbutton.helpers.Utils

open class StatusViewHolder(
    private val binding: ItemStatusBinding,
    root: View? = null,
) : StatusBaseViewHolder(root ?: binding.root) {

    override fun setupWithStatus(
        status: StatusViewData,
        listener: StatusActionListener,
        statusDisplayOptions: StatusDisplayOptions,
        payloads: Any?,
    ) = with(binding) {
        if (payloads == null) {
            val sensitive = !TextUtils.isEmpty(status.actionable.spoilerText)
            val expanded = status.isExpanded
            setupCollapsedState(sensitive, expanded, status, listener)
            val reblogging = status.rebloggingStatus
            if (reblogging == null || status.filterAction === Filter.Action.WARN) {
                statusInfo.hide()
            } else {
                val rebloggedByDisplayName = reblogging.account.name
                setRebloggedByDisplayName(
                    rebloggedByDisplayName,
                    reblogging.account.emojis,
                    statusDisplayOptions,
                )
                statusInfo.setOnClickListener {
                    listener.onOpenReblog(
                        bindingAdapterPosition,
                    )
                }
            }
        }
        statusReblogsCount.visible(statusDisplayOptions.showStatsInline)
        statusFavouritesCount.visible(statusDisplayOptions.showStatsInline)
        setFavouritedCount(status.actionable.favouritesCount)
        setReblogsCount(status.actionable.reblogsCount)
        super.setupWithStatus(status, listener, statusDisplayOptions, payloads)
    }

    private fun setRebloggedByDisplayName(
        name: CharSequence,
        accountEmoji: List<Emoji>?,
        statusDisplayOptions: StatusDisplayOptions,
    ) = with(binding) {
        val context = statusInfo.context
        val wrappedName: CharSequence = name.unicodeWrap()
        val boostedText: CharSequence = context.getString(R.string.post_boosted_format, wrappedName)
        val emojifiedText =
            boostedText.emojify(accountEmoji, statusInfo, statusDisplayOptions.animateEmojis)
        statusInfo.text = emojifiedText
        statusInfo.visibility = View.VISIBLE
    }

    // don't use this on the same ViewHolder as setRebloggedByDisplayName, will cause recycling issues as paddings are changed
    protected fun setPollInfo(ownPoll: Boolean) = with(binding) {
        statusInfo.setText(if (ownPoll) R.string.poll_ended_created else R.string.poll_ended_voted)
        statusInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_poll_24dp, 0, 0, 0)
        statusInfo.compoundDrawablePadding =
            Utils.dpToPx(statusInfo.context, 10)
        statusInfo.setPaddingRelative(Utils.dpToPx(statusInfo.context, 28), 0, 0, 0)
        statusInfo.visibility = View.VISIBLE
    }

    protected fun setReblogsCount(reblogsCount: Int) = with(binding) {
        statusReblogsCount.text = formatNumber(reblogsCount.toLong(), 1000)
    }

    protected fun setFavouritedCount(favouritedCount: Int) = with(binding) {
        statusFavouritesCount.text = formatNumber(favouritedCount.toLong(), 1000)
    }

    protected fun hideStatusInfo() = with(binding) {
        statusInfo.hide()
    }

    private fun setupCollapsedState(
        sensitive: Boolean,
        expanded: Boolean,
        status: StatusViewData,
        listener: StatusActionListener,
    ) = with(binding) {
        /* input filter for TextViews have to be set before text */
        if (status.isCollapsible && (!sensitive || expanded)) {
            buttonToggleContent.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onContentCollapsedChange(
                        !status.isCollapsed,
                        position,
                    )
                }
            }
            buttonToggleContent.visibility = View.VISIBLE
            if (status.isCollapsed) {
                buttonToggleContent.setText(R.string.post_content_warning_show_more)
                content.filters = COLLAPSE_INPUT_FILTER
            } else {
                buttonToggleContent.setText(R.string.post_content_warning_show_less)
                content.filters = NO_INPUT_FILTER
            }
        } else {
            buttonToggleContent.visibility = View.GONE
            content.filters = NO_INPUT_FILTER
        }
    }

    override fun showStatusContent(show: Boolean) = with(binding) {
        super.showStatusContent(show)
        buttonToggleContent.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun toggleExpandedState(
        sensitive: Boolean,
        expanded: Boolean,
        status: StatusViewData,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener,
    ) {
        setupCollapsedState(sensitive, expanded, status, listener)
        super.toggleExpandedState(sensitive, expanded, status, statusDisplayOptions, listener)
    }

    companion object {
        private val COLLAPSE_INPUT_FILTER = arrayOf<InputFilter>(SmartLengthInputFilter)
        private val NO_INPUT_FILTER = arrayOfNulls<InputFilter>(0)
    }
}

open class FilterableStatusViewHolder(
    private val binding: ItemStatusWrapperBinding,
) : StatusViewHolder(binding.statusContainer, binding.root) {

    override fun setupWithStatus(
        status: StatusViewData,
        listener: StatusActionListener,
        statusDisplayOptions: StatusDisplayOptions,
        payloads: Any?,
    ) {
        super.setupWithStatus(status, listener, statusDisplayOptions, payloads)
        setupFilterPlaceholder(status, listener)
    }

    private fun setupFilterPlaceholder(
        status: StatusViewData,
        listener: StatusActionListener,
    ) {
        if (status.filterAction !== Filter.Action.WARN) {
            showFilteredPlaceholder(false)
            return
        }

        // Shouldn't be necessary given the previous test against getFilterAction(),
        // but guards against a possible NPE. See the TODO in StatusViewData.filterAction
        // for more details.
        val filterResults = status.actionable.filtered
        if (filterResults.isNullOrEmpty()) {
            showFilteredPlaceholder(false)
            return
        }
        var matchedFilter: Filter? = null
        for ((filter) in filterResults) {
            if (filter.action === Filter.Action.WARN) {
                matchedFilter = filter
                break
            }
        }

        // Guard against a possible NPE
        if (matchedFilter == null) {
            showFilteredPlaceholder(false)
            return
        }
        showFilteredPlaceholder(true)
        binding.statusFilteredPlaceholder.statusFilterLabel.text = itemView.context.getString(
            R.string.status_filter_placeholder_label_format,
            matchedFilter.title,
        )
        binding.statusFilteredPlaceholder.statusFilterShowAnyway.setOnClickListener {
            listener.clearWarningAction(
                bindingAdapterPosition,
            )
        }
    }

    private fun showFilteredPlaceholder(show: Boolean) {
        binding.statusContainer.root.visibility = if (show) View.GONE else View.VISIBLE
        binding.statusFilteredPlaceholder.root.visibility = if (show) View.VISIBLE else View.GONE
    }
}
