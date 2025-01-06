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

package app.pachli.components.search.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import app.pachli.adapter.StatusViewHolder
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.StatusViewData
import app.pachli.databinding.ItemStatusBinding
import app.pachli.interfaces.StatusActionListener

class SearchStatusesAdapter(
    private val pachliAccountId: Long,
    private val statusDisplayOptions: StatusDisplayOptions,
    private val statusListener: StatusActionListener<StatusViewData>,
) : PagingDataAdapter<StatusViewData, StatusViewHolder<StatusViewData>>(STATUS_COMPARATOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusViewHolder<StatusViewData> {
        return StatusViewHolder(
            ItemStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )
    }

    override fun onBindViewHolder(holder: StatusViewHolder<StatusViewData>, position: Int) {
        getItem(position)?.let { item ->
            holder.setupWithStatus(pachliAccountId, item, statusListener, statusDisplayOptions)
        }
    }

    companion object {
        val STATUS_COMPARATOR = object : DiffUtil.ItemCallback<StatusViewData>() {
            override fun areContentsTheSame(oldItem: StatusViewData, newItem: StatusViewData): Boolean =
                oldItem == newItem

            override fun areItemsTheSame(oldItem: StatusViewData, newItem: StatusViewData): Boolean =
                oldItem.id == newItem.id
        }
    }
}
