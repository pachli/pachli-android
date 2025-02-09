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
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.StatusDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.ConversationData
import app.pachli.core.database.model.RemoteKeyEntity.RemoteKeyKind
import app.pachli.core.network.retrofit.MastodonApi
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

class ConversationsRepository @Inject constructor(
    @ApplicationScope internal val externalScope: CoroutineScope,
    private val mastodonApi: MastodonApi,
    private val transactionProvider: TransactionProvider,
    private val remoteKeyDao: RemoteKeyDao,
    private val conversationsDao: ConversationsDao,
    private val statusDao: StatusDao,
    private val timelineDao: TimelineDao,

) {
    private var factory: InvalidatingPagingSourceFactory<Int, ConversationData>? = null

    @OptIn(ExperimentalPagingApi::class)
    suspend fun conversations(pachliAccountId: Long): Flow<PagingData<ConversationData>> {
        factory = InvalidatingPagingSourceFactory { conversationsDao.conversationsForAccount(pachliAccountId) }

        // Room is row-keyed, not item-keyed. Find the user's REFRESH key, then find the
        // row of the notification with that ID, and use that as the Pager's initialKey.
        val initialKey = remoteKeyDao.remoteKeyForKind(pachliAccountId, RKE_TIMELINE_ID, RemoteKeyKind.REFRESH)?.key
//        val row = initialKey?.let { conversationsDao.getConversationRowNumber(pachliAccountId, it) }
        val row = 0

        return Pager(
            initialKey = row,
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

    companion object {
        private const val PAGE_SIZE = 30

        internal const val RKE_TIMELINE_ID = "CONVERSATIONS"
    }
}
