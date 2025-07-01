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

import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.database.dao.ConversationsDao
import app.pachli.core.database.dao.StatusDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.ConversationAccountFilterDecisionUpdate
import app.pachli.core.database.model.ConversationContentFilterActionUpdate
import app.pachli.core.database.model.ConversationData
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.model.FilterAction
import app.pachli.core.network.retrofit.MastodonApi
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ConversationsRepository @Inject constructor(
    @ApplicationScope internal val externalScope: CoroutineScope,
    private val mastodonApi: MastodonApi,
    private val transactionProvider: TransactionProvider,
    private val conversationsDao: ConversationsDao,
    private val statusDao: StatusDao,
    private val timelineDao: TimelineDao,
) {
    private var factory: InvalidatingPagingSourceFactory<Int, ConversationData>? = null

    @OptIn(ExperimentalPagingApi::class)
    fun conversations(pachliAccountId: Long): Flow<PagingData<ConversationData>> {
        factory = InvalidatingPagingSourceFactory { conversationsDao.conversationsForAccount(pachliAccountId) }

        // The Mastodon conversations API does not support fetching a specific conversation
        // so it is not possible to restore the user's reading position.

        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                enablePlaceholders = true,
            ),
            remoteMediator = ConversationsRemoteMediator(
                pachliAccountId,
                mastodonApi,
                transactionProvider,
                conversationsDao,
                statusDao,
                timelineDao,
            ),
            pagingSourceFactory = factory!!,
        ).flow
    }

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
