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

package app.pachli.adapter

import android.view.View
import androidx.core.text.HtmlCompat
import app.pachli.R
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.model.FilterAction
import app.pachli.core.network.model.Filter
import app.pachli.databinding.ItemStatusWrapperBinding
import app.pachli.interfaces.StatusActionListener
import app.pachli.viewdata.IStatusViewData

open class FilterableStatusViewHolder<T : IStatusViewData>(
    private val pachliAccountId: Long,
    private val binding: ItemStatusWrapperBinding,
) : StatusViewHolder<T>(pachliAccountId, binding.statusContainer, binding.root) {
    /** The filter that matched the status, null if the status is not being filtered. */
    var matchedFilter: Filter? = null

    override fun setupWithStatus(
        viewData: T,
        listener: StatusActionListener<T>,
        statusDisplayOptions: StatusDisplayOptions,
        payloads: Any?,
    ) {
        super.setupWithStatus(viewData, listener, statusDisplayOptions, payloads)
        setupFilterPlaceholder(viewData, listener)
    }

    private fun setupFilterPlaceholder(
        status: T,
        listener: StatusActionListener<T>,
    ) {
        if (status.filterAction !== FilterAction.WARN) {
            matchedFilter = null
            setPlaceholderVisibility(false)
            return
        }

        status.actionable.filtered?.find { it.filter.filterAction === app.pachli.core.network.model.FilterAction.WARN }?.let { result ->
            this.matchedFilter = result.filter
            setPlaceholderVisibility(true)

            val label = HtmlCompat.fromHtml(
                context.getString(
                    R.string.status_filter_placeholder_label_format,
                    result.filter.title,
                ),
                HtmlCompat.FROM_HTML_MODE_LEGACY,
            )
            binding.root.contentDescription = label
            binding.statusFilteredPlaceholder.statusFilterLabel.text = label

            binding.statusFilteredPlaceholder.statusFilterShowAnyway.setOnClickListener {
                listener.clearWarningAction(status)
            }
            binding.statusFilteredPlaceholder.statusFilterEditFilter.setOnClickListener {
                listener.onEditFilterById(pachliAccountId, result.filter.id)
            }
        } ?: {
            matchedFilter = null
            setPlaceholderVisibility(false)
        }
    }

    private fun setPlaceholderVisibility(show: Boolean) {
        binding.statusContainer.root.visibility = if (show) View.GONE else View.VISIBLE
        binding.statusFilteredPlaceholder.root.visibility = if (show) View.VISIBLE else View.GONE
    }
}
