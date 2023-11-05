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
import app.pachli.entity.Status
import app.pachli.network.MastodonApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import timber.log.Timber

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
        try {
            val result = if (params is LoadParams.Refresh && key != null) {
                withContext(Dispatchers.IO) {
                    val initialStatus = async { getSingleStatus(key) }
                    val additionalStatuses = async { getStatusList(maxId = key, limit = params.loadSize - 1) }
                    buildList {
                        initialStatus.await()?.let { this.add(it) }
                        additionalStatuses.await()?.let { this.addAll(it) }
                    }
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

                getStatusList(minId = minId, maxId = maxId, limit = params.loadSize) ?: emptyList()
            }
            return LoadResult.Page(
                data = result,
                prevKey = result.firstOrNull()?.id,
                nextKey = result.lastOrNull()?.id,
            )
        } catch (e: Exception) {
            Timber.w("failed to load statuses", e)
            return LoadResult.Error(e)
        }
    }

    private suspend fun getSingleStatus(statusId: String): Status? {
        return mastodonApi.status(statusId).getOrNull()
    }

    private suspend fun getStatusList(minId: String? = null, maxId: String? = null, limit: Int): List<Status>? {
        return mastodonApi.accountStatuses(
            accountId = accountId,
            maxId = maxId,
            sinceId = null,
            minId = minId,
            limit = limit,
            excludeReblogs = true,
        ).body()
    }
}
