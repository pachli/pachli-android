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

import androidx.paging.LoadState
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.take

/**
 * Performs [action] after the next prepend operation completes on the adapter.
 *
 * A prepend operation is complete when the adapter's prepend [LoadState] transitions
 * from [LoadState.Loading] to [LoadState.NotLoading].
 */
suspend fun <T : Any, VH : RecyclerView.ViewHolder> PagingDataAdapter<T, VH>.postPrepend(
    action: () -> Unit,
) {
    val initial: Pair<LoadState?, LoadState?> = Pair(null, null)
    loadStateFlow
        .runningFold(initial) { prev, next -> prev.second to next.prepend }
        .filter { it.first is LoadState.Loading && it.second is LoadState.NotLoading }
        .take(1)
        .collect { action() }
}
