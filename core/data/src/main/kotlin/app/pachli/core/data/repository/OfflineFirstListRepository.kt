/*
 * Copyright 2024 Pachli Association
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

package app.pachli.core.data.repository

import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.source.ListsLocalDataSource
import app.pachli.core.data.source.ListsRemoteDataSource
import app.pachli.core.database.model.asEntity
import app.pachli.core.database.model.asModel
import app.pachli.core.model.MastodonList
import app.pachli.core.model.UserListRepliesPolicy
import app.pachli.core.network.model.asModel
import app.pachli.core.network.model.asNetworkModel
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.map

/**
 * Repository for lists that caches information locally.
 *
 * - Methods that query list data always return from the cache.
 * - Methods that query list membership always query the remote server.
 * - Methods that update data update the remote server first, and cache
 * successful responses.
 * - Call [refresh] to update the local cache.
 */
@Singleton
internal class OfflineFirstListRepository @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val localDataSource: ListsLocalDataSource,
    private val remoteDataSource: ListsRemoteDataSource,
) : ListsRepository {
    override suspend fun refresh(pachliAccountId: Long): Result<List<MastodonList>, ListsError.Retrieve> = externalScope.async {
        remoteDataSource.getLists().map { it.asModel() }
            .onSuccess { localDataSource.replace(pachliAccountId, it.asEntity(pachliAccountId)) }
    }.await()

    override fun getLists(pachliAccountId: Long) = localDataSource.getLists(pachliAccountId).map {
        it.asModel()
    }

    fun x() = localDataSource.getAllLists().map {
        it.groupBy { it.accountId }.mapValues { it.value.asModel() }
    }

    override fun getListsFlow() = localDataSource.getAllLists().map {
        it.groupBy { it.accountId }.mapValues { it.value.asModel() }
    }

    override suspend fun createList(pachliAccountId: Long, title: String, exclusive: Boolean, repliesPolicy: UserListRepliesPolicy) = externalScope.async {
        remoteDataSource.createList(pachliAccountId, title, exclusive, repliesPolicy.asNetworkModel())
            .map { it.asModel() }
            .onSuccess { localDataSource.saveList(it.asEntity(pachliAccountId)) }
    }.await()

    override suspend fun updateList(pachliAccountId: Long, listId: String, title: String, exclusive: Boolean, repliesPolicy: UserListRepliesPolicy) = externalScope.async {
        remoteDataSource.updateList(pachliAccountId, listId, title, exclusive, repliesPolicy.asNetworkModel())
            .map { it.asModel() }
            .onSuccess { localDataSource.updateList(it.asEntity(pachliAccountId)) }
    }.await()

    override suspend fun deleteList(pachliAccountId: Long, list: MastodonList) = externalScope.async {
        remoteDataSource.deleteList(pachliAccountId, list.listId)
            .onSuccess { localDataSource.deleteList(list.asEntity(pachliAccountId)) }
            .map { }
    }.await()

    override suspend fun getListsWithAccount(pachliAccountId: Long, accountId: String) = remoteDataSource.getListsWithAccount(pachliAccountId, accountId)
        .map { it.asModel() }

    override suspend fun getAccountsInList(pachliAccountId: Long, listId: String) = remoteDataSource.getAccountsInList(pachliAccountId, listId)
        .map { it.asModel() }

    override suspend fun addAccountsToList(pachliAccountId: Long, listId: String, accountIds: List<String>): Result<Unit, ListsError.AddAccounts> = externalScope.async {
        remoteDataSource.addAccountsToList(pachliAccountId, listId, accountIds).map { }
    }.await()

    override suspend fun deleteAccountsFromList(pachliAccountId: Long, listId: String, accountIds: List<String>): Result<Unit, ListsError.DeleteAccounts> = externalScope.async {
        remoteDataSource.deleteAccountsFromList(pachliAccountId, listId, accountIds).map { }
    }.await()
}
