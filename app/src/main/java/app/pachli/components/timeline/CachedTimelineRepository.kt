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

import android.content.Context
import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.filter
import app.pachli.components.timeline.TimelineRepository.Companion.PAGE_SIZE
import app.pachli.components.timeline.viewmodel.CachedTimelineRemoteMediator
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.repository.OfflineFirstStatusRepository
import app.pachli.core.data.repository.StatusRepository
import app.pachli.core.database.dao.CollectionsDao
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.StatusDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.dao.TranslatedStatusDao
import app.pachli.core.database.di.InvalidationTracker
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.RemoteKeyEntity.RemoteKeyKind
import app.pachli.core.database.model.StatusViewDataEntity
import app.pachli.core.database.model.TimelineStatusWithQuote
import app.pachli.core.database.model.TranslatedStatusEntity
import app.pachli.core.model.Timeline
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.ui.getDomain
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

// TODO: This is very similar to NetworkTimelineRepository. They could be merged (and the use
// of the cache be made a parameter to getStatusStream), except that they return Pagers of
// different generic types.
//
// NetworkTimelineRepository factory is <String, Status>, this is <Int, TimelineStatusWithAccount>
//
// Re-writing the caching so that they can use the same types is the TODO.

@Singleton
class CachedTimelineRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val invalidationTracker: InvalidationTracker,
    private val mastodonApi: MastodonApi,
    private val transactionProvider: TransactionProvider,
    private val timelineDao: TimelineDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val translatedStatusDao: TranslatedStatusDao,
    private val statusDao: StatusDao,
    private val collectionsDao: CollectionsDao,
    @ApplicationScope private val externalScope: CoroutineScope,
    statusRepository: OfflineFirstStatusRepository,
) : TimelineRepository<TimelineStatusWithQuote>, StatusRepository by statusRepository {
    private var factory: InvalidatingPagingSourceFactory<Int, TimelineStatusWithQuote>? = null

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

    /** @return flow of Mastodon [TimelineStatusWithQuote]. */
    @OptIn(ExperimentalPagingApi::class)
    override suspend fun getStatusStream(
        pachliAccountId: Long,
        timeline: Timeline,
    ): Flow<PagingData<TimelineStatusWithQuote>> {
        factory = InvalidatingPagingSourceFactory {
            ResolveCollectionCardsPagingSource(pachliAccountId, timelineDao, collectionsDao)
        }

        val initialKey = timeline.remoteKeyTimelineId?.let { timelineId ->
            remoteKeyDao.remoteKeyForKind(pachliAccountId, timelineId, RemoteKeyKind.REFRESH)?.key
        }

        val row = initialKey?.let { timelineDao.getStatusRowNumber(pachliAccountId, it) }

        Timber.d("initialKey: %s is row: %d", initialKey, row)

        hiddenDomains.clear()
        hiddenStatuses.clear()
        hiddenAccounts.clear()

        // The custom paging source means Room doesn't know there's a relationship
        // between the tables. Manually track changes to relevant tables and invalidate
        // invalidate the paging source to ensure the user's clicks to show/hide
        // collections, collection membership changes, etc, are reflected in the UI.
        CoroutineScope(currentCoroutineContext()).launch {
            invalidationTracker.createFlow(
                "TimelineCollectionEntity",
                "CollectionItemEntity",
                "CollectionViewDataEntity",
                emitInitialState = false,
            ).collect { factory?.invalidate() }
        }

        return Pager(
            initialKey = row,
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                enablePlaceholders = true,
            ),
            remoteMediator = CachedTimelineRemoteMediator(
                context,
                mastodonApi,
                pachliAccountId,
                transactionProvider,
                timelineDao,
                remoteKeyDao,
                statusDao,
                collectionsDao,
            ),
            pagingSourceFactory = factory!!,
        ).flow.map { pagingData ->
            pagingData.filter { status ->
                !hiddenStatuses.contains(status.timelineStatus.status.statusId) &&
                    !hiddenStatuses.contains(status.timelineStatus.status.reblogStatusId) &&
                    !hiddenAccounts.contains(status.timelineStatus.status.accountId) &&
                    !hiddenAccounts.contains(status.timelineStatus.status.reblogAccountId) &&
                    !hiddenDomains.contains(getDomain(status.timelineStatus.account.url)) &&
                    !hiddenDomains.contains(getDomain(status.timelineStatus.reblogAccount?.url))
            }
        }
    }

    /** Invalidate the active paging source, see [androidx.paging.PagingSource.invalidate] */
    override suspend fun invalidate(pachliAccountId: Long) {
        // Invalidating when no statuses have been loaded can cause empty timelines because it
        // cancels the network load.
        if (timelineDao.getStatusCount(pachliAccountId) < 1) {
            return
        }

        factory?.invalidate()
    }

    /**
     * @return Map between statusIDs and any viewdata for them cached in the repository.
     */
    suspend fun getStatusViewData(pachliAccountId: Long, statusId: List<String>): Map<String, StatusViewDataEntity> {
        return statusDao.getStatusViewData(pachliAccountId, statusId)
    }

    /**
     * @return Map between statusIDs and any translations for them cached in the repository.
     */
    suspend fun getStatusTranslations(pachliAccountId: Long, statusIds: List<String>): Map<String, TranslatedStatusEntity> {
        return translatedStatusDao.getTranslations(pachliAccountId, statusIds)
    }

    /** Remove all statuses authored/boosted by the given account, for the active account */
    suspend fun removeAllByAccountId(pachliAccountId: Long, accountId: String) = externalScope.launch {
        hiddenAccounts.add(accountId)
        timelineDao.removeAllByAccount(pachliAccountId, accountId)
    }.join()

    /** Remove all statuses from the given instance, for the active account */
    suspend fun removeAllByInstance(pachliAccountId: Long, instance: String) = externalScope.launch {
        hiddenDomains.add(instance)
        timelineDao.deleteAllFromInstance(pachliAccountId, instance)
    }.join()

    fun removeStatusWithId(statusId: String) {
        hiddenStatuses.add(statusId)
        factory?.invalidate()
    }

    /** Clear the warning (remove the "filtered" setting) for the given status, for the active account */
    suspend fun clearStatusWarning(pachliAccountId: Long, statusId: String) = externalScope.launch {
        statusDao.clearWarning(pachliAccountId, statusId)
    }.join()
}

