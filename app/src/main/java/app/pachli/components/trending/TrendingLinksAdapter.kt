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

package app.pachli.components.trending

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil.ItemCallback
import androidx.recyclerview.widget.ListAdapter
import app.pachli.R
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.network.model.TrendsLink
import app.pachli.databinding.ItemTrendingLinkBinding
import app.pachli.view.PreviewCardView

/**
 * @param statusDisplayOptions
 * @oaram withLinkTimeline If true, show a link to a timeline with statuses that
 * mention this link.
 * @param onViewLink
 */
class TrendingLinksAdapter(
    statusDisplayOptions: StatusDisplayOptions,
    withLinkTimeline: Boolean,
    private val onViewLink: PreviewCardView.OnClickListener,
) : ListAdapter<TrendsLink, TrendingLinkViewHolder>(diffCallback) {
    var statusDisplayOptions = statusDisplayOptions
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    var withLinkTimeline = withLinkTimeline
        set(value) {
            if (field != value) {
                field = value
                notifyItemRangeChanged(0, itemCount)
            }
        }

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrendingLinkViewHolder {
        return TrendingLinkViewHolder(
            ItemTrendingLinkBinding.inflate(LayoutInflater.from(parent.context)),
            onViewLink,
        )
    }

    override fun onBindViewHolder(holder: TrendingLinkViewHolder, position: Int) {
        holder.bind(getItem(position), statusDisplayOptions, withLinkTimeline)
    }

    override fun getItemViewType(position: Int): Int {
        return R.layout.item_trending_link
    }

    companion object {
        val diffCallback = object : ItemCallback<TrendsLink>() {
            override fun areItemsTheSame(oldItem: TrendsLink, newItem: TrendsLink): Boolean {
                return oldItem.url == newItem.url
            }

            override fun areContentsTheSame(oldItem: TrendsLink, newItem: TrendsLink): Boolean {
                return oldItem == newItem
            }
        }
    }
}
