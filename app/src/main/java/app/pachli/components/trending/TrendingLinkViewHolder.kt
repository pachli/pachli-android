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
import app.pachli.core.network.model.TrendsLink
import app.pachli.databinding.ItemTrendingLinkBinding
import app.pachli.util.StatusDisplayOptions

class TrendingLinkViewHolder(
    private val binding: ItemTrendingLinkBinding,
    private val onClick: (String) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(link: TrendsLink, statusDisplayOptions: StatusDisplayOptions) {
        binding.statusCardView.bind(link, sensitive = false, statusDisplayOptions) {
            onClick(link.url)
        }
    }
}
