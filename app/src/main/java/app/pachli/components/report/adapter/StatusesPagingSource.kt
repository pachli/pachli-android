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

package app.pachli.components.report.adapter

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.pachli.core.network.model.Status
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.apiresult.ApiResult
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class StatusesPagingSource(
    private val accountId: String,
    private val mastodonApi: MastodonApi,
) : PagingSource<String, Status>() {

    override fun getRefreshKey(state: PagingState<String, Status>): String? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestItemToPosition(anchorPosition)?.id
        }
    }

    override suspend fun load(params: LoadParams<String>): LoadResult<String, Status> {
        val key = params.key
        return if (params is LoadParams.Refresh && key != null) {
            coroutineScope {
                val initialStatus = async { getSingleStatus(key) }
                val additionalStatuses = async { getStatusList(maxId = key, limit = params.loadSize - 1) }
                val list = buildList {
                    initialStatus.await()
                        .onSuccess { this.add(it.body) }
                        .onFailure { return@coroutineScope Err(it) }
                    additionalStatuses.await()
                        .onSuccess { this.addAll(it.body) }
                        .onFailure { return@coroutineScope Err(it) }
                }
                return@coroutineScope Ok(list)
            }
        } else {
            val maxId = if (params is LoadParams.Refresh || params is LoadParams.Append) {
                params.key
            } else {
                null
            }

            val minId = if (params is LoadParams.Prepend) {
                params.key
            } else {
                null
            }

            getStatusList(minId = minId, maxId = maxId, limit = params.loadSize).map { it.body }
        }.mapBoth(
            { LoadResult.Page(data = it, prevKey = it.firstOrNull()?.id, nextKey = it.lastOrNull()?.id) },
            { LoadResult.Error(it.throwable) },
        )
    }

    private suspend fun getSingleStatus(statusId: String): ApiResult<Status> {
        return mastodonApi.status(statusId)
    }

    private suspend fun getStatusList(minId: String? = null, maxId: String? = null, limit: Int): ApiResult<List<Status>> {
        return mastodonApi.accountStatuses(
            accountId = accountId,
            maxId = maxId,
            sinceId = null,
            minId = minId,
            limit = limit,
            excludeReblogs = true,
        )
    }
}
