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

package app.pachli.adapter

import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.StatusViewDataQ
import app.pachli.core.ui.SetStatusContent
import app.pachli.core.ui.StatusActionListener
import app.pachli.databinding.ItemStatusDetailedBinding
import com.bumptech.glide.RequestManager

class StatusDetailedViewHolder(
    binding: ItemStatusDetailedBinding,
    glide: RequestManager,
    setStatusContent: SetStatusContent,
) : StatusBaseViewHolder<StatusViewDataQ, IStatusViewData>(binding.root, glide, setStatusContent) {

    override fun setupWithStatus(
        viewData: StatusViewDataQ,
        listener: StatusActionListener<IStatusViewData>,
        statusDisplayOptions: StatusDisplayOptions,
        payloads: List<List<Any?>>?,
    ) {
        // Hide statistics on the controls in detailed view, statistics are (optionally) shown
        // elsewhere in the UI.
        super.setupWithStatus(
            viewData,
            listener,
            statusDisplayOptions.copy(showStatsInline = false),
            payloads,
        )
    }
}
