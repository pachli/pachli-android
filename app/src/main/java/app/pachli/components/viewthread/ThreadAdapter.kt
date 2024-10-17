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

package app.pachli.components.viewthread

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import app.pachli.adapter.FilterableStatusViewHolder
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.adapter.StatusDetailedViewHolder
import app.pachli.adapter.StatusViewHolder
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.model.FilterAction
import app.pachli.databinding.ItemStatusBinding
import app.pachli.databinding.ItemStatusDetailedBinding
import app.pachli.databinding.ItemStatusWrapperBinding
import app.pachli.interfaces.StatusActionListener
import app.pachli.viewdata.StatusViewData

class ThreadAdapter(
    private val pachliAccountId: Long,
    private val statusDisplayOptions: StatusDisplayOptions,
    private val statusActionListener: StatusActionListener<StatusViewData>,
) : ListAdapter<StatusViewData, StatusBaseViewHolder<StatusViewData>>(ThreadDifferCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusBaseViewHolder<StatusViewData> {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_STATUS -> {
                StatusViewHolder(ItemStatusBinding.inflate(inflater, parent, false))
            }
            VIEW_TYPE_STATUS_FILTERED -> {
                FilterableStatusViewHolder(ItemStatusWrapperBinding.inflate(inflater, parent, false))
            }
            VIEW_TYPE_STATUS_DETAILED -> {
                StatusDetailedViewHolder(ItemStatusDetailedBinding.inflate(inflater, parent, false))
            }
            else -> error("Unknown item type: $viewType")
        }
    }

    override fun onBindViewHolder(viewHolder: StatusBaseViewHolder<StatusViewData>, position: Int) {
        val status = getItem(position)
        viewHolder.setupWithStatus(pachliAccountId, status, statusActionListener, statusDisplayOptions)
    }

    override fun getItemViewType(position: Int): Int {
        val viewData = getItem(position)
        return if (viewData.isDetailed) {
            VIEW_TYPE_STATUS_DETAILED
        } else if (viewData.filterAction == FilterAction.WARN) {
            VIEW_TYPE_STATUS_FILTERED
        } else {
            VIEW_TYPE_STATUS
        }
    }

    companion object {
        private const val VIEW_TYPE_STATUS = 0
        private const val VIEW_TYPE_STATUS_DETAILED = 1
        private const val VIEW_TYPE_STATUS_FILTERED = 2

        val ThreadDifferCallback = object : DiffUtil.ItemCallback<StatusViewData>() {
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
                return false // Items are different always. It allows to refresh timestamp on every view holder update
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
