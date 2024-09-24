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
import app.pachli.core.data.repository.ListsError.Create
import app.pachli.core.data.repository.ListsError.Delete
import app.pachli.core.data.repository.ListsError.GetListsWithAccount
import app.pachli.core.data.repository.ListsError.Retrieve
import app.pachli.core.data.repository.ListsError.Update
import app.pachli.core.network.model.MastoList
import app.pachli.core.network.model.TimelineAccount
import app.pachli.core.network.model.UserListRepliesPolicy
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.mapError
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

@Singleton
class NetworkListsRepository @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val api: MastodonApi,
) : ListsRepository {
    override suspend fun getLists(): Result<List<MastoList>, ListsError.Retrieve> = binding {
        externalScope.async {
            api.getLists().mapEither(
                { it.body },
                { Retrieve(it) },
            ).bind()
        }.await()
    }

    override suspend fun createList(title: String, exclusive: Boolean, repliesPolicy: UserListRepliesPolicy): Result<MastoList, Create> = binding {
        externalScope.async {
            api.createList(title, exclusive, repliesPolicy).mapError { Create(it) }.bind().run {
                body
            }
        }.await()
    }

    override suspend fun editList(listId: String, title: String, exclusive: Boolean, repliesPolicy: UserListRepliesPolicy): Result<MastoList, Update> = binding {
        externalScope.async {
            api.updateList(listId, title, exclusive, repliesPolicy).mapError { Update(it) }.bind().run {
                body
            }
        }.await()
    }

    override suspend fun deleteList(list: MastodonList): Result<Unit, Delete> = binding {
        externalScope.async {
            api.deleteList(list.listId).mapError { Delete(it) }.bind()
        }.await()
    }

    override suspend fun getListsWithAccount(accountId: String): Result<List<MastoList>, GetListsWithAccount> = binding {
        api.getListsIncludesAccount(accountId).mapError { GetListsWithAccount(accountId, it) }.bind().body
    }

    override suspend fun getAccountsInList(listId: String): Result<List<TimelineAccount>, ListsError.GetAccounts> = binding {
        api.getAccountsInList(listId, 0).mapError { ListsError.GetAccounts(listId, it) }.bind().body
    }

    override suspend fun addAccountsToList(listId: String, accountIds: List<String>): Result<Unit, ListsError.AddAccounts> = binding {
        externalScope.async {
            api.addAccountToList(listId, accountIds).mapError { ListsError.AddAccounts(listId, it) }.bind()
        }.await()
    }

    override suspend fun deleteAccountsFromList(listId: String, accountIds: List<String>): Result<Unit, ListsError.DeleteAccounts> = binding {
        externalScope.async {
            api.deleteAccountFromList(listId, accountIds).mapError { ListsError.DeleteAccounts(listId, it) }.bind()
        }.await()
    }
}
