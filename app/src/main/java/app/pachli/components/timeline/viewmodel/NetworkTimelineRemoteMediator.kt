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

import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.pachli.BuildConfig
import app.pachli.core.accounts.AccountManager
import app.pachli.core.network.model.Status
import app.pachli.core.network.model.TimelineKind
import app.pachli.core.network.retrofit.MastodonApi
import kotlinx.coroutines.CoroutineScope
import retrofit2.HttpException
import retrofit2.Response
import timber.log.Timber
import java.io.IOException

/** Remote mediator for accessing timelines that are not backed by the database. */
@OptIn(ExperimentalPagingApi::class)
class NetworkTimelineRemoteMediator(
    private val viewModelScope: CoroutineScope,
    private val api: MastodonApi,
    accountManager: AccountManager,
    private val factory: InvalidatingPagingSourceFactory<String, Status>,
    private val pageCache: PageCache,
    private val timelineKind: TimelineKind,
) : RemoteMediator<String, Status>() {

    private val activeAccount = accountManager.activeAccount!!

    override suspend fun load(loadType: LoadType, state: PagingState<String, Status>): MediatorResult {
        if (!activeAccount.isLoggedIn()) {
            return MediatorResult.Success(endOfPaginationReached = true)
        }

        return try {
            val key = when (loadType) {
                LoadType.REFRESH -> {
                    // Find the closest page to the current position
                    val itemKey = state.anchorPosition?.let { state.closestItemToPosition(it) }?.id
                    itemKey?.let { ik ->
                        // Find the page that contains the item, so the remote key can be determined

                        // Most Mastodon timelines are ordered by ID, greatest ID first. But not all
                        // (https://github.com/mastodon/documentation/issues/1292 explains that
                        // trends/statuses) isn't. This makes finding the relevant page a little
                        // more complicated.

                        // First, assume that they are ordered by ID, and find the page that should
                        // contain this item.
                        var pageContainingItem = pageCache.floorEntry(ik)?.value

                        // Second, if no page was found it means the statuses are not sorted, and
                        // the entire cache must be searched.
                        if (pageContainingItem == null) {
                            for (page in pageCache.values) {
                                val s = page.data.find { it.id == ik }
                                if (s != null) {
                                    pageContainingItem = page
                                    break
                                }
                            }

                            pageContainingItem ?: throw java.lang.IllegalStateException("$itemKey not found in the pageCache page")
                        }

                        // Double check the item appears in the page
                        if (BuildConfig.DEBUG) {
                            pageContainingItem.data.find { it.id == itemKey }
                                ?: throw java.lang.IllegalStateException("$itemKey not found in returned page")
                        }

                        // The desired key is the prevKey of the page immediately before this one
                        pageCache.lowerEntry(pageContainingItem.data.last().id)?.value?.prevKey
                    }
                }
                LoadType.APPEND -> {
                    pageCache.firstEntry()?.value?.nextKey ?: return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.PREPEND -> {
                    pageCache.lastEntry()?.value?.prevKey ?: return MediatorResult.Success(endOfPaginationReached = true)
                }
            }

            Timber.d("- load(), type = $loadType, key = $key")

            val response = fetchStatusPageByKind(loadType, key, state.config.initialLoadSize)
            val page = Page.tryFrom(response).getOrElse { return MediatorResult.Error(it) }

            val endOfPaginationReached = page.data.isEmpty()
            if (!endOfPaginationReached) {
                synchronized(pageCache) {
                    if (loadType == LoadType.REFRESH) {
                        pageCache.clear()
                    }

                    pageCache.upsert(page)
                    Timber.d("  Page $loadType complete for $timelineKind, now got ${pageCache.size} pages")
                    pageCache.debug()
                }
                Timber.d("  Invalidating paging source")
                factory.invalidate()
            }

            return MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (e: IOException) {
            MediatorResult.Error(e)
        } catch (e: HttpException) {
            MediatorResult.Error(e)
        }
    }

    @Throws(IOException::class, HttpException::class)
    private suspend fun fetchStatusPageByKind(loadType: LoadType, key: String?, loadSize: Int): Response<List<Status>> {
        val (maxId, minId) = when (loadType) {
            // When refreshing fetch a page of statuses that are immediately *newer* than the key
            // This is so that the user's reading position is not lost.
            LoadType.REFRESH -> Pair(null, key)
            // When appending fetch a page of statuses that are immediately *older* than the key
            LoadType.APPEND -> Pair(key, null)
            // When prepending fetch a page of statuses that are immediately *newer* than the key
            LoadType.PREPEND -> Pair(null, key)
        }

        return when (timelineKind) {
            TimelineKind.Bookmarks -> api.bookmarks(maxId = maxId, minId = minId, limit = loadSize)
            TimelineKind.Favourites -> api.favourites(maxId = maxId, minId = minId, limit = loadSize)
            TimelineKind.Home -> api.homeTimeline(maxId = maxId, minId = minId, limit = loadSize)
            TimelineKind.PublicFederated -> api.publicTimeline(local = false, maxId = maxId, minId = minId, limit = loadSize)
            TimelineKind.PublicLocal -> api.publicTimeline(local = true, maxId = maxId, minId = minId, limit = loadSize)
            TimelineKind.TrendingStatuses -> api.trendingStatuses()
            is TimelineKind.Tag -> {
                val firstHashtag = timelineKind.tags.first()
                val additionalHashtags = timelineKind.tags.subList(1, timelineKind.tags.size)
                api.hashtagTimeline(firstHashtag, additionalHashtags, null, maxId = maxId, minId = minId, limit = loadSize)
            }
            is TimelineKind.User.Pinned -> api.accountStatuses(
                timelineKind.id,
                maxId = maxId,
                minId = minId,
                limit = loadSize,
                excludeReplies = null,
                onlyMedia = null,
                pinned = true,
            )
            is TimelineKind.User.Posts -> api.accountStatuses(
                timelineKind.id,
                maxId = maxId,
                minId = minId,
                limit = loadSize,
                excludeReplies = true,
                onlyMedia = null,
                pinned = null,
            )
            is TimelineKind.User.Replies -> api.accountStatuses(
                timelineKind.id,
                maxId = maxId,
                minId = minId,
                limit = loadSize,
                excludeReplies = null,
                onlyMedia = null,
                pinned = null,
            )
            is TimelineKind.UserList -> api.listTimeline(
                timelineKind.id,
                maxId = maxId,
                minId = minId,
                limit = loadSize,
            )
        }
    }
}
