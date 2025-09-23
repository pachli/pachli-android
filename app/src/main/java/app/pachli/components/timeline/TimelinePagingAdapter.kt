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
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.adapter.FilterableStatusViewHolder
import app.pachli.adapter.StatusViewDataDiffCallback
import app.pachli.adapter.StatusViewHolder
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.model.FilterAction
import app.pachli.core.ui.SetStatusContent
import app.pachli.core.ui.StatusActionListener
import app.pachli.databinding.ItemStatusBinding
import app.pachli.databinding.ItemStatusWrapperBinding
import com.bumptech.glide.RequestManager

class TimelinePagingAdapter(
    private val glide: RequestManager,
    private val setStatusContent: SetStatusContent,
    private val statusListener: StatusActionListener<StatusViewData>,
    var statusDisplayOptions: StatusDisplayOptions,
) : PagingDataAdapter<StatusViewData, RecyclerView.ViewHolder>(StatusViewDataDiffCallback) {
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(viewGroup.context)
        return when (viewType) {
            VIEW_TYPE_STATUS_FILTERED -> {
                FilterableStatusViewHolder<StatusViewData>(
                    ItemStatusWrapperBinding.inflate(inflater, viewGroup, false),
                    glide,
                    setStatusContent,
                )
            }
            VIEW_TYPE_STATUS -> {
                StatusViewHolder<StatusViewData>(
                    ItemStatusBinding.inflate(inflater, viewGroup, false),
                    glide,
                    setStatusContent,
                )
            }
            else -> return object : RecyclerView.ViewHolder(inflater.inflate(R.layout.item_placeholder, viewGroup, false)) {}
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        bindViewHolder(viewHolder, position, null)
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int, payloads: List<Any?>) {
        bindViewHolder(viewHolder, position, payloads as? List<List<Any?>>)
    }

    private fun bindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int, payloads: List<List<Any?>>?) {
        try {
            getItem(position)
        } catch (_: IndexOutOfBoundsException) {
            null
        }?.let {
            (viewHolder as StatusViewHolder<StatusViewData>).setupWithStatus(
                it,
                statusListener,
                statusDisplayOptions,
                payloads,
            )
        }
    }

    override fun getItemViewType(position: Int): Int {
        // The getItem() call here can occasionally trigger a bug in androidx.paging
        // where androidx.paging.PageStore.checkIndex(PageStore.kt:56) throws an
        // IndexOutOfBoundsException. Fall back to returning a placeholder in that
        // case.
        val viewData = try {
            getItem(position) ?: return VIEW_TYPE_PLACEHOLDER
        } catch (_: IndexOutOfBoundsException) {
            return VIEW_TYPE_PLACEHOLDER
        }

        return if (viewData.contentFilterAction == FilterAction.WARN) {
            VIEW_TYPE_STATUS_FILTERED
        } else {
            VIEW_TYPE_STATUS
        }
    }

    companion object {
        private const val VIEW_TYPE_STATUS = 0
        private const val VIEW_TYPE_STATUS_FILTERED = 1
        private const val VIEW_TYPE_PLACEHOLDER = -1
    }
}
