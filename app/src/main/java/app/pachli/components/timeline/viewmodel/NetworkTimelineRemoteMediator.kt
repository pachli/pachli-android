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
import app.pachli.core.model.Timeline
import app.pachli.core.network.model.Status
import app.pachli.core.network.retrofit.MastodonApi
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import retrofit2.HttpException
import retrofit2.Response
import timber.log.Timber

/** Remote mediator for accessing timelines that are not backed by the database. */
@OptIn(ExperimentalPagingApi::class)
class NetworkTimelineRemoteMediator(
    private val viewModelScope: CoroutineScope,
    private val api: MastodonApi,
    accountManager: AccountManager,
    private val factory: InvalidatingPagingSourceFactory<String, Status>,
    private val pageCache: PageCache,
    private val timeline: Timeline,
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
                        val pageContainingItem = pageCache.getPageById(ik)

                        // Double check the item appears in the page
                        if (BuildConfig.DEBUG) {
                            pageContainingItem ?: throw java.lang.IllegalStateException("page with $itemKey not found")
                            pageContainingItem.data.find { it.id == itemKey }
                                ?: throw java.lang.IllegalStateException("$itemKey not found in returned page")
                        }

                        // The desired key is the prevKey of the page immediately before this one
                        pageCache.getPrevPage(pageContainingItem?.prevKey)?.prevKey
                    }
                }
                LoadType.APPEND -> {
                    pageCache.lastPage?.nextKey ?: return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.PREPEND -> {
                    pageCache.firstPage?.prevKey ?: return MediatorResult.Success(endOfPaginationReached = true)
                }
            }

            Timber.d("- load(), type = %s, key = %s", loadType, key)

            val response = fetchStatusPageByKind(loadType, key, state.config.initialLoadSize)
            val page = Page.tryFrom(response).getOrElse { return MediatorResult.Error(it) }

            val endOfPaginationReached = page.data.isEmpty()
            if (!endOfPaginationReached) {
                synchronized(pageCache) {
                    pageCache.add(page, loadType)
                    Timber.d(
                        "  Page %s complete for %s, now got %d pages",
                        loadType,
                        timeline,
                        pageCache.size,
                    )
                    pageCache.debug()
                }
                Timber.d("  Invalidating paging source")
                factory.invalidate()
            }

            return MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (e: Exception) {
            Timber.e(e, "Error loading, LoadType = %s", loadType)
            MediatorResult.Error(e)
        }
    }

    @Throws(IOException::class, HttpException::class, IllegalStateException::class)
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

        return when (timeline) {
            Timeline.Bookmarks -> api.bookmarks(maxId = maxId, minId = minId, limit = loadSize)
            Timeline.Favourites -> api.favourites(maxId = maxId, minId = minId, limit = loadSize)
            Timeline.Home -> api.homeTimeline(maxId = maxId, minId = minId, limit = loadSize)
            Timeline.PublicFederated -> api.publicTimeline(local = false, maxId = maxId, minId = minId, limit = loadSize)
            Timeline.PublicLocal -> api.publicTimeline(local = true, maxId = maxId, minId = minId, limit = loadSize)
            Timeline.TrendingStatuses -> api.trendingStatuses(limit = LIMIT_TRENDING_STATUSES)
            is Timeline.Hashtags -> {
                val firstHashtag = timeline.tags.first()
                val additionalHashtags = timeline.tags.subList(1, timeline.tags.size)
                api.hashtagTimeline(firstHashtag, additionalHashtags, null, maxId = maxId, minId = minId, limit = loadSize)
            }
            is Timeline.User.Pinned -> api.accountStatuses(
                timeline.id,
                maxId = maxId,
                minId = minId,
                limit = loadSize,
                excludeReplies = null,
                onlyMedia = null,
                pinned = true,
            )
            is Timeline.User.Posts -> api.accountStatuses(
                timeline.id,
                maxId = maxId,
                minId = minId,
                limit = loadSize,
                excludeReplies = true,
                onlyMedia = null,
                pinned = null,
            )
            is Timeline.User.Replies -> api.accountStatuses(
                timeline.id,
                maxId = maxId,
                minId = minId,
                limit = loadSize,
                excludeReplies = null,
                onlyMedia = null,
                pinned = null,
            )
            is Timeline.UserList -> api.listTimeline(
                timeline.listId,
                maxId = maxId,
                minId = minId,
                limit = loadSize,
            )
            else -> throw IllegalStateException("NetworkTimelineRemoteMediator does not support $timeline")
        }
    }

    companion object {
        /**
         * How many trending statuses to fetch. These are not paged, so fetch the
         * documented (https://docs.joinmastodon.org/methods/trends/#query-parameters-1)
         * maximum.
         */
        const val LIMIT_TRENDING_STATUSES = 40
    }
}
