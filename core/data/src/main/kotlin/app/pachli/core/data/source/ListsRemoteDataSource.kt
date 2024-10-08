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

package app.pachli.core.data.source

import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.repository.ListsError
import app.pachli.core.data.repository.ListsError.GetListsWithAccount
import app.pachli.core.network.model.UserListRepliesPolicy
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.mapError
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

@Singleton
class ListsRemoteDataSource @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val mastodonApi: MastodonApi,
) {
    suspend fun getLists() = externalScope.async {
        mastodonApi.getLists()
            .mapEither({ it.body }, { ListsError.Retrieve(it) })
    }.await()

    suspend fun createList(pachliAccountId: Long, title: String, exclusive: Boolean, repliesPolicy: UserListRepliesPolicy) = externalScope.async {
        mastodonApi.createList(title, exclusive, repliesPolicy)
            .mapEither({ it.body }, { ListsError.Create(it) })
    }.await()

    suspend fun updateList(pachliAccountId: Long, listId: String, title: String, exclusive: Boolean, repliesPolicy: UserListRepliesPolicy) = externalScope.async {
        mastodonApi.updateList(listId, title, exclusive, repliesPolicy)
            .mapEither({ it.body }, { ListsError.Update(it) })
    }.await()

    suspend fun deleteList(pachliAccountId: Long, listId: String) = externalScope.async {
        mastodonApi.deleteList(listId)
            .mapError { ListsError.Delete(it) }
    }.await()

    /**
     * @return Lists owned by [pachliAccountId] that contain [accountId].
     */
    suspend fun getListsWithAccount(pachliAccountId: Long, accountId: String) = externalScope.async {
        mastodonApi.getListsIncludesAccount(accountId)
            .mapEither({ it.body }, { GetListsWithAccount(accountId, it) })
    }.await()

    suspend fun getAccountsInList(pachliAccountId: Long, listId: String) = externalScope.async {
        mastodonApi.getAccountsInList(listId, 0)
            .mapEither({ it.body }, { ListsError.GetAccounts(listId, it) })
    }.await()

    suspend fun addAccountsToList(pachliAccountId: Long, listId: String, accountIds: List<String>) = externalScope.async {
        mastodonApi.addAccountToList(listId, accountIds)
            .mapError { ListsError.AddAccounts(listId, it) }
    }.await()

    suspend fun deleteAccountsFromList(pachliAccountId: Long, listId: String, accountIds: List<String>) = externalScope.async {
        mastodonApi.deleteAccountFromList(listId, accountIds)
            .mapError { ListsError.DeleteAccounts(listId, it) }
    }.await()
}
