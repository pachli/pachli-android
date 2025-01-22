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

package app.pachli.components.timeline

import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import app.pachli.components.timeline.TimelineRepository.Companion.PAGE_SIZE
import app.pachli.components.timeline.viewmodel.NetworkTimelinePagingSource
import app.pachli.components.timeline.viewmodel.NetworkTimelineRemoteMediator
import app.pachli.components.timeline.viewmodel.PageCache
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.model.Timeline
import app.pachli.core.network.model.Status
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.ui.getDomain
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

// Things that make this more difficult than it should be:
//
// - Mastodon API doesn't support "Fetch page that contains item X", you have to rely on having
//   the page that contains item X, and the previous or next page, so you can use the prev/next
//   link values from the next or previous page to step forwards or backwards to the page you
//   actually want.
//
// - Not all Mastodon APIs that paginate support a "Fetch me just the item X". E.g., getting a
//   list of bookmarks (https://docs.joinmastodon.org/methods/bookmarks/#get) paginates, but does
//   not support a "Get a single bookmark" call. Ditto for favourites. So even though some API
//   methods do support that they can't be used here, because this has to work for all paging APIs.
//
// - Values of next/prev in the Link header do not have to match any of the item keys (or be taken
//   from the same namespace).
//
// - Two pages that are consecutive in the result set may not have next/prev values that point
//   back to each other. I.e., this is a valid set of two pages from an API call:
//
//   .--- page index
//   |     .-- ID of last item (key in `pageCache`)
//   v     V
//   0: k: 109934818460629189, prevKey: 995916, nextKey: 941865
//   1: k: 110033940961955385, prevKey: 1073324, nextKey: 997376
//
//   They are consecutive in the result set, but pageCache[0].prevKey != pageCache[1].nextKey. So
//   there's no benefit to using the nextKey/prevKey tokens as the keys in PageCache.
//
// - Bugs in the Paging library mean that on initial load (especially of rapidly changing timelines
//   like Federated) the user's initial position can jump around a lot. See:
//   - https://issuetracker.google.com/issues/235319241
//   - https://issuetracker.google.com/issues/289824257

/** Timeline repository where the timeline information is backed by an in-memory cache. */
class NetworkTimelineRepository @Inject constructor(
    private val mastodonApi: MastodonApi,
) : TimelineRepository<Status> {
    private val pageCache = PageCache()

    private var factory: InvalidatingPagingSourceFactory<String, Status>? = null

    /** @return flow of Mastodon [Status]. */
    @OptIn(ExperimentalPagingApi::class)
    override suspend fun getStatusStream(
        account: AccountEntity,
        kind: Timeline,
    ): Flow<PagingData<Status>> {
        Timber.d("getStatusStream()")

        factory = InvalidatingPagingSourceFactory {
            NetworkTimelinePagingSource(pageCache)
        }

        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE),
            remoteMediator = NetworkTimelineRemoteMediator(
                mastodonApi,
                account,
                factory!!,
                pageCache,
                kind,
            ),
            pagingSourceFactory = factory!!,
        ).flow
    }

    override suspend fun invalidate(pachliAccountId: Long) = factory?.invalidate() ?: Unit

    fun invalidate() = factory?.invalidate()

    fun removeAllByAccountId(accountId: String) {
        synchronized(pageCache) {
            for (page in pageCache.values) {
                page.data.removeAll { status ->
                    status.account.id == accountId || status.actionableStatus.account.id == accountId
                }
            }
        }
        invalidate()
    }

    fun removeAllByInstance(instance: String) {
        synchronized(pageCache) {
            for (page in pageCache.values) {
                page.data.removeAll { status -> getDomain(status.account.url) == instance }
            }
        }
        invalidate()
    }

    fun removeStatusWithId(statusId: String) {
        synchronized(pageCache) {
            pageCache.getPageById(statusId)?.data?.removeAll { status ->
                status.id == statusId || status.reblog?.id == statusId
            }
        }
        invalidate()
    }

    fun updateStatusById(statusId: String, updater: (Status) -> Status) {
        synchronized(pageCache) {
            pageCache.getPageById(statusId)?.let { page ->
                val index = page.data.indexOfFirst { it.id == statusId }
                if (index != -1) {
                    page.data[index] = updater(page.data[index])
                }
            }
        }
        invalidate()
    }

    fun updateActionableStatusById(statusId: String, updater: (Status) -> Status) {
        synchronized(pageCache) {
            pageCache.getPageById(statusId)?.let { page ->
                val index = page.data.indexOfFirst { it.id == statusId }
                if (index != -1) {
                    val status = page.data[index]
                    page.data[index] = status.reblog?.let {
                        status.copy(reblog = it)
                    } ?: updater(status)
                }
            }
        }
    }

    fun reload() {
        synchronized(pageCache) {
            pageCache.clear()
        }
        invalidate()
    }
}
