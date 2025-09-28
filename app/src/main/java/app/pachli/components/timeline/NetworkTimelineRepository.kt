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
import androidx.paging.filter
import androidx.paging.map
import app.pachli.components.timeline.TimelineRepository.Companion.PAGE_SIZE
import app.pachli.components.timeline.viewmodel.NetworkTimelinePagingSource
import app.pachli.components.timeline.viewmodel.NetworkTimelineRemoteMediator
import app.pachli.components.timeline.viewmodel.PageCache
import app.pachli.core.data.repository.OfflineFirstStatusRepository
import app.pachli.core.data.repository.StatusRepository
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.di.InvalidationTracker
import app.pachli.core.database.model.RemoteKeyEntity.RemoteKeyKind
import app.pachli.core.database.model.TimelineStatusWithAccount
import app.pachli.core.database.model.asEntity
import app.pachli.core.model.Status
import app.pachli.core.model.Timeline
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.ui.getDomain
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
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
    private val invalidationTracker: InvalidationTracker,
    private val mastodonApi: MastodonApi,
    private val remoteKeyDao: RemoteKeyDao,
    private val statusRepository: OfflineFirstStatusRepository,
) : TimelineRepository<TimelineStatusWithAccount>, StatusRepository by statusRepository {
    private val pageCache = PageCache()

    private var factory: InvalidatingPagingSourceFactory<String, Status>? = null

    /**
     * Domains that should be (temporarily) removed from the timeline because the user
     * has blocked them.
     *
     * The server performs the block asynchronously, this is used to perform post-load
     * filtering on the statuses to apply the block locally as a stop-gap.
     */
    private val hiddenDomains = mutableSetOf<String>()

    /**
     * Status IDs that should be (temporarily) removed from the timeline because the user
     * has blocked them.
     *
     * The server performs the block asynchronously, this is used to perform post-load
     * filtering on the statuses to apply the block locally as a stop-gap.
     */
    private val hiddenStatuses = mutableSetOf<String>()

    /**
     * Account IDs that should be (temporarily) removed from the timeline because the user
     * has blocked them.
     *
     * The server performs the block asynchronously, this is used to perform post-load
     * filtering on the statuses to apply the block locally as a stop-gap.
     */
    private val hiddenAccounts = mutableSetOf<String>()

    /** @return flow of Mastodon [TimelineStatusWithAccount]. */
    @OptIn(ExperimentalPagingApi::class)
    override suspend fun getStatusStream(
        pachliAccountId: Long,
        timeline: Timeline,
    ): Flow<PagingData<TimelineStatusWithAccount>> {
        Timber.d("timeline: $timeline, getStatusStream()")

        val initialKey = timeline.remoteKeyTimelineId?.let { refreshKeyPrimaryKey ->
            remoteKeyDao.remoteKeyForKind(pachliAccountId, refreshKeyPrimaryKey, RemoteKeyKind.REFRESH)
        }?.key

        Timber.d("timeline: $timeline, initialKey: $initialKey")
        factory = InvalidatingPagingSourceFactory {
            NetworkTimelinePagingSource(pageCache)
        }

        // Track changes to tables that might be changed by user actions. Changes to
        // these tables have to invalidate the paging source so the `map` that runs
        // on the `Pager.flow` below can re-run and reflect the changes in the data.
        // This shouldn't outlive the viewmodel scope that called `getStatusStream()`.
        CoroutineScope(coroutineContext).launch {
            invalidationTracker.createFlow("StatusViewDataEntity", emitInitialState = false)
                .collect {
                    Timber.d("timeline: $timeline, tables changed: $it")
                    factory?.invalidate()
                }
        }

        hiddenDomains.clear()
        hiddenStatuses.clear()
        hiddenAccounts.clear()

        return Pager(
            initialKey = initialKey,
            config = PagingConfig(pageSize = PAGE_SIZE),
            remoteMediator = NetworkTimelineRemoteMediator(
                mastodonApi,
                pachliAccountId,
                factory!!,
                pageCache,
                timeline,
                remoteKeyDao,
            ),
            pagingSourceFactory = factory!!,
        ).flow
            .map { pagingData ->
                pagingData.filter { status ->
                    !hiddenStatuses.contains(status.actionableId) &&
                        !hiddenStatuses.contains(status.reblog?.id) &&
                        !hiddenAccounts.contains(status.actionableStatus.account.id) &&
                        !hiddenAccounts.contains(status.account.id) &&
                        !hiddenDomains.contains(getDomain(status.actionableStatus.account.url)) &&
                        !hiddenDomains.contains(getDomain(status.account.url))
                }.map { status ->
                    val statusViewData = statusRepository.getStatusViewData(pachliAccountId, status.actionableId)
                    val translations = statusRepository.getTranslation(pachliAccountId, status.actionableId)
                    TimelineStatusWithAccount(
                        status = status.asEntity(pachliAccountId),
                        account = status.reblog?.account?.asEntity(pachliAccountId) ?: status.account.asEntity(pachliAccountId),
                        reblogAccount = status.reblog?.let { status.account.asEntity(pachliAccountId) },
                        viewData = statusViewData,
                        translatedStatus = translations,
                    )
                }
            }
    }

    override suspend fun invalidate(pachliAccountId: Long) = factory?.invalidate() ?: Unit

    fun invalidate() = factory?.invalidate()

    // Can't update the local cache here, as there's no guarantee the server has
    // propogated the removal to all timelines, so reloading (via invalidate())
    // risks loading a timeline where the account hasn't yet been removed.
    fun removeAllByAccountId(accountId: String) {
        hiddenAccounts.add(accountId)
        invalidate()
    }

    // Can't update the local cache here, as there's no guarantee the server has
    // propogated the removal to all timelines, so reloading (via invalidate())
    // risks loading a timeline where the domain hasn't yet been removed.
    fun removeAllByInstance(instance: String) {
        hiddenDomains.add(instance)
        invalidate()
    }

    // Can't update the local cache here, as there's no guarantee the server has
    // propogated the removal to all timelines, so reloading (via invalidate())
    // risks loading a timeline where the status hasn't yet been removed.
    fun removeStatusWithId(statusId: String) {
        hiddenStatuses.add(statusId)
        invalidate()
    }

    suspend fun updateStatusById(statusId: String, updater: (Status) -> Status) {
        pageCache.withLock { pageCache.updateStatusById(statusId, updater) }
        invalidate()
    }

    suspend fun updateActionableStatusById(statusId: String, updater: (Status) -> Status) {
        pageCache.withLock { pageCache.updateActionableStatusById(statusId, updater) }
        invalidate()
    }
}
