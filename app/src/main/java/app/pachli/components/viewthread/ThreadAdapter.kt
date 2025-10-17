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
import androidx.recyclerview.widget.ListAdapter
import app.pachli.adapter.FilterableStatusViewHolder
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.adapter.StatusDetailedViewHolder
import app.pachli.adapter.StatusViewDataDiffCallback
import app.pachli.adapter.StatusViewHolder
import app.pachli.core.activity.OpenUrlUseCase
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.model.FilterAction
import app.pachli.core.ui.SetStatusContent
import app.pachli.core.ui.StatusActionListener
import app.pachli.databinding.ItemStatusBinding
import app.pachli.databinding.ItemStatusDetailedBinding
import app.pachli.databinding.ItemStatusWrapperBinding
import com.bumptech.glide.RequestManager

class ThreadAdapter(
    private val glide: RequestManager,
    private val statusDisplayOptions: StatusDisplayOptions,
    private val statusActionListener: StatusActionListener<StatusViewData>,
    private val setStatusContent: SetStatusContent,
    private val openUrl: OpenUrlUseCase,
) : ListAdapter<StatusViewData, StatusBaseViewHolder<StatusViewData>>(StatusViewDataDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusBaseViewHolder<StatusViewData> {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_STATUS -> {
                StatusViewHolder(
                    ItemStatusBinding.inflate(inflater, parent, false),
                    glide,
                    setStatusContent,
                )
            }
            VIEW_TYPE_STATUS_FILTERED -> {
                FilterableStatusViewHolder(
                    ItemStatusWrapperBinding.inflate(inflater, parent, false),
                    glide,
                    setStatusContent,
                )
            }
            VIEW_TYPE_STATUS_DETAILED -> {
                StatusDetailedViewHolder(
                    ItemStatusDetailedBinding.inflate(inflater, parent, false),
                    glide,
                    setStatusContent,
                )
            }
            else -> error("Unknown item type: $viewType")
        }
    }

    override fun onBindViewHolder(viewHolder: StatusBaseViewHolder<StatusViewData>, position: Int) {
        val status = getItem(position)
        viewHolder.setupWithStatus(status, statusActionListener, statusDisplayOptions, null)
    }

    override fun onBindViewHolder(holder: StatusBaseViewHolder<StatusViewData>, position: Int, payloads: List<Any?>) {
        val status = getItem(position)
        holder.setupWithStatus(status, statusActionListener, statusDisplayOptions, payloads as? List<List<Any?>>)
    }

    override fun getItemViewType(position: Int): Int {
        val viewData = getItem(position)
        // Check contentFilterAction first to ensure that detailed statuses are also
        // filtered. This allows the user to view the thread and get more context to
        // decide whether or not to view the detailed status.
        return if (viewData.contentFilterAction == FilterAction.WARN) {
            VIEW_TYPE_STATUS_FILTERED
        } else if (viewData.isDetailed) {
            VIEW_TYPE_STATUS_DETAILED
        } else {
            VIEW_TYPE_STATUS
        }
    }

    companion object {
        private const val VIEW_TYPE_STATUS = 0
        private const val VIEW_TYPE_STATUS_DETAILED = 1
        private const val VIEW_TYPE_STATUS_FILTERED = 2
    }
}
