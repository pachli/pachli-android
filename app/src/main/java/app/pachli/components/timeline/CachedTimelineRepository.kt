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
import app.pachli.components.timeline.TimelineRepository.Companion.PAGE_SIZE
import app.pachli.components.timeline.viewmodel.CachedTimelineRemoteMediator
import app.pachli.components.timeline.viewmodel.CachedTimelineRemoteMediator.Companion.RKE_TIMELINE_ID
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.StatusDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.dao.TranslatedStatusDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.RemoteKeyEntity.RemoteKeyKind
import app.pachli.core.database.model.StatusViewDataEntity
import app.pachli.core.database.model.TimelineStatusWithAccount
import app.pachli.core.database.model.TranslatedStatusEntity
import app.pachli.core.database.model.TranslationState
import app.pachli.core.model.Timeline
import app.pachli.core.network.model.Translation
import app.pachli.core.network.retrofit.MastodonApi
import at.connyduck.calladapter.networkresult.NetworkResult
import at.connyduck.calladapter.networkresult.fold
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
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
    private val mastodonApi: MastodonApi,
    private val transactionProvider: TransactionProvider,
    val timelineDao: TimelineDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val translatedStatusDao: TranslatedStatusDao,
    private val statusDao: StatusDao,
    @ApplicationScope private val externalScope: CoroutineScope,
) : TimelineRepository<TimelineStatusWithAccount> {
    private var factory: InvalidatingPagingSourceFactory<Int, TimelineStatusWithAccount>? = null

    /** @return flow of Mastodon [TimelineStatusWithAccount. */
    @OptIn(ExperimentalPagingApi::class)
    override suspend fun getStatusStream(
        account: AccountEntity,
        kind: Timeline,
    ): Flow<PagingData<TimelineStatusWithAccount>> {
        Timber.d("getStatusStream, account is %s", account.fullName)

        factory = InvalidatingPagingSourceFactory { timelineDao.getStatuses(account.id) }

        val initialKey = remoteKeyDao.remoteKeyForKind(account.id, RKE_TIMELINE_ID, RemoteKeyKind.REFRESH)?.key
        val row = initialKey?.let { timelineDao.getStatusRowNumber(account.id, it) } ?: 0

        Timber.d("initialKey: %s is row: %d", initialKey, row)

        return Pager(
            initialKey = (row - ((PAGE_SIZE * 3) / 2)).coerceAtLeast(0),
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                enablePlaceholders = true,
            ),
            remoteMediator = CachedTimelineRemoteMediator(
                mastodonApi,
                account.id,
                transactionProvider,
                timelineDao,
                remoteKeyDao,
                statusDao,
            ),
            pagingSourceFactory = factory!!,
        ).flow
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

    suspend fun saveStatusViewData(statusViewData: StatusViewData) = externalScope.launch {
        timelineDao.upsertStatusViewData(
            StatusViewDataEntity(
                serverId = statusViewData.actionableId,
                timelineUserId = statusViewData.pachliAccountId,
                expanded = statusViewData.isExpanded,
                contentShowing = statusViewData.isShowingContent,
                contentCollapsed = statusViewData.isCollapsed,
                translationState = statusViewData.translationState,
            ),
        )
    }.join()

    /**
     * @return Map between statusIDs and any viewdata for them cached in the repository.
     */
    suspend fun getStatusViewData(pachliAccountId: Long, statusId: List<String>): Map<String, StatusViewDataEntity> {
        return timelineDao.getStatusViewData(pachliAccountId, statusId)
    }

    /**
     * @return Map between statusIDs and any translations for them cached in the repository.
     */
    suspend fun getStatusTranslations(pachliAccountId: Long, statusIds: List<String>): Map<String, TranslatedStatusEntity> {
        return translatedStatusDao.getTranslations(pachliAccountId, statusIds)
    }

    /** Remove all statuses authored/boosted by the given account, for the active account */
    suspend fun removeAllByAccountId(pachliAccountId: Long, accountId: String) = externalScope.launch {
        timelineDao.removeAllByUser(pachliAccountId, accountId)
    }.join()

    /** Remove all statuses from the given instance, for the active account */
    suspend fun removeAllByInstance(pachliAccountId: Long, instance: String) = externalScope.launch {
        timelineDao.deleteAllFromInstance(pachliAccountId, instance)
    }.join()

    /** Clear the warning (remove the "filtered" setting) for the given status, for the active account */
    suspend fun clearStatusWarning(pachliAccountId: Long, statusId: String) = externalScope.launch {
        statusDao.clearWarning(pachliAccountId, statusId)
    }.join()

    suspend fun translate(statusViewData: StatusViewData): NetworkResult<Translation> {
        saveStatusViewData(statusViewData.copy(translationState = TranslationState.TRANSLATING))
        val translation = mastodonApi.translate(statusViewData.actionableId)
        translation.fold(
            {
                translatedStatusDao.upsert(
                    TranslatedStatusEntity(
                        serverId = statusViewData.actionableId,
                        timelineUserId = statusViewData.pachliAccountId,
                        // TODO: Should this embed the network type instead of copying data
                        // from one type to another?
                        content = it.content,
                        spoilerText = it.spoilerText,
                        poll = it.poll,
                        attachments = it.attachments,
                        provider = it.provider,
                    ),
                )
                saveStatusViewData(statusViewData.copy(translationState = TranslationState.SHOW_TRANSLATION))
            },
            {
                // Reset the translation state
                saveStatusViewData(statusViewData)
            },
        )
        return translation
    }

    suspend fun translateUndo(statusViewData: StatusViewData) {
        saveStatusViewData(statusViewData.copy(translationState = TranslationState.SHOW_ORIGINAL))
    }

    /**
     * Saves the ID of the status that future refreshes will try and restore
     * from.
     *
     * @param pachliAccountId
     * @param key Status ID to restore from. Null indicates the refresh should
     * refresh the newest statuses.
     */
    suspend fun saveRefreshKey(pachliAccountId: Long, key: String?) = externalScope.async {
        Timber.d("saveRefreshKey: $key")
        remoteKeyDao.upsert(
            RemoteKeyEntity(pachliAccountId, RKE_TIMELINE_ID, RemoteKeyKind.REFRESH, key),
        )
    }.await()

    /**
     * @param pachliAccountId
     * @return The most recent saved refresh key. Null if not set, or the refresh
     * should fetch the latest statuses.
     */
    suspend fun getRefreshKey(pachliAccountId: Long): String? = externalScope.async {
        remoteKeyDao.getRefreshKey(pachliAccountId, RKE_TIMELINE_ID)
    }.await()
}
