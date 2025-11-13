/* Copyright 2019 Joel Pyska
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

package app.pachli.components.report.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.StatusItemViewData
import app.pachli.core.model.Status
import app.pachli.core.ui.SetStatusContent
import app.pachli.core.ui.StatusActionListener
import app.pachli.databinding.ItemReportStatusBinding
import com.bumptech.glide.RequestManager

interface ReportStatusActionListener : StatusActionListener {
    fun setStatusChecked(status: Status, isChecked: Boolean)
    fun isStatusChecked(id: String): Boolean
}

class ReportStatusesAdapter(
    private val glide: RequestManager,
    private val setStatusContent: SetStatusContent,
    private val statusDisplayOptions: StatusDisplayOptions,
    private val reportStatusActionListener: ReportStatusActionListener,
) : PagingDataAdapter<StatusItemViewData, ReportStatusViewHolder>(STATUS_COMPARATOR) {

    private val statusForPosition: (Int) -> StatusItemViewData? = { position: Int ->
        if (position != RecyclerView.NO_POSITION) getItem(position) else null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportStatusViewHolder {
        val binding = ItemReportStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReportStatusViewHolder(
            binding,
            glide,
            setStatusContent,
            statusDisplayOptions,
            reportStatusActionListener,
            statusForPosition,
        )
    }

    override fun onBindViewHolder(holder: ReportStatusViewHolder, position: Int) {
        getItem(position)?.let { status ->
            holder.bind(status)
        }
    }

    companion object {
        val STATUS_COMPARATOR = object : DiffUtil.ItemCallback<StatusItemViewData>() {
            override fun areContentsTheSame(oldItem: StatusItemViewData, newItem: StatusItemViewData): Boolean = oldItem == newItem

            override fun areItemsTheSame(oldItem: StatusItemViewData, newItem: StatusItemViewData): Boolean = oldItem.statusId == newItem.statusId
        }
    }
}
