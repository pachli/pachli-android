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
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.RemoteKeyEntity.RemoteKeyKind
import app.pachli.core.database.model.TSQ
import app.pachli.core.model.Timeline
import app.pachli.core.network.model.Status as NetworkStatus
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.apiresult.ApiResponse
import app.pachli.core.network.retrofit.apiresult.ApiResult
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrElse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers
import timber.log.Timber

/**
 * Reference to a function to call that can return a page of data from the API.
 *
 * @param maxId maxId value to pass to Mastodon API.
 * @param minId minId value to pass to Mastodon API.
 * @param limit limit value to pass to Mastodon API.
 */
typealias FetchPage = suspend (maxId: String?, minId: String?, limit: Int) -> ApiResult<List<NetworkStatus>>

/** Remote mediator for accessing timelines that are not backed by the database. */
@OptIn(ExperimentalPagingApi::class)
class NetworkTimelineRemoteMediator(
    private val api: MastodonApi,
    private val pachliAccountId: Long,
    private val factory: InvalidatingPagingSourceFactory<String, TSQ>,
    private val pageCache: PageCache,
    private val timeline: Timeline,
    private val remoteKeyDao: RemoteKeyDao,
) : RemoteMediator<String, TSQ>() {
    override suspend fun load(loadType: LoadType, state: PagingState<String, TSQ>): MediatorResult {
        Timber.d("timeline: $timeline, load(), type: $loadType")

        return pageCache.withLock {
            val remoteKeyTimelineId = timeline.remoteKeyTimelineId

            val page = when (loadType) {
                LoadType.REFRESH -> {
                    // Find the itemkey. This may be legitimately null if there is no key,
                    // or the user is performing a "Load newest" operation.
                    val itemKey = if (remoteKeyTimelineId != null) {
                        remoteKeyDao.remoteKeyForKind(pachliAccountId, remoteKeyTimelineId, RemoteKeyKind.REFRESH)?.key
                    } else {
                        state.anchorPosition?.let { state.closestItemToPosition(it) }?.timelineStatus?.status?.serverId?.let { ik ->
                            // Find the page that contains the item, so the remote key can be determined
                            val pageContainingItem = pageCache.getPageById(ik)

                            // Double check the item appears in the page
                            if (BuildConfig.DEBUG) {
                                pageContainingItem ?: throw java.lang.IllegalStateException("page with $ik not found")
                                pageContainingItem.data.find { it.statusId == ik }
                                    ?: throw java.lang.IllegalStateException("$ik not found in returned page, might be Mastodon bug https://github.com/mastodon/mastodon/issues/30172")
                            }

                            // The desired key is the prevKey of the page immediately before this one
                            pageCache.getPrevPage(pageContainingItem?.prevKey)?.prevKey
                        }
                    }
                    Timber.d("timeline: $timeline, itemKey: $itemKey")
                    Page.tryFrom(getInitialPage(itemKey, state.config.initialLoadSize * PAGE_SIZE_MULTIPLIER))
                }

                LoadType.APPEND -> {
                    val key = pageCache.lastPage?.nextKey ?: return MediatorResult.Success(endOfPaginationReached = true)
                    Page.tryFrom(fetchStatusPageByKind(loadType, key, state.config.pageSize * PAGE_SIZE_MULTIPLIER))
                }

                LoadType.PREPEND -> {
                    val key = pageCache.firstPage?.prevKey ?: return MediatorResult.Success(endOfPaginationReached = true)
                    Page.tryFrom(fetchStatusPageByKind(loadType, key, state.config.pageSize * PAGE_SIZE_MULTIPLIER))
                }
            }.getOrElse { return MediatorResult.Error(it.throwable) }

            Timber.d("- $timeline, load(), type = %s, items: %d", loadType, page.data.size)
            Timber.d("  $timeline, first id: ${page.data.firstOrNull()?.statusId}")
            Timber.d("  $timeline, last  id: ${page.data.lastOrNull()?.statusId}")

            val endOfPaginationReached = when (loadType) {
                LoadType.REFRESH -> page.data.isEmpty() || (page.prevKey == null && page.nextKey == null)
                LoadType.PREPEND -> page.data.isEmpty() || page.prevKey == null
                LoadType.APPEND -> page.data.isEmpty() || page.nextKey == null
            }

            when (loadType) {
                LoadType.REFRESH -> {
                    remoteKeyTimelineId?.let { timelineId ->
                        remoteKeyDao.deletePrevNext(pachliAccountId, timelineId)
                        remoteKeyDao.upsert(
                            RemoteKeyEntity(pachliAccountId, timelineId, RemoteKeyKind.NEXT, page.nextKey),
                        )
                        remoteKeyDao.upsert(
                            RemoteKeyEntity(pachliAccountId, timelineId, RemoteKeyKind.PREV, page.prevKey),
                        )
                    }
                    pageCache.clear()
                    if (page.data.isNotEmpty()) pageCache.add(page)
                }

                LoadType.PREPEND -> {
                    if (remoteKeyTimelineId != null && page.prevKey != null) {
                        remoteKeyDao.upsert(
                            RemoteKeyEntity(pachliAccountId, remoteKeyTimelineId, RemoteKeyKind.PREV, page.prevKey),
                        )
                    }
                    if (page.data.isNotEmpty()) pageCache.prepend(page)
                }

                LoadType.APPEND -> {
                    if (remoteKeyTimelineId != null && page.nextKey != null) {
                        remoteKeyDao.upsert(
                            RemoteKeyEntity(pachliAccountId, remoteKeyTimelineId, RemoteKeyKind.NEXT, page.nextKey),
                        )
                    }
                    if (page.data.isNotEmpty()) pageCache.append(page)
                }
            }
            Timber.d(
                "  Page %s complete for %s, now got %d pages",
                loadType,
                timeline,
                pageCache.size,
            )
            pageCache.debug()

            Timber.d("  Invalidating paging source")
            factory.invalidate()

            MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        }
    }

    /**
     * Fetches the initial page for a timeline. If the timeline supports state
     * restoration then this is page containing the status to restore to, otherwise
     * the most recent page.
     *
     * @param statusId If restoring, the status ID to restore to. Null otherwise.
     * @param pageSize
     * @return The initial page of statuses, depending on the type of [timeline]. If
     * [timeline] supports position restoration then a page of statuses containing
     * [statusId] is returned.
     */
    private suspend fun getInitialPage(statusId: String?, pageSize: Int): ApiResult<List<NetworkStatus>> {
        return when (timeline) {
            Timeline.Home -> getPageAround(statusId, pageSize) { maxId, minId, limit ->
                api.homeTimeline(minId = minId, maxId = maxId, limit = limit)
            }

            Timeline.PublicLocal -> getPageAround(statusId, pageSize) { maxId, minId, limit ->
                api.publicTimeline(local = true, minId = minId, maxId = maxId, limit = limit)
            }

            Timeline.PublicFederated -> getPageAround(statusId, pageSize) { maxId, minId, limit ->
                api.publicTimeline(local = false, minId = minId, maxId = maxId, limit = limit)
            }

            is Timeline.User.Replies -> getPageAround(statusId, pageSize) { maxId, minId, limit ->
                api.accountStatuses(
                    timeline.id,
                    maxId = maxId,
                    minId = minId,
                    limit = limit,
                    excludeReblogs = timeline.excludeReblogs,
                )
            }

            is Timeline.UserList -> getPageAround(statusId, pageSize) { maxId, minId, limit ->
                api.listTimeline(minId = minId, maxId = maxId, limit = limit, listId = timeline.listId)
            }

            else -> fetchStatusPageByKind(LoadType.REFRESH, statusId, pageSize)
        }
    }

    /**
     * Fetches a page of statuses, with [statusId] roughly in the middle of the
     * page.
     *
     * @param statusId ID of the status to load around.
     * @param pageSize Approximate number of statuses wanted. The returned list may
     * be smaller than this if there are not enough statuses before/after [statusId].
     * @param fetchPage Function to use to fetch a page of data from the API.
     * @return A page of statuses with [statusId] roughly in the middle of the page.
     * @see CachedTimelineRemoteMediator.getInitialPage
     */
    private suspend fun getPageAround(
        statusId: String?,
        pageSize: Int,
        fetchPage: FetchPage,
    ): ApiResult<List<NetworkStatus>> = coroutineScope {
        Timber.d("timeline: $timeline, getPageAround, statusId: $statusId")
        statusId ?: return@coroutineScope fetchPage(null, null, pageSize)

        val status = async { api.status(statusId = statusId) }
        val prevPage = async { fetchPage(null, statusId, pageSize / 2) }
        val nextPage = async { fetchPage(statusId, null, pageSize / 2) }

        val statuses = buildList {
            prevPage.await().get()?.let { this.addAll(it.body) }
            status.await().get()?.let { this.add(it.body) }
            nextPage.await().get()?.let { this.addAll(it.body) }
        }

        val minId = statuses.firstOrNull()?.id ?: statusId
        val maxId = statuses.lastOrNull()?.id ?: statusId

        val headers = Headers.Builder()
            .add("link: </?max_id=$maxId>; rel=\"next\", </?min_id=$minId>; rel=\"prev\"")
            .build()

        Timber.d("timeline: $timeline, getPageAround, first id: ${statuses.firstOrNull()?.id}")
        Timber.d("timeline: $timeline, getPageAround, last  id: ${statuses.lastOrNull()?.id}")
        return@coroutineScope Ok(ApiResponse(headers, statuses, 200))
    }

    private suspend fun fetchStatusPageByKind(loadType: LoadType, key: String?, loadSize: Int): ApiResult<List<NetworkStatus>> {
        val (maxId, minId) = when (loadType) {
            // When refreshing fetch a page of statuses that are immediately *newer* than the key
            // This is so that the user's reading position is not lost.
            LoadType.REFRESH -> Pair(null, key)
            // When appending fetch a page of statuses that are immediately *older* than the key
            LoadType.APPEND -> Pair(key, null)
            // When prepending fetch a page of statuses that are immediately *newer* than the key
            LoadType.PREPEND -> Pair(null, key)
        }

        Timber.d("timeline: $timeline, fetchStatusPageByKind: loadType: $loadType, maxId: $maxId, minId: $minId")
        return when (timeline) {
            Timeline.Bookmarks -> api.bookmarks(maxId = maxId, minId = minId, limit = loadSize)
            Timeline.Favourites -> api.favourites(maxId = maxId, minId = minId, limit = loadSize)
            Timeline.Home -> api.homeTimeline(maxId = maxId, minId = minId, limit = loadSize)
            Timeline.PublicFederated -> api.publicTimeline(local = false, maxId = maxId, minId = minId, limit = loadSize)
            Timeline.PublicLocal -> api.publicTimeline(local = true, maxId = maxId, minId = minId, limit = loadSize)
            Timeline.TrendingStatuses -> api.trendingStatuses(limit = LIMIT_TRENDING_STATUSES)
            is Timeline.Link -> api.linkTimeline(url = timeline.url, maxId = maxId, minId = minId, limit = loadSize)
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
                pinned = true,
            )
            is Timeline.User.Posts -> api.accountStatuses(
                timeline.id,
                maxId = maxId,
                minId = minId,
                limit = loadSize,
                excludeReplies = true,
            )
            is Timeline.User.Replies -> api.accountStatuses(
                timeline.id,
                maxId = maxId,
                minId = minId,
                limit = loadSize,
                excludeReblogs = timeline.excludeReblogs,
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

        /**
         * Factor to multiply the page size to load a larger number of posts from the server.
         * See [TimelineRepository.PAGE_SIZE][app.pachli.components.timeline.TimelineRepository.Companion.PAGE_SIZE]
         */
        private const val PAGE_SIZE_MULTIPLIER = 3
    }
}
