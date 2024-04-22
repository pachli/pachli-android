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

package app.pachli.components.timeline.viewmodel

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import app.pachli.core.network.model.Status
import javax.inject.Inject
import timber.log.Timber

private val INVALID = LoadResult.Invalid<String, Status>()

/**
 * [PagingSource] for Mastodon Status, identified by the Status ID
 *
 * @param pageCache The [PageCache] backing this source
 */
class NetworkTimelinePagingSource @Inject constructor(
    private val pageCache: PageCache,
) : PagingSource<String, Status>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, Status> {
        Timber.d("- load(), type = %s, key = %s", params.javaClass.simpleName, params.key)
        pageCache.debug()

        val page = synchronized(pageCache) {
            if (pageCache.isEmpty()) return@synchronized null

            when (params) {
                is LoadParams.Refresh -> {
                    pageCache.getPageById(params.key) ?: pageCache.firstPage
                }
                is LoadParams.Append -> {
                    pageCache.getNextPage(params.key)
                }
                is LoadParams.Prepend -> {
                    pageCache.getPrevPage(params.key)
                }
            }
        }

        if (page == null) {
            Timber.d("  Returning empty page")
        } else {
            Timber.d("  Returning full page:")
            Timber.d("     %s", page)
        }

        // Bail if this paging source has already been invalidated. If you do not do this there
        // is a lot of spurious animation, especially during the initial load, as multiple pages
        // are loaded and the paging source is repeatedly invalidated.
        if (invalid) {
            Timber.d("Invalidated, returning LoadResult.Invalid")
            return INVALID
        }

        // Set itemsBefore and itemsAfter values to include in the returned Page.
        // If you do not do this (and this is not documented anywhere) then the anchorPosition
        // in the PagingState (used in getRefreshKey) is bogus, and refreshing the list can
        // result in large jumps in the user's position.
        //
        // The items are calculated relative to the local cache, not the remote data source.
        val itemsBefore = pageCache.itemsBefore(page?.prevKey)
        val itemsAfter = pageCache.itemsAfter(page?.nextKey)

        return LoadResult.Page(
            page?.data ?: emptyList(),
            nextKey = page?.nextKey,
            prevKey = page?.prevKey,
            itemsAfter = itemsAfter,
            itemsBefore = itemsBefore,
        )
    }

    override fun getRefreshKey(state: PagingState<String, Status>): String? {
        val refreshKey = state.anchorPosition?.let {
            state.closestItemToPosition(it)?.id
        } ?: pageCache.firstPage?.data?.let {
            it.getOrNull(it.size / 2)?.id
        }

        Timber.d("- getRefreshKey(), state.anchorPosition = %d, return %s", state.anchorPosition, refreshKey)
        return refreshKey
    }
}
