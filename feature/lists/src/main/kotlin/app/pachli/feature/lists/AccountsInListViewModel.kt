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

package app.pachli.feature.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.data.repository.ListsError
import app.pachli.core.data.repository.ListsRepository
import app.pachli.core.network.model.TimelineAccount
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.apiresult.ApiError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface SearchResults {
    /** Search not started */
    data object Empty : SearchResults

    /** Search results are loading */
    data object Loading : SearchResults

    /** Search results are loaded, discovered [accounts] */
    data class Loaded(val accounts: List<TimelineAccount>) : SearchResults
}

sealed interface Accounts {
    data object Loading : Accounts
    data class Loaded(val accounts: List<TimelineAccount>) : Accounts
}

@HiltViewModel(assistedFactory = AccountsInListViewModel.Factory::class)
class AccountsInListViewModel @AssistedInject constructor(
    private val api: MastodonApi,
    private val listsRepository: ListsRepository,
    @Assisted val listId: String,
) : ViewModel() {

    private val _accountsInList = MutableStateFlow<Result<Accounts, FlowError.GetAccounts>>(Ok(Accounts.Loading))
    val accountsInList = _accountsInList.asStateFlow()

    private val _searchResults = MutableStateFlow<Result<SearchResults, ApiError>>(Ok(SearchResults.Empty))

    /** Flow of results after calling [search] */
    val searchResults = _searchResults.asStateFlow()

    private val _errors = Channel<Error>()
    val errors = _errors.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _accountsInList.value = Ok(Accounts.Loading)
        _accountsInList.value = listsRepository.getAccountsInList(listId)
            .mapEither(
                { Accounts.Loaded(it) },
                { FlowError.GetAccounts(it) },
            )
    }

    /**
     * Add [account] to [listId], refreshing on success, sending [Error.AddAccounts] on failure
     */
    fun addAccountToList(account: TimelineAccount) = viewModelScope.launch {
        listsRepository.addAccountsToList(listId, listOf(account.id))
            .onSuccess { refresh() }
            .onFailure {
                Timber.e("Failed to add account to list: %s", account.username)
                _errors.send(Error.AddAccounts(it))
            }
    }

    /**
     * Remove [accountId] from [listId], refreshing on success, sending
     * [Error.DeleteAccounts] on failure
     */
    fun deleteAccountFromList(accountId: String) = viewModelScope.launch {
        listsRepository.deleteAccountsFromList(listId, listOf(accountId))
            .onSuccess { refresh() }
            .onFailure {
                Timber.e("Failed to remove account from list: %s", accountId)
                _errors.send(Error.DeleteAccounts(it))
            }
    }

    /** Search for [query] and send results to [searchResults] */
    fun search(query: String) {
        when {
            query.isEmpty() -> _searchResults.value = Ok(SearchResults.Empty)
            query.isBlank() -> _searchResults.value = Ok(SearchResults.Loaded(emptyList()))
            else -> viewModelScope.launch {
                _searchResults.value = api.searchAccounts(query, null, 10, true)
                    .map { SearchResults.Loaded(it.body) }
            }
        }
    }

    /** Create [AccountsInListViewModel] injecting [listId] */
    @AssistedFactory
    interface Factory {
        fun create(listId: String): AccountsInListViewModel
    }

    /**
     * Errors that can be part of the [Result] in the
     * [AccountsInListViewModel.accountsInList] flow
     */
    sealed interface FlowError : ListsError {
        @JvmInline
        value class GetAccounts(private val error: ListsError.GetAccounts) : FlowError, ListsError by error
    }

    /** Asynchronous errors from network operations */
    sealed interface Error : ListsError {
        @JvmInline
        value class AddAccounts(private val error: ListsError.AddAccounts) : Error, ListsError by error

        @JvmInline
        value class DeleteAccounts(private val error: ListsError.DeleteAccounts) : Error, ListsError by error
    }
}
