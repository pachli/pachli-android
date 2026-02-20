/*
 * Copyright (c) 2025 Pachli Association
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

import android.widget.Checkable
import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.StatusItemViewData
import app.pachli.core.ui.SetContent
import app.pachli.databinding.ItemReportStatusBinding
import com.bumptech.glide.RequestManager

open class ReportStatusViewHolder(
    private val binding: ItemReportStatusBinding,
    private val glide: RequestManager,
    private val setContent: SetContent,
    private val statusDisplayOptions: StatusDisplayOptions,
    private val listener: ReportStatusActionListener,
    private val getStatusForPosition: (Int) -> StatusItemViewData?,
) : RecyclerView.ViewHolder(binding.root), Checkable {
    private var isChecked = false

    init {
        binding.statusSelection.setOnCheckedChangeListener { _, isChecked ->
            this.isChecked = isChecked
            viewdata()?.let { viewdata ->
                listener.setStatusChecked(viewdata.statusViewData.status, isChecked)
            }
        }
    }

    fun bind(viewData: StatusItemViewData) {
        isChecked = listener.isStatusChecked(viewData.statusId)
        binding.statusSelection.isChecked = isChecked
        binding.statusView.setupWithStatus(
            setContent,
            glide,
            viewData,
            listener,
            statusDisplayOptions,
        )

        itemView.setOnClickListener { listener.onViewThread(viewData.actionable) }

        itemView.accessibilityDelegate = null
        itemView.contentDescription = binding.statusView.getContentDescription(
            viewData,
            statusDisplayOptions,
        )
    }

    private fun viewdata() = getStatusForPosition(bindingAdapterPosition)

    override fun isChecked() = isChecked

    override fun setChecked(checked: Boolean) {
        isChecked = checked
        binding.statusSelection.isChecked = isChecked
    }

    override fun toggle() {
        setChecked(!isChecked)
    }
}
