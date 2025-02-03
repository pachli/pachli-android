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
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.StatusDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.RemoteKeyEntity.RemoteKeyKind
import app.pachli.core.database.model.StatusEntity
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.database.model.TimelineStatusEntity
import app.pachli.core.database.model.TimelineStatusWithAccount
import app.pachli.core.network.model.Links
import app.pachli.core.network.model.Status
import app.pachli.core.network.retrofit.MastodonApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Headers
import retrofit2.HttpException
import retrofit2.Response
import timber.log.Timber

@OptIn(ExperimentalPagingApi::class)
class CachedTimelineRemoteMediator(
    private val mastodonApi: MastodonApi,
    private val pachliAccountId: Long,
    private val transactionProvider: TransactionProvider,
    private val timelineDao: TimelineDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val statusDao: StatusDao,
) : RemoteMediator<Int, TimelineStatusWithAccount>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, TimelineStatusWithAccount>,
    ): MediatorResult {
        Timber.d("load(), account ID: %d, LoadType = %s", pachliAccountId, loadType)

        return try {
            transactionProvider {
                val response = when (loadType) {
                    LoadType.REFRESH -> {
                        // Ignore the provided state, always try and fetch from the remote
                        // REFRESH key.
                        val statusId = remoteKeyDao.remoteKeyForKind(
                            pachliAccountId,
                            RKE_TIMELINE_ID,
                            RemoteKeyKind.REFRESH,
                        )?.key
                        Timber.d("Refresh from item: %s", statusId)
                        getInitialPage(statusId, state.config.pageSize)
                    }

                    LoadType.PREPEND -> {
                        val rke = remoteKeyDao.remoteKeyForKind(
                            pachliAccountId,
                            RKE_TIMELINE_ID,
                            RemoteKeyKind.PREV,
                        ) ?: return@transactionProvider MediatorResult.Success(endOfPaginationReached = true)
                        Timber.d("Prepend from remoteKey: %s", rke)
                        mastodonApi.homeTimeline(minId = rke.key, limit = state.config.pageSize)
                    }

                    LoadType.APPEND -> {
                        val rke = remoteKeyDao.remoteKeyForKind(
                            pachliAccountId,
                            RKE_TIMELINE_ID,
                            RemoteKeyKind.NEXT,
                        ) ?: return@transactionProvider MediatorResult.Success(endOfPaginationReached = true)
                        Timber.d("Append from remoteKey: %s", rke)
                        mastodonApi.homeTimeline(maxId = rke.key, limit = state.config.pageSize)
                    }
                }

                val statuses = response.body()
                if (!response.isSuccessful || statuses == null) {
                    return@transactionProvider MediatorResult.Error(HttpException(response))
                }

                Timber.d("%d - # statuses loaded", statuses.size)

                // This request succeeded with no new data, and pagination ends (unless this is a
                // REFRESH, which must always set endOfPaginationReached to false).
                if (statuses.isEmpty()) {
                    return@transactionProvider MediatorResult.Success(endOfPaginationReached = loadType != LoadType.REFRESH)
                }

                Timber.d("  %s..%s", statuses.first().id, statuses.last().id)

                val links = Links.from(response.headers()["link"])

                when (loadType) {
                    LoadType.REFRESH -> {
                        timelineDao.deleteAllStatusesForAccountOnTimeline(
                            pachliAccountId,
                            TimelineStatusEntity.Kind.Home,
                        )

                        remoteKeyDao.upsert(
                            RemoteKeyEntity(
                                pachliAccountId,
                                RKE_TIMELINE_ID,
                                RemoteKeyKind.NEXT,
                                links.next,
                            ),
                        )

                        remoteKeyDao.upsert(
                            RemoteKeyEntity(
                                pachliAccountId,
                                RKE_TIMELINE_ID,
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
                                pachliAccountId,
                                RKE_TIMELINE_ID,
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
                                pachliAccountId,
                                RKE_TIMELINE_ID,
                                RemoteKeyKind.NEXT,
                                next,
                            ),
                        )
                    }
                }
                insertStatuses(pachliAccountId, statuses)

                MediatorResult.Success(endOfPaginationReached = false)
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Timber.e(e, "Error loading, LoadType = %s", loadType)
            MediatorResult.Error(e)
        }
    }

    /**
     * @return The initial page of statuses centered on the status with [statusId],
     * or the most recent statuses if [statusId] is null.
     */
    private suspend fun getInitialPage(statusId: String?, pageSize: Int): Response<List<Status>> = coroutineScope {
        statusId ?: return@coroutineScope mastodonApi.homeTimeline(limit = pageSize)

        val status = async { mastodonApi.status(statusId = statusId) }
        val prevPage = async { mastodonApi.homeTimeline(minId = statusId, limit = pageSize * 3) }
        val nextPage = async { mastodonApi.homeTimeline(maxId = statusId, limit = pageSize * 3)        }

        val statuses = buildList {
            prevPage.await().body()?.let { this.addAll(it) }
            status.await().getOrNull()?.let { this.add(it) }
            nextPage.await().body()?.let { this.addAll(it) }
        }

        val minId = statuses.firstOrNull()?.id ?: statusId
        val maxId = statuses.lastOrNull()?.id ?: statusId

        val headers = Headers.Builder()
            .add("link: </?max_id=$maxId>; rel=\"next\", </?min_id=$minId>; rel=\"prev\"")
            .build()

        return@coroutineScope Response.success(statuses, headers)
    }

    /**
     * Inserts `statuses` and the accounts referenced by those statuses in to the cache,
     * then adds references to them in the Home timeline.
     */
    private suspend fun insertStatuses(pachliAccountId: Long, statuses: List<Status>) {
        check(transactionProvider.inTransaction())

        /** Unique accounts referenced in this batch of statuses. */
        val accounts = buildSet {
            statuses.forEach { status ->
                add(status.account)
                status.reblog?.account?.let { add(it) }
            }
        }

        timelineDao.upsertAccounts(accounts.map { TimelineAccountEntity.from(it, pachliAccountId) })
        statusDao.upsertStatuses(statuses.map { StatusEntity.from(it, pachliAccountId) })
        timelineDao.upsertStatuses(
            statuses.map {
                TimelineStatusEntity(
                    kind = TimelineStatusEntity.Kind.Home,
                    pachliAccountId = pachliAccountId,
                    statusId = it.id,
                )
            },
        )
    }

    companion object {
        const val RKE_TIMELINE_ID = "HOME"
    }
}
