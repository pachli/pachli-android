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
import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.Transaction
import app.pachli.core.accounts.AccountManager
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.RemoteKeyKind
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.database.model.TimelineStatusEntity
import app.pachli.core.database.model.TimelineStatusWithAccount
import app.pachli.core.network.model.Links
import app.pachli.core.network.model.Status
import app.pachli.core.network.retrofit.MastodonApi
import com.squareup.moshi.Moshi
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import retrofit2.HttpException
import retrofit2.Response
import timber.log.Timber

@OptIn(ExperimentalPagingApi::class)
class CachedTimelineRemoteMediator(
    private val initialKey: String?,
    private val api: MastodonApi,
    accountManager: AccountManager,
    private val factory: InvalidatingPagingSourceFactory<Int, TimelineStatusWithAccount>,
    private val transactionProvider: TransactionProvider,
    private val timelineDao: TimelineDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val moshi: Moshi,
) : RemoteMediator<Int, TimelineStatusWithAccount>() {
    private val activeAccount = accountManager.activeAccount!!

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, TimelineStatusWithAccount>,
    ): MediatorResult {
        if (!activeAccount.isLoggedIn()) {
            return MediatorResult.Success(endOfPaginationReached = true)
        }

        Timber.d("load(), LoadType = %s", loadType)

        return try {
            val response = when (loadType) {
                LoadType.REFRESH -> {
                    Timber.e(RuntimeException(), "Stack trace for LoadType.REFRESH")
                    val closestItem = state.anchorPosition?.let {
                        state.closestItemToPosition(maxOf(0, it - (state.config.pageSize / 2)))
                    }?.status?.serverId
                    val statusId = closestItem ?: initialKey
                    Timber.d("Loading from item: %s", statusId)
                    getInitialPage(statusId, state.config.pageSize)
                }
                LoadType.APPEND -> {
                    val rke = remoteKeyDao.remoteKeyForKind(
                        activeAccount.id,
                        TIMELINE_ID,
                        RemoteKeyKind.NEXT,
                    ) ?: return MediatorResult.Success(endOfPaginationReached = true)
                    Timber.d("Loading from remoteKey: %s", rke)
                    api.homeTimeline(maxId = rke.key, limit = state.config.pageSize)
                }
                LoadType.PREPEND -> {
                    val rke = remoteKeyDao.remoteKeyForKind(
                        activeAccount.id,
                        TIMELINE_ID,
                        RemoteKeyKind.PREV,
                    ) ?: return MediatorResult.Success(endOfPaginationReached = true)
                    Timber.d("Loading from remoteKey: %s", rke)
                    api.homeTimeline(minId = rke.key, limit = state.config.pageSize)
                }
            }

            val statuses = response.body()
            if (!response.isSuccessful || statuses == null) {
                return MediatorResult.Error(HttpException(response))
            }

            Timber.d("%d - # statuses loaded", statuses.size)

            // This request succeeded with no new data, and pagination ends (unless this is a
            // REFRESH, which must always set endOfPaginationReached to false).
            if (statuses.isEmpty()) {
                factory.invalidate()
                return MediatorResult.Success(endOfPaginationReached = loadType != LoadType.REFRESH)
            }

            Timber.d("  %s..%s", statuses.first().id, statuses.last().id)

            val links = Links.from(response.headers()["link"])

            transactionProvider {
                when (loadType) {
                    LoadType.REFRESH -> {
                        remoteKeyDao.delete(activeAccount.id)
                        timelineDao.removeAllStatuses(activeAccount.id)

                        remoteKeyDao.upsert(
                            RemoteKeyEntity(
                                activeAccount.id,
                                TIMELINE_ID,
                                RemoteKeyKind.NEXT,
                                links.next,
                            ),
                        )
                        remoteKeyDao.upsert(
                            RemoteKeyEntity(
                                activeAccount.id,
                                TIMELINE_ID,
                                RemoteKeyKind.PREV,
                                links.prev,
                            ),
                        )
                    }
                    // links.prev may be null if there are no statuses, only set if non-null,
                    // https://github.com/mastodon/mastodon/issues/25760
                    LoadType.PREPEND -> links.prev?.let { prev ->
                        remoteKeyDao.upsert(
                            RemoteKeyEntity(
                                activeAccount.id,
                                TIMELINE_ID,
                                RemoteKeyKind.PREV,
                                prev,
                            ),
                        )
                    }
                    // links.next may be null if there are no statuses, only set if non-null,
                    // https://github.com/mastodon/mastodon/issues/25760
                    LoadType.APPEND -> links.next?.let { next ->
                        remoteKeyDao.upsert(
                            RemoteKeyEntity(
                                activeAccount.id,
                                TIMELINE_ID,
                                RemoteKeyKind.NEXT,
                                next,
                            ),
                        )
                    }
                }
                insertStatuses(statuses)
            }

            return MediatorResult.Success(endOfPaginationReached = false)
        } catch (e: IOException) {
            MediatorResult.Error(e)
        } catch (e: HttpException) {
            MediatorResult.Error(e)
        }
    }

    /**
     * Fetch the initial page of statuses, using key as the ID of the initial status to fetch.
     *
     * - If there is no key, a page of the most recent statuses is returned
     * - If the status exists a page that contains the status, and the statuses immediately
     *   before and after it are returned. This provides enough content that the list adapater
     *   can restore the user's reading position.
     * - If the status does not exist the page of statuses immediately before is returned (if
     *   non-empty)
     * - If there is no page of statuses immediately before then the page immediately after is
     *   returned (if non-empty)
     * - Finally, fall back to the most recent statuses
     */
    private suspend fun getInitialPage(statusId: String?, pageSize: Int): Response<List<Status>> = coroutineScope {
        // If the key is null this is straightforward, just return the most recent statuses.
        statusId ?: return@coroutineScope api.homeTimeline(limit = pageSize)

        // It's important to return *something* from this state. If an empty page is returned
        // (even with next/prev links) Pager3 assumes there is no more data to load and stops.
        //
        // In addition, the Mastodon API does not let you fetch a page that contains a given key.
        // You can fetch the page immediately before the key, or the page immediately after, but
        // you can not fetch the page itself.

        // Fetch the requested status, and the page immediately after (next)
        val deferredStatus = async { api.status(statusId = statusId) }
        val deferredNextPage = async {
            api.homeTimeline(maxId = statusId, limit = pageSize)
        }

        deferredStatus.await().getOrNull()?.let { status ->
            val statuses = buildList {
                this.add(status)
                deferredNextPage.await().body()?.let { this.addAll(it) }
            }

            // "statuses" now contains at least one status we can return, and
            // hopefully a full page.

            // Build correct max_id and min_id links for the response. The "min_id" to use
            // when fetching the next page is the same as "key". The "max_id" is the ID of
            // the oldest status in the list.
            val minId = statuses.first().id
            val maxId = statuses.last().id
            val headers = Headers.Builder()
                .add("link: </?max_id=$maxId>; rel=\"next\", </?min_id=$minId>; rel=\"prev\"")
                .build()

            return@coroutineScope Response.success(statuses, headers)
        }

        // The user's last read status was missing. Use the page of statuses chronologically older
        // than their desired status. This page must *not* be empty (as noted earlier, if it is,
        // paging stops).
        deferredNextPage.await().let { response ->
            if (response.isSuccessful) {
                if (!response.body().isNullOrEmpty()) return@coroutineScope response
            }
        }

        // There were no statuses older than the user's desired status. Return the page
        // of statuses immediately newer than their desired status. This page must
        // *not* be empty (as noted earlier, if it is, paging stops).
        api.homeTimeline(minId = statusId, limit = pageSize).let { response ->
            if (response.isSuccessful) {
                if (!response.body().isNullOrEmpty()) return@coroutineScope response
            }
        }

        // Everything failed -- fallback to fetching the most recent statuses
        return@coroutineScope api.homeTimeline(limit = pageSize)
    }

    /**
     * Inserts `statuses` and the accounts referenced by those statuses in to the cache.
     */
    @Transaction
    private suspend fun insertStatuses(statuses: List<Status>) {
        for (status in statuses) {
            timelineDao.insertAccount(TimelineAccountEntity.from(status.account, activeAccount.id, moshi))
            status.reblog?.account?.let {
                val account = TimelineAccountEntity.from(it, activeAccount.id, moshi)
                timelineDao.insertAccount(account)
            }

            timelineDao.insertStatus(
                TimelineStatusEntity.from(
                    status,
                    timelineUserId = activeAccount.id,
                    moshi = moshi,
                ),
            )
        }
    }

    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val TIMELINE_ID = "HOME"
    }
}
