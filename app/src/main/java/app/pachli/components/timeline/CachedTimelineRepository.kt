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
import app.pachli.components.timeline.viewmodel.CachedTimelineRemoteMediator
import app.pachli.core.accounts.AccountManager
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.dao.TranslatedStatusDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.StatusViewDataEntity
import app.pachli.core.database.model.TimelineStatusWithAccount
import app.pachli.core.database.model.TranslatedStatusEntity
import app.pachli.core.database.model.TranslationState
import app.pachli.core.network.model.TimelineKind
import app.pachli.core.network.model.Translation
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.util.EmptyPagingSource
import app.pachli.viewdata.StatusViewData
import at.connyduck.calladapter.networkresult.NetworkResult
import at.connyduck.calladapter.networkresult.fold
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
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
    private val accountManager: AccountManager,
    private val transactionProvider: TransactionProvider,
    val timelineDao: TimelineDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val translatedStatusDao: TranslatedStatusDao,
    private val moshi: Moshi,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private var factory: InvalidatingPagingSourceFactory<Int, TimelineStatusWithAccount>? = null

    private var activeAccount = accountManager.activeAccount

    /** @return flow of Mastodon [TimelineStatusWithAccount], loaded in [pageSize] increments */
    @OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
    fun getStatusStream(
        kind: TimelineKind,
        pageSize: Int = PAGE_SIZE,
        initialKey: String? = null,
    ): Flow<PagingData<TimelineStatusWithAccount>> {
        Timber.d("getStatusStream(): key: $initialKey")

        return accountManager.activeAccountFlow.flatMapLatest {
            activeAccount = it

            factory = InvalidatingPagingSourceFactory {
                activeAccount?.let { timelineDao.getStatuses(it.id) } ?: EmptyPagingSource()
            }

            val row = initialKey?.let { key ->
                // Room is row-keyed (by Int), not item-keyed, so the status ID string that was
                // passed as `initialKey` won't work.
                //
                // Instead, get all the status IDs for this account, in timeline order, and find the
                // row index that contains the status. The row index is the correct initialKey.
                activeAccount?.let { account ->
                    timelineDao.getStatusRowNumber(account.id)
                        .indexOfFirst { it == key }.takeIf { it != -1 }
                }
            }

            Timber.d("initialKey: $initialKey is row: $row")

            Pager(
                config = PagingConfig(
                    pageSize = pageSize,
                    jumpThreshold = PAGE_SIZE * 3,
                    enablePlaceholders = true,
                ),
                initialKey = row,
                remoteMediator = CachedTimelineRemoteMediator(
                    initialKey,
                    mastodonApi,
                    accountManager,
                    factory!!,
                    transactionProvider,
                    timelineDao,
                    remoteKeyDao,
                    moshi,
                ),
                pagingSourceFactory = factory!!,
            ).flow
        }
    }

    /** Invalidate the active paging source, see [androidx.paging.PagingSource.invalidate] */
    suspend fun invalidate() {
        // Invalidating when no statuses have been loaded can cause empty timelines because it
        // cancels the network load.
        if (timelineDao.getStatusCount(activeAccount!!.id) < 1) {
            return
        }

        factory?.invalidate()
    }

    suspend fun saveStatusViewData(statusViewData: StatusViewData) = externalScope.launch {
        timelineDao.upsertStatusViewData(
            StatusViewDataEntity(
                serverId = statusViewData.actionableId,
                timelineUserId = activeAccount!!.id,
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
    suspend fun getStatusViewData(statusId: List<String>): Map<String, StatusViewDataEntity> {
        return timelineDao.getStatusViewData(activeAccount!!.id, statusId)
    }

    /**
     * @return Map between statusIDs and any translations for them cached in the repository.
     */
    suspend fun getStatusTranslations(statusIds: List<String>): Map<String, TranslatedStatusEntity> {
        return translatedStatusDao.getTranslations(activeAccount!!.id, statusIds)
    }

    /** Remove all statuses authored/boosted by the given account, for the active account */
    suspend fun removeAllByAccountId(accountId: String) = externalScope.launch {
        timelineDao.removeAllByUser(activeAccount!!.id, accountId)
    }.join()

    /** Remove all statuses from the given instance, for the active account */
    suspend fun removeAllByInstance(instance: String) = externalScope.launch {
        timelineDao.deleteAllFromInstance(activeAccount!!.id, instance)
    }.join()

    /** Clear the warning (remove the "filtered" setting) for the given status, for the active account */
    suspend fun clearStatusWarning(statusId: String) = externalScope.launch {
        timelineDao.clearWarning(activeAccount!!.id, statusId)
    }.join()

    /** Remove all statuses and invalidate the pager, for the active account */
    suspend fun clearAndReload() = externalScope.launch {
        timelineDao.removeAll(activeAccount!!.id)
        factory?.invalidate()
    }.join()

    suspend fun clearAndReloadFromNewest() = externalScope.launch {
        activeAccount?.let {
            timelineDao.removeAll(it.id)
            remoteKeyDao.delete(it.id)
            invalidate()
        }
    }

    suspend fun translate(statusViewData: StatusViewData): NetworkResult<Translation> {
        saveStatusViewData(statusViewData.copy(translationState = TranslationState.TRANSLATING))
        val translation = mastodonApi.translate(statusViewData.actionableId)
        translation.fold({
            translatedStatusDao.upsert(
                TranslatedStatusEntity(
                    serverId = statusViewData.actionableId,
                    timelineUserId = activeAccount!!.id,
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
        }, {
            // Reset the translation state
            saveStatusViewData(statusViewData)
        })
        return translation
    }

    suspend fun translateUndo(statusViewData: StatusViewData) {
        saveStatusViewData(statusViewData.copy(translationState = TranslationState.SHOW_ORIGINAL))
    }

    companion object {
        private const val PAGE_SIZE = 30
    }
}
