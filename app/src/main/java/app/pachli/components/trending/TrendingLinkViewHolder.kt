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

import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.model.TrendsLink
import app.pachli.databinding.ItemTrendingLinkBinding
import app.pachli.view.PreviewCardView
import com.bumptech.glide.RequestManager

class TrendingLinkViewHolder(
    private val binding: ItemTrendingLinkBinding,
    private val glide: RequestManager,
    private val onClick: PreviewCardView.OnClickListener,
) : RecyclerView.ViewHolder(binding.root) {
    internal lateinit var link: TrendsLink

    /**
     * @param link
     * @param statusDisplayOptions
     * @param showTimelineLink True if the UI to view a timeline of statuses about this link
     * should be shown.
     */
    fun bind(link: TrendsLink, statusDisplayOptions: StatusDisplayOptions, showTimelineLink: Boolean) {
        this.link = link

        binding.statusCardView.bind(glide, link, sensitive = false, statusDisplayOptions, showTimelineLink, onClick)
    }
}
