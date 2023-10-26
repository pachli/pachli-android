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
import app.pachli.R
import app.pachli.core.network.model.Filter
import app.pachli.databinding.ItemStatusWrapperBinding
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.StatusDisplayOptions
import app.pachli.viewdata.StatusViewData

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
        binding.statusFilteredPlaceholder.statusFilterLabel.text = context.getString(
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
