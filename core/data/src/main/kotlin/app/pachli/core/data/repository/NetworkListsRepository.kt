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

import app.pachli.core.accounts.AccountManager
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.repository.ListsError.Create
import app.pachli.core.data.repository.ListsError.Delete
import app.pachli.core.data.repository.ListsError.GetListsWithAccount
import app.pachli.core.data.repository.ListsError.Retrieve
import app.pachli.core.data.repository.ListsError.Update
import app.pachli.core.network.model.MastoList
import app.pachli.core.network.model.TimelineAccount
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.mapError
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class NetworkListsRepository @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val api: MastodonApi,
    private val accountManager: AccountManager,
) : ListsRepository {
    private val _lists = MutableStateFlow<Result<Lists, Retrieve>>(Ok(Lists.Loading))
    override val lists: StateFlow<Result<Lists, Retrieve>> get() = _lists.asStateFlow()

    init {
        externalScope.launch { accountManager.activeAccountFlow.collect { refresh() } }
    }

    override fun refresh() {
        externalScope.launch {
            _lists.value = Ok(Lists.Loading)
            _lists.value = api.getLists()
                .mapBoth(
                    { Ok(Lists.Loaded(it.body)) },
                    { Err(Retrieve(it)) },
                )
        }
    }

    override suspend fun createList(title: String, exclusive: Boolean): Result<MastoList, Create> = binding {
        externalScope.async {
            api.createList(title, exclusive).mapError { Create(it) }.bind().run {
                refresh()
                body
            }
        }.await()
    }

    override suspend fun editList(listId: String, title: String, exclusive: Boolean): Result<MastoList, Update> = binding {
        externalScope.async {
            api.updateList(listId, title, exclusive).mapError { Update(it) }.bind().run {
                refresh()
                body
            }
        }.await()
    }

    override suspend fun deleteList(listId: String): Result<Unit, Delete> = binding {
        externalScope.async {
            api.deleteList(listId).mapError { Delete(it) }.bind().run { refresh() }
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
