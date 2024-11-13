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

import app.pachli.core.data.repository.ListsError
import app.pachli.core.data.repository.ListsError.GetListsWithAccount
import app.pachli.core.network.model.UserListRepliesPolicy
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.mapError
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListsRemoteDataSource @Inject constructor(
    private val mastodonApi: MastodonApi,
) {
    suspend fun getLists() = mastodonApi.getLists()
        .mapEither({ it.body }, { ListsError.Retrieve(it) })

    suspend fun createList(pachliAccountId: Long, title: String, exclusive: Boolean, repliesPolicy: UserListRepliesPolicy) =
        mastodonApi.createList(title, exclusive, repliesPolicy)
            .mapEither({ it.body }, { ListsError.Create(it) })

    suspend fun updateList(pachliAccountId: Long, listId: String, title: String, exclusive: Boolean, repliesPolicy: UserListRepliesPolicy) =
        mastodonApi.updateList(listId, title, exclusive, repliesPolicy)
            .mapEither({ it.body }, { ListsError.Update(it) })

    suspend fun deleteList(pachliAccountId: Long, listId: String) =
        mastodonApi.deleteList(listId)
            .mapError { ListsError.Delete(it) }

    /**
     * @return Lists owned by [pachliAccountId] that contain [accountId].
     */
    suspend fun getListsWithAccount(pachliAccountId: Long, accountId: String) =
        mastodonApi.getListsIncludesAccount(accountId)
            .mapEither({ it.body }, { GetListsWithAccount(accountId, it) })

    suspend fun getAccountsInList(pachliAccountId: Long, listId: String) =
        mastodonApi.getAccountsInList(listId, 0)
            .mapEither({ it.body }, { ListsError.GetAccounts(listId, it) })

    suspend fun addAccountsToList(pachliAccountId: Long, listId: String, accountIds: List<String>) =
        mastodonApi.addAccountToList(listId, accountIds)
            .mapError { ListsError.AddAccounts(listId, it) }

    suspend fun deleteAccountsFromList(pachliAccountId: Long, listId: String, accountIds: List<String>) =
        mastodonApi.deleteAccountFromList(listId, accountIds)
            .mapError { ListsError.DeleteAccounts(listId, it) }
}
