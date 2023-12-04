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

package app.pachli.components.scheduled

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.pachli.core.network.model.ScheduledStatus
import app.pachli.core.network.retrofit.MastodonApi
import at.connyduck.calladapter.networkresult.getOrElse

class ScheduledStatusPagingSourceFactory(
    private val mastodonApi: MastodonApi,
) : () -> ScheduledStatusPagingSource {

    private val scheduledTootsCache = mutableListOf<ScheduledStatus>()

    private var pagingSource: ScheduledStatusPagingSource? = null

    override fun invoke(): ScheduledStatusPagingSource {
        return ScheduledStatusPagingSource(mastodonApi, scheduledTootsCache).also {
            pagingSource = it
        }
    }

    fun remove(status: ScheduledStatus) {
        scheduledTootsCache.remove(status)
        pagingSource?.invalidate()
    }
}

class ScheduledStatusPagingSource(
    private val mastodonApi: MastodonApi,
    private val scheduledStatusesCache: MutableList<ScheduledStatus>,
) : PagingSource<String, ScheduledStatus>() {

    override fun getRefreshKey(state: PagingState<String, ScheduledStatus>): String? {
        return null
    }

    override suspend fun load(params: LoadParams<String>): LoadResult<String, ScheduledStatus> {
        return if (params is LoadParams.Refresh && scheduledStatusesCache.isNotEmpty()) {
            LoadResult.Page(
                data = scheduledStatusesCache,
                prevKey = null,
                nextKey = scheduledStatusesCache.lastOrNull()?.id,
            )
        } else {
            val result = mastodonApi.scheduledStatuses(
                maxId = params.key,
                limit = params.loadSize,
            ).getOrElse { return LoadResult.Error(it) }

            LoadResult.Page(data = result, prevKey = null, nextKey = result.lastOrNull()?.id)
        }
    }
}
