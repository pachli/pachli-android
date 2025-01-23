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

package app.pachli.components.timeline

import androidx.paging.PagingData
import androidx.paging.PagingSource
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.model.Timeline
import kotlinx.coroutines.flow.Flow

/**
 * Common interface for a repository that provides a [PagingData] timeline of items
 * of type [T].
 */
interface TimelineRepository<T : Any> {
    /** @return Flow of [T] for [account] and [kind]. */
    suspend fun getStatusStream(account: AccountEntity, kind: Timeline): Flow<PagingData<T>>

    /** Invalidate the active paging source for [pachliAccountId], see [PagingSource.invalidate] */
    suspend fun invalidate(pachliAccountId: Long)

    companion object {
        /** Default page size when fetching remote items. */
        const val PAGE_SIZE = 30
    }
}
