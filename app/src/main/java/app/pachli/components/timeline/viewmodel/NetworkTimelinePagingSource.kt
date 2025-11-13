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

import androidx.annotation.VisibleForTesting
import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import app.pachli.core.data.repository.StatusRepository
import app.pachli.core.database.dao.TimelineStatusWithAccount
import app.pachli.core.database.model.TSQ
import app.pachli.core.database.model.asEntity
import app.pachli.core.model.Status
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

private val INVALID = LoadResult.Invalid<String, TSQ>()

/**
 * [PagingSource] for Mastodon Status, identified by the Status ID
 *
 * @param pageCache The [PageCache] backing this source
 * @param initialKey The initial key to load, see [getRefreshKey].
 */
class NetworkTimelinePagingSource(
    private val pachliAccountId: Long,
    private val pageCache: PageCache,
    private val initialKey: String? = null,
    private val statusRepository: StatusRepository,
) : PagingSource<String, TSQ>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, TSQ> {
        Timber.d("- load(), type = %s, key = %s", params.javaClass.simpleName, params.key)

        return pageCache.withLock {
            pageCache.debug()

            val page = run {
                if (pageCache.isEmpty()) return@run null

                return@run when (params) {
                    is LoadParams.Refresh -> {
                        // If params.key is null then either there was no initialKey, or
                        // getRefreshKey returned null. If so just return the first page.
                        // Similarly, if there is no page with that key then return the
                        // first page.
                        Timber.d("Refreshing with ${params.key}")
                        val page = params.key?.let { pageCache.getPageById(it) }
                            ?: return@run pageCache.firstPage

                        // Refresh pages should be bigger than the normal loadSize. If not
                        // scrolling past an "edge" that triggers a refresh can still cause
                        // flicking and slight jump scrolling.
                        //
                        // Fix this by checking the page size. If it's too small construct
                        // a larger synthetic page by including the desired page's immediate
                        // neighbours.
                        if (page.data.size > params.loadSize) return@run page

                        val prevPage = page.prevKey?.let { pageCache.getPrevPage(it) }
                        val nextPage = page.nextKey?.let { pageCache.getNextPage(it) }

                        Page(
                            data = buildList {
                                addAll(prevPage?.data.orEmpty())
                                addAll(page.data)
                                addAll(nextPage?.data.orEmpty())
                            }.toMutableList(),

                            // Need to distinguish between nextPage or prevPage being null,
                            // and them not being null but having a null nextKey or prevKey.
                            //
                            // If the *page* is null then fall back to the key from the
                            // page in the middle. But the page might exist but have a
                            // null next/prev key, which is valid. In that case that
                            // key must be used as is, even if it is null. So the "?:"
                            // operator won't work here.
                            nextKey = if (nextPage == null) page.nextKey else nextPage.nextKey,
                            prevKey = if (prevPage == null) page.prevKey else prevPage.prevKey,
                        )
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
                Timber.d("  Returning empty page for %s", params.javaClass.simpleName)
            } else {
                Timber.d("  Returning full page for %s", params.javaClass.simpleName)
                Timber.d("     %s", page)
            }

            // Bail if this paging source has already been invalidated. If you do not do this there
            // is a lot of spurious animation, especially during the initial load, as multiple pages
            // are loaded and the paging source is repeatedly invalidated.
            if (invalid) {
                Timber.d("Invalidated, returning LoadResult.Invalid for %s", params.javaClass.simpleName)
                return INVALID
            }

            LoadResult.Page(
                page?.data.orEmpty().asTSQ(pachliAccountId, statusRepository),
                nextKey = page?.nextKey,
                prevKey = page?.prevKey,
            )
        }
    }

    override fun getRefreshKey(state: PagingState<String, TSQ>): String? {
        // `state` might be null (see https://issuetracker.google.com/issues/452663010
        // for details). If it is, fall back to the key passed to the constructor.
        val refreshKey = state.anchorPosition?.let {
            state.closestItemToPosition(it)?.timelineStatus?.status?.serverId
        } ?: initialKey
        Timber.d("- getRefreshKey(), state.anchorPosition = %d, return %s", state.anchorPosition, refreshKey)
        return refreshKey
    }
}

/**
 * Converts a `Iterable<Status>` to a `List<TSQ>`, minimising the database lookups
 * that need to happen to get the viewdata and translations.
 */
@VisibleForTesting
suspend fun Iterable<Status>.asTSQ(pachliAccountId: Long, statusRepository: StatusRepository): List<TSQ> {
    // Figure out all the status IDs referenced in this iterable.
    val statusIds = buildSet {
        this@asTSQ.map {
            add(it.actionableId)
            it.reblog?.let { (it.quote as? Status.Quote.FullQuote)?.let { add(it.status.actionableId) } }
            (it.quote as? Status.Quote.FullQuote)?.let { add(it.status.actionableId) }
        }
    }
    // Populate the caches in two database lookups.
    val viewDataCache = statusRepository.getStatusViewData(pachliAccountId, statusIds)
    val translationCache = statusRepository.getTranslations(pachliAccountId, statusIds)

    return map { status ->
        TSQ(
            timelineStatus = TimelineStatusWithAccount(
                status = status.asEntity(pachliAccountId),
                account = status.reblog?.account?.asEntity(pachliAccountId) ?: status.account.asEntity(pachliAccountId),
                reblogAccount = status.reblog?.let { status.account.asEntity(pachliAccountId) },
                viewData = viewDataCache[status.actionableId],
                translatedStatus = translationCache[status.actionableId],
            ),
            quotedStatus = (status.quote as? Status.Quote.FullQuote)?.status?.let { q ->
                TimelineStatusWithAccount(
                    status = q.asEntity(pachliAccountId),
                    account = q.account.asEntity(pachliAccountId),
                    reblogAccount = null,
                    viewData = viewDataCache[q.actionableId],
                    translatedStatus = translationCache[q.actionableId],
                )
            },
        )
    }
}
