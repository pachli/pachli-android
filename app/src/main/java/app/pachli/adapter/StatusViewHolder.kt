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

package app.pachli.adapter

import android.text.InputFilter
import android.text.TextUtils
import android.view.View
import app.pachli.R
import app.pachli.core.activity.emojify
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.common.util.formatNumber
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.network.model.Emoji
import app.pachli.core.network.model.Filter
import app.pachli.databinding.ItemStatusBinding
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.SmartLengthInputFilter
import app.pachli.viewdata.IStatusViewData
import at.connyduck.sparkbutton.helpers.Utils

open class StatusViewHolder<T : IStatusViewData>(
    private val binding: ItemStatusBinding,
    root: View? = null,
) : StatusBaseViewHolder<T>(root ?: binding.root) {

    override fun setupWithStatus(
        viewData: T,
        listener: StatusActionListener<T>,
        statusDisplayOptions: StatusDisplayOptions,
        payloads: Any?,
    ) = with(binding) {
        if (payloads == null) {
            val sensitive = !TextUtils.isEmpty(viewData.actionable.spoilerText)
            val expanded = viewData.isExpanded
            setupCollapsedState(viewData, sensitive, expanded, listener)
            val reblogging = viewData.rebloggingStatus
            if (reblogging == null || viewData.filterAction === Filter.Action.WARN) {
                statusInfo.hide()
            } else {
                val rebloggedByDisplayName = reblogging.account.name
                setRebloggedByDisplayName(
                    rebloggedByDisplayName,
                    reblogging.account.emojis,
                    statusDisplayOptions,
                )
                statusInfo.setOnClickListener {
                    listener.onOpenReblog(viewData.status)
                }
            }
        }
        statusReblogsCount.visible(statusDisplayOptions.showStatsInline)
        statusFavouritesCount.visible(statusDisplayOptions.showStatsInline)
        setFavouritedCount(viewData.actionable.favouritesCount)
        setReblogsCount(viewData.actionable.reblogsCount)
        super.setupWithStatus(viewData, listener, statusDisplayOptions, payloads)
    }

    private fun setRebloggedByDisplayName(
        name: CharSequence,
        accountEmoji: List<Emoji>?,
        statusDisplayOptions: StatusDisplayOptions,
    ) = with(binding) {
        val wrappedName: CharSequence = name.unicodeWrap()
        val boostedText: CharSequence = context.getString(R.string.post_boosted_format, wrappedName)
        val emojifiedText =
            boostedText.emojify(accountEmoji, statusInfo, statusDisplayOptions.animateEmojis)
        statusInfo.text = emojifiedText
        statusInfo.show()
    }

    // don't use this on the same ViewHolder as setRebloggedByDisplayName, will cause recycling issues as paddings are changed
    protected fun setPollInfo(ownPoll: Boolean) = with(binding) {
        statusInfo.setText(if (ownPoll) R.string.poll_ended_created else R.string.poll_ended_voted)
        statusInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_poll_24dp, 0, 0, 0)
        statusInfo.compoundDrawablePadding =
            Utils.dpToPx(context, 10)
        statusInfo.setPaddingRelative(Utils.dpToPx(context, 28), 0, 0, 0)
        statusInfo.show()
    }

    private fun setReblogsCount(reblogsCount: Int) = with(binding) {
        statusReblogsCount.text = formatNumber(reblogsCount.toLong(), 1000)
    }

    private fun setFavouritedCount(favouritedCount: Int) = with(binding) {
        statusFavouritesCount.text = formatNumber(favouritedCount.toLong(), 1000)
    }

    protected fun hideStatusInfo() = with(binding) {
        statusInfo.hide()
    }

    private fun setupCollapsedState(
        viewData: T,
        sensitive: Boolean,
        expanded: Boolean,
        listener: StatusActionListener<T>,
    ) = with(binding) {
        /* input filter for TextViews have to be set before text */
        if (viewData.isCollapsible && (!sensitive || expanded)) {
            buttonToggleContent.setOnClickListener {
                listener.onContentCollapsedChange(viewData, !viewData.isCollapsed)
            }
            buttonToggleContent.show()
            if (viewData.isCollapsed) {
                buttonToggleContent.setText(R.string.post_content_warning_show_more)
                content.filters = COLLAPSE_INPUT_FILTER
            } else {
                buttonToggleContent.setText(R.string.post_content_warning_show_less)
                content.filters = NO_INPUT_FILTER
            }
        } else {
            buttonToggleContent.hide()
            content.filters = NO_INPUT_FILTER
        }
    }

    override fun showStatusContent(show: Boolean) = with(binding) {
        super.showStatusContent(show)
        buttonToggleContent.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun toggleExpandedState(
        viewData: T,
        sensitive: Boolean,
        expanded: Boolean,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener<T>,
    ) {
        setupCollapsedState(viewData, sensitive, expanded, listener)
        super.toggleExpandedState(viewData, sensitive, expanded, statusDisplayOptions, listener)
    }

    companion object {
        private val COLLAPSE_INPUT_FILTER = arrayOf<InputFilter>(SmartLengthInputFilter)
        private val NO_INPUT_FILTER = arrayOfNulls<InputFilter>(0)
    }
}
