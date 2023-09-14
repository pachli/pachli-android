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

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.Transaction
import androidx.room.withTransaction
import app.pachli.components.timeline.toEntity
import app.pachli.db.AccountManager
import app.pachli.db.AppDatabase
import app.pachli.db.RemoteKeyEntity
import app.pachli.db.RemoteKeyKind
import app.pachli.db.TimelineStatusWithAccount
import app.pachli.entity.Status
import app.pachli.network.Links
import app.pachli.network.MastodonApi
import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException

@OptIn(ExperimentalPagingApi::class)
class CachedTimelineRemoteMediator(
    private val api: MastodonApi,
    accountManager: AccountManager,
    private val factory: InvalidatingPagingSourceFactory<Int, TimelineStatusWithAccount>,
    private val db: AppDatabase,
    private val gson: Gson,
) : RemoteMediator<Int, TimelineStatusWithAccount>() {

    private val timelineDao = db.timelineDao()
    private val remoteKeyDao = db.remoteKeyDao()
    private val activeAccount = accountManager.activeAccount!!

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, TimelineStatusWithAccount>,
    ): MediatorResult {
        if (!activeAccount.isLoggedIn()) {
            return MediatorResult.Success(endOfPaginationReached = true)
        }

        Log.d(TAG, "load(), LoadType = $loadType")

        return try {
            val response = when (loadType) {
                LoadType.REFRESH -> {
                    val key = state.anchorPosition?.let { state.closestItemToPosition(it) }?.status?.serverId
                    Log.d(TAG, "Loading from item close to current position: $key")
                    api.homeTimeline(minId = key, limit = state.config.pageSize)
                }
                LoadType.APPEND -> {
                    val rke = db.withTransaction {
                        remoteKeyDao.remoteKeyForKind(
                            activeAccount.id,
                            TIMELINE_ID,
                            RemoteKeyKind.NEXT,
                        )
                    } ?: return MediatorResult.Success(endOfPaginationReached = true)
                    Log.d(TAG, "Loading from remoteKey: $rke")
                    api.homeTimeline(maxId = rke.key, limit = state.config.pageSize)
                }
                LoadType.PREPEND -> {
                    val rke = db.withTransaction {
                        remoteKeyDao.remoteKeyForKind(
                            activeAccount.id,
                            TIMELINE_ID,
                            RemoteKeyKind.PREV,
                        )
                    } ?: return MediatorResult.Success(endOfPaginationReached = true)
                    Log.d(TAG, "Loading from remoteKey: $rke")
                    api.homeTimeline(minId = rke.key, limit = state.config.pageSize)
                }
            }

            val statuses = response.body()
            if (!response.isSuccessful || statuses == null) {
                return MediatorResult.Error(HttpException(response))
            }

            Log.d(TAG, "${statuses.size} - # statuses loaded")

            // This request succeeded with no new data, and pagination ends (unless this is a
            // REFRESH, which must always set endOfPaginationReached to false).
            if (statuses.isEmpty()) {
                factory.invalidate()
                return MediatorResult.Success(endOfPaginationReached = loadType != LoadType.REFRESH)
            }

            Log.d(TAG, "  ${statuses.first().id}..${statuses.last().id}")

            val links = Links.from(response.headers()["link"])
            db.withTransaction {
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
     * Inserts `statuses` and the accounts referenced by those statuses in to the cache.
     */
    @Transaction
    private suspend fun insertStatuses(statuses: List<Status>) {
        for (status in statuses) {
            timelineDao.insertAccount(status.account.toEntity(activeAccount.id, gson))
            status.reblog?.account?.toEntity(activeAccount.id, gson)?.let { rebloggedAccount ->
                timelineDao.insertAccount(rebloggedAccount)
            }

            timelineDao.insertStatus(
                status.toEntity(
                    timelineUserId = activeAccount.id,
                    gson = gson,
                ),
            )
        }
    }

    companion object {
        private const val TAG = "CachedTimelineRemoteMediator"

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val TIMELINE_ID = "HOME"
    }
}
