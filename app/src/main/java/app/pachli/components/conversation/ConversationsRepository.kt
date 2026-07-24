/*
 * Copyright (c) 2025 Pachli Association
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

package app.pachli.components.conversation

import android.content.Context
import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.repository.CollectionsRepository
import app.pachli.core.data.repository.OfflineFirstStatusRepository
import app.pachli.core.data.repository.StatusRepository
import app.pachli.core.database.dao.CollectionsDao
import app.pachli.core.database.dao.ConversationsDao
import app.pachli.core.database.dao.StatusDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.di.InvalidationTracker
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.ConversationAccountFilterDecisionUpdate
import app.pachli.core.database.model.ConversationContentFilterActionUpdate
import app.pachli.core.database.model.ConversationData
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.model.FilterAction
import app.pachli.core.network.retrofit.MastodonApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ConversationsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope internal val externalScope: CoroutineScope,
    private val invalidationTracker: InvalidationTracker,
    private val mastodonApi: MastodonApi,
    private val transactionProvider: TransactionProvider,
    private val conversationsDao: ConversationsDao,
    private val statusDao: StatusDao,
    private val timelineDao: TimelineDao,
    private val collectionsDao: CollectionsDao,
    private val collectionsRepository: CollectionsRepository,
    statusRepository: OfflineFirstStatusRepository,
) : StatusRepository by statusRepository {
    private var factory: InvalidatingPagingSourceFactory<Int, ConversationData>? = null

    @OptIn(ExperimentalPagingApi::class)
    suspend fun conversations(pachliAccountId: Long): Flow<PagingData<ConversationData>> {
        factory = InvalidatingPagingSourceFactory {
            ResolveCollectionCardsPagingSource(pachliAccountId, conversationsDao, collectionsDao)
        }

        // Track changes to tables that might be changed by user actions. Changes to
        // these tables have to invalidate the paging source so the `map` that runs
        // on the `Pager.flow` below can re-run and reflect the changes in the data.
        // This shouldn't outlive the viewmodel scope that called `getStatusStream()`.
        CoroutineScope(currentCoroutineContext()).launch {
            invalidationTracker.createFlow(
                "CollectionViewDataEntity",
                "StatusViewDataEntity",
                emitInitialState = false,
            )
                .collect { factory?.invalidate() }
        }

        // The Mastodon conversations API does not support fetching a specific conversation
        // so it is not possible to restore the user's reading position.

        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                enablePlaceholders = true,
            ),
            remoteMediator = ConversationsRemoteMediator(
                context,
                pachliAccountId,
                mastodonApi,
                transactionProvider,
                conversationsDao,
                statusDao,
                timelineDao,
                collectionsRepository,
            ),
            pagingSourceFactory = factory!!,
        ).flow
    }

    fun invalidate() = factory?.invalidate()

    /**
     * Sets the [FilterAction] for [conversationId] to [FilterAction.NONE]
     *
     * @param pachliAccountId
     * @param conversationId Conversation's server ID.
     */
    fun clearContentFilter(pachliAccountId: Long, conversationId: String) = externalScope.launch {
        conversationsDao.upsert(ConversationContentFilterActionUpdate(pachliAccountId, conversationId, FilterAction.NONE))
    }

    /**
     * Sets the [AccountFilterDecision] for [conversationId] to [accountFilterDecision].
     *
     * @param pachliAccountId
     * @param conversationId Conversation's server ID.
     * @param accountFilterDecision New [AccountFilterDecision].
     */
    fun setAccountFilterDecision(
        pachliAccountId: Long,
        conversationId: String,
        accountFilterDecision: AccountFilterDecision,
    ) = externalScope.launch {
        conversationsDao.upsert(
            ConversationAccountFilterDecisionUpdate(
                pachliAccountId,
                conversationId,
                accountFilterDecision,
            ),
        )
    }

    companion object {
        private const val PAGE_SIZE = 30
    }
}

private class ResolveCollectionCardsPagingSource(
    private val pachliAccountId: Long,
    private val conversationsDao: ConversationsDao,
    private val collectionsDao: CollectionsDao,
) : PagingSource<Int, ConversationData>() {
    val delegate by unsafeLazy { conversationsDao.getConversationsWithQuote(pachliAccountId) }

    override fun getRefreshKey(state: PagingState<Int, ConversationData>): Int? = delegate.getRefreshKey(state)

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ConversationData> {
        val result = delegate.load(params)

        if (result !is LoadResult.Page) return result

        // Fetch any missing collections and add them to data.

        // Map from statusID to list of collection IDs referenced by that status, in
        // the order the collections are referenced.
        val statusIdToCollectionIds = buildMap {
            result.data.forEach { conversationData ->
                val lastStatus = conversationData.lastStatus
                if (lastStatus.timelineStatus.status.taggedCollections.isNotEmpty()) {
                    put(lastStatus.timelineStatus.status.actionableId, lastStatus.timelineStatus.status.taggedCollections.map { it.collectionId })
                }
                if (lastStatus.quotedStatus?.status?.taggedCollections?.isNotEmpty() == true) {
                    lastStatus.quotedStatus?.let { q ->
                        put(q.status.actionableId, q.status.taggedCollections.map { it.collectionId })
                    }
                }
            }
        }

        // Return early if there are no collections.
        if (statusIdToCollectionIds.isEmpty()) return result

        val collectionIds = statusIdToCollectionIds.values.flatten().distinct()

        // Fetch the missing collections. Map from collectionId to collection.
        val collectionCardViewData = collectionsDao.getCollectionCardViewData(
            pachliAccountId,
            collectionIds, // statusIdToCollectionIds.values.flatten().distinct(),
        ).map { it.asModel() }.associateBy { it.collectionId }

        // Build the modified data for this page.
        //
        // Any TimelineStatusWithAccount that doesn't have taggedCollections is
        // passed through unchanged.
        //
        // For the others, the collectionCards property is set to the collection
        // cards for those tagged collections.
        val modifiedData = result.data.map { conversationData ->
            val lastStatus = conversationData.lastStatus
            val statusId = lastStatus.timelineStatus.status.actionableId
            val quotedStatusId = lastStatus.quotedStatus?.status?.actionableId

            val newTimelineStatusCollectionIds = statusIdToCollectionIds[statusId].orEmpty()
            val newQuotedStatusCollectionIds = quotedStatusId?.let { statusIdToCollectionIds[quotedStatusId] }.orEmpty()

            // Bail early if this status has no collections.
            if (newTimelineStatusCollectionIds.isEmpty() && newQuotedStatusCollectionIds.isEmpty()) return@map conversationData

            val newTimelineStatus = if (newTimelineStatusCollectionIds.isEmpty()) {
                lastStatus.timelineStatus
            } else {
                lastStatus.timelineStatus.copy(
                    collectionCards = newTimelineStatusCollectionIds.mapNotNull { collectionCardViewData[it] },
                )
            }
            val newQuotedStatus = if (newQuotedStatusCollectionIds.isEmpty()) {
                lastStatus.quotedStatus
            } else {
                lastStatus.quotedStatus?.copy(
                    collectionCards = newQuotedStatusCollectionIds.mapNotNull { collectionCardViewData[it] },
                )
            }

            conversationData.copy(
                lastStatus = lastStatus.copy(
                    timelineStatus = newTimelineStatus,
                    quotedStatus = newQuotedStatus,
                ),
            )
        }

        return result.copy(data = modifiedData)
    }
}
