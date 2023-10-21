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
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package app.pachli.components.search.adapter

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.pachli.components.search.SearchType
import app.pachli.entity.SearchResult
import app.pachli.core.network.retrofit.MastodonApi
import at.connyduck.calladapter.networkresult.getOrElse

class SearchPagingSource<T : Any>(
    private val mastodonApi: MastodonApi,
    private val searchType: SearchType,
    private val searchRequest: String,
    private val initialItems: List<T>?,
    private val parser: (SearchResult) -> List<T>,
) : PagingSource<Int, T>() {

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        if (searchRequest.isEmpty()) {
            return LoadResult.Page(
                data = emptyList(),
                prevKey = null,
                nextKey = null,
            )
        }

        if (params.key == null && !initialItems.isNullOrEmpty()) {
            return LoadResult.Page(
                data = initialItems.toList(),
                prevKey = null,
                nextKey = initialItems.size,
            )
        }

        val currentKey = params.key ?: 0

        val data = mastodonApi.search(
            query = searchRequest,
            type = searchType.apiParameter,
            resolve = true,
            limit = params.loadSize,
            offset = currentKey,
            following = false,
        ).getOrElse { return LoadResult.Error(it) }

        val res = parser(data)

        val nextKey = if (res.isEmpty()) {
            null
        } else {
            currentKey + res.size
        }

        return LoadResult.Page(
            data = res,
            prevKey = null,
            nextKey = nextKey,
        )
    }
}
