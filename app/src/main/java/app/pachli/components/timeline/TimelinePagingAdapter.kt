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

package app.pachli.components.timeline

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.adapter.FilterableStatusViewHolder
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.adapter.StatusViewHolder
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.network.model.Filter
import app.pachli.databinding.ItemStatusBinding
import app.pachli.databinding.ItemStatusWrapperBinding
import app.pachli.interfaces.StatusActionListener
import app.pachli.viewdata.StatusViewData

class TimelinePagingAdapter(
    private val statusListener: StatusActionListener<StatusViewData>,
    var statusDisplayOptions: StatusDisplayOptions,
) : PagingDataAdapter<StatusViewData, RecyclerView.ViewHolder>(TimelineDifferCallback) {
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(viewGroup.context)
        return when (viewType) {
            VIEW_TYPE_STATUS_FILTERED -> {
                FilterableStatusViewHolder<StatusViewData>(ItemStatusWrapperBinding.inflate(inflater, viewGroup, false))
            }
            VIEW_TYPE_STATUS -> {
                StatusViewHolder<StatusViewData>(ItemStatusBinding.inflate(inflater, viewGroup, false))
            }
            else -> return object : RecyclerView.ViewHolder(inflater.inflate(R.layout.item_placeholder, viewGroup, false)) {}
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        bindViewHolder(viewHolder, position, null)
    }

    override fun onBindViewHolder(
        viewHolder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<*>,
    ) {
        bindViewHolder(viewHolder, position, payloads)
    }

    private fun bindViewHolder(
        viewHolder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<*>?,
    ) {
        getItem(position)?.let {
            (viewHolder as StatusViewHolder<StatusViewData>).setupWithStatus(
                it,
                statusListener,
                statusDisplayOptions,
                payloads?.getOrNull(0),
            )
        }
    }

    override fun getItemViewType(position: Int): Int {
        val viewData = getItem(position) ?: return VIEW_TYPE_PLACEHOLDER
        return if (viewData.filterAction == Filter.Action.WARN) {
            VIEW_TYPE_STATUS_FILTERED
        } else {
            VIEW_TYPE_STATUS
        }
    }

    companion object {
        private const val VIEW_TYPE_STATUS = 0
        private const val VIEW_TYPE_STATUS_FILTERED = 1
        private const val VIEW_TYPE_PLACEHOLDER = -1

        val TimelineDifferCallback = object : DiffUtil.ItemCallback<StatusViewData>() {
            override fun areItemsTheSame(
                oldItem: StatusViewData,
                newItem: StatusViewData,
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: StatusViewData,
                newItem: StatusViewData,
            ): Boolean {
                return oldItem == newItem
            }

            override fun getChangePayload(
                oldItem: StatusViewData,
                newItem: StatusViewData,
            ): Any? {
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