/**
 * PagingSource for [TimelineStatusWithQuote] that resolves the
 * [Status.taggedCollections][app.pachli.core.model.Status.taggedCollections]
 * property and populates the
 * [TimelineStatusWithAccount.collectionCards][app.pachli.core.database.dao.TimelineStatusWithAccount.collectionCards]
 * property.
 *
 * This works around a Room limitation -- a paging source can't have a many-many
 * relation where that relationship might appear at multiple levels of results.
 *
 * From a modeling perspective there's a many-many relationship between the
 * collection IDs in the `taggedCollections` property and the `CollectionEntity`
 * table.
 *
 * However, Room can't represent that relationship.
 *
 * Using a multi-map doesn't work because Paging3 doesn't support them. The
 * many-many relationship would return multiple rows for a single item, which
 * breaks the "limit offset" paging Paging3 uses, as Paging3 expects one row per
 * result.
 *
 * Using Room's [@Relation] annotation and a junction/association table doesn't
 * work because the annotation expects the columns in the results to have
 * consistent names. They don't, because a
 * [TimelineStatusWithAccount][app.pachli.core.database.dao.TimelineStatusWithAccount]
 * might be used on its own (1 level deep, columns have a `s_` prefix), or in a
 * [TimelineStatusWithQuote] (2 levels deep, columns have a `s_s_` prefix) and
 * Room can't figure out how to match them together.
 *
 * To fix this, this paging source wraps [TimelineDao.getStatusWithQuote]. When
 * each page of [TimelineStatusWithQuote] is returned it is checked for collections
 * referenced in the
 * [Status.taggedCollections][app.pachli.core.model.Status.taggedCollections]
 * properties.
 *
 * If any collections are found an additional query is made to fetch all the
 * [CollectionCardViewData][app.pachli.core.database.model.CollectionCardViewData]
 * referenced by the statuses in this page. A modified copy of the page is
 * made that adds the
 * [CollectionCardViewData][app.pachli.core.database.model.CollectionCardViewData]
 * to each page, and the modified page is returned.
 */
private class ResolveCollectionCardsPagingSource(
    private val pachliAccountId: Long,
    private val timelineDao: TimelineDao,
    private val collectionsDao: CollectionsDao,
) : PagingSource<Int, TimelineStatusWithQuote>() {
    val delegate by unsafeLazy { timelineDao.getStatusesWithQuote(pachliAccountId) }

    override fun getRefreshKey(state: PagingState<Int, TimelineStatusWithQuote>): Int? = delegate.getRefreshKey(state)

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TimelineStatusWithQuote> {
        val result = delegate.load(params)

        if (result !is LoadResult.Page) return result

        // Fetch any missing collections and add them to data.

        // Map from statusID to list of collection IDs referenced by that status, in
        // the order the collections are referenced.
        val statusIdToCollectionIds = buildMap {
            result.data.forEach { tsq ->
                put(tsq.timelineStatus.status.actionableId, tsq.timelineStatus.status.taggedCollections.map { it.collectionId })
                tsq.quotedStatus?.let { q ->
                    put(q.status.actionableId, q.status.taggedCollections.map { it.collectionId })
                }
            }
        }

        // Return early if there are no collections.
        if (statusIdToCollectionIds.isEmpty()) return result

        // Fetch the missing collections. Map from collectionId to collection.
        val collectionCardViewData = collectionsDao.getCollectionCardViewData(
            pachliAccountId,
            statusIdToCollectionIds.values.flatten().distinct(),
        ).map { it.asModel() }.associateBy { it.collectionId }

        // Build the modified data for this page.
        //
        // Any TimelineStatusWithAccount that doesn't have taggedCollections is
        // passed through unchanged.
        //
        // For the others, the collectionCards property is set to the collection
        // cards for those tagged collections.
        val modifiedData = result.data.map { tsq ->
            val statusId = tsq.timelineStatus.status.actionableId
            val quotedStatusId = tsq.quotedStatus?.status?.actionableId

            val newTimelineStatusCollectionIds = statusIdToCollectionIds[statusId].orEmpty()
            val newQuotedStatusCollectionIds = quotedStatusId?.let { statusIdToCollectionIds[quotedStatusId] }.orEmpty()

            // Bail early if this status has no collections.
            if (newTimelineStatusCollectionIds.isEmpty() && newQuotedStatusCollectionIds.isEmpty()) return@map tsq

            val newTimelineStatus = if (newTimelineStatusCollectionIds.isEmpty()) {
                tsq.timelineStatus
            } else {
                tsq.timelineStatus.copy(
                    collectionCards = newTimelineStatusCollectionIds.mapNotNull { collectionCardViewData[it] },
                )
            }
            val newQuotedStatus = if (newQuotedStatusCollectionIds.isEmpty()) {
                tsq.quotedStatus
            } else {
                tsq.quotedStatus?.copy(
                    collectionCards = newQuotedStatusCollectionIds.mapNotNull { collectionCardViewData[it] },
                )
            }

            tsq.copy(
                timelineStatus = newTimelineStatus,
                quotedStatus = newQuotedStatus,
            )
        }

        return result.copy(data = modifiedData)
    }
}
