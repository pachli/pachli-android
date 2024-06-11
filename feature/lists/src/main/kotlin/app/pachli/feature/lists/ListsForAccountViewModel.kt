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
import app.pachli.core.data.repository.HasListId
import app.pachli.core.data.repository.Lists
import app.pachli.core.data.repository.ListsError
import app.pachli.core.data.repository.ListsRepository
import app.pachli.core.network.model.MastoList
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.collections.set
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okhttp3.internal.toImmutableMap

sealed interface ListsWithMembership {
    data object Loading : ListsWithMembership
    data class Loaded(val listsWithMembership: Map<String, ListWithMembership>) : ListsWithMembership
}

/**
 * A [MastoList] with a property for whether [ListsForAccountViewModel.accountId] is a
 * member of the list.
 *
 * @property list The Mastodon list
 * @property isMember True if this list contains [ListsForAccountViewModel.accountId]
 */
data class ListWithMembership(
    val list: MastoList,
    val isMember: Boolean,
)

@HiltViewModel(assistedFactory = ListsForAccountViewModel.Factory::class)
class ListsForAccountViewModel @AssistedInject constructor(
    private val listsRepository: ListsRepository,
    @Assisted val accountId: String,
) : ViewModel() {
    private val _listsWithMembership = MutableStateFlow<Result<ListsWithMembership, FlowError>>(Ok(ListsWithMembership.Loading))
    val listsWithMembership = _listsWithMembership.asStateFlow()

    private val _errors = Channel<HasListId>()
    val errors = _errors.receiveAsFlow()

    private val listsWithMembershipMap = mutableMapOf<String, ListWithMembership>()

    init {
        refresh()
    }

    /**
     * Takes the user's lists, and the subset of those lists that [accountId] is a member of,
     * and merges them to produce a map of [ListWithMembership].
     */
    fun refresh() = viewModelScope.launch {
        _listsWithMembership.value = Ok(ListsWithMembership.Loading)
        listsRepository.lists.collect { result ->
            val lists = result.getOrElse {
                _listsWithMembership.value = Err(Error.Retrieve(it))
                return@collect
            }

            if (lists !is Lists.Loaded) return@collect

            _listsWithMembership.value = with(listsWithMembershipMap) {
                val memberLists = listsRepository.getListsWithAccount(accountId)
                    .getOrElse { return@with Err(Error.GetListsWithAccount(it)) }

                clear()

                memberLists.forEach { list ->
                    put(list.id, ListWithMembership(list, true))
                }

                lists.lists.forEach { list ->
                    putIfAbsent(list.id, ListWithMembership(list, false))
                }

                Ok(ListsWithMembership.Loaded(listsWithMembershipMap.toImmutableMap()))
            }
        }
    }

    /**
     * Fallibly adds [accountId] to [listId], sending [Error.AddAccounts] on failure.
     */
    fun addAccountToList(listId: String) = viewModelScope.launch {
        // Optimistically update so the UI is snappy
        listsWithMembershipMap[listId]?.let {
            listsWithMembershipMap[listId] = it.copy(isMember = true)
        }

        _listsWithMembership.value = Ok(ListsWithMembership.Loaded(listsWithMembershipMap.toImmutableMap()))

        listsRepository.addAccountsToList(listId, listOf(accountId)).onFailure { error ->
            // Undo the optimistic update
            listsWithMembershipMap[listId]?.let {
                listsWithMembershipMap[listId] = it.copy(isMember = false)
            }

            _listsWithMembership.value = Ok(ListsWithMembership.Loaded(listsWithMembershipMap.toImmutableMap()))

            _errors.send(Error.AddAccounts(error))
        }
    }

    /**
     * Fallibly deletes [accountId] from [listId], sending [Error.DeleteAccounts] on failure.
     */
    fun deleteAccountFromList(listId: String) = viewModelScope.launch {
        // Optimistically update so the UI is snappy
        listsWithMembershipMap[listId]?.let {
            listsWithMembershipMap[listId] = it.copy(isMember = false)
        }
        _listsWithMembership.value = Ok(ListsWithMembership.Loaded(listsWithMembershipMap.toImmutableMap()))

        listsRepository.deleteAccountsFromList(listId, listOf(accountId)).onFailure { error ->
            // Undo the optimistic update
            listsWithMembershipMap[listId]?.let {
                listsWithMembershipMap[listId] = it.copy(isMember = true)
            }

            _listsWithMembership.value = Ok(ListsWithMembership.Loaded(listsWithMembershipMap.toImmutableMap()))

            _errors.send(Error.DeleteAccounts(error))
        }
    }

    /** Create [ListsForAccountViewModel] injecting [accountId] */
    @AssistedFactory
    interface Factory {
        fun create(accountId: String): ListsForAccountViewModel
    }

    /**
     *  Marker for errors that can be part of the [Result] in the
     *  [ListsForAccountViewModel.listsWithMembership] flow
     */
    sealed interface FlowError : Error

    /** Asynchronous errors from network operations */
    sealed interface Error {
        val cause: ListsError

        /** Failed to fetch lists, or lists containing a particular account */
        @JvmInline
        value class GetListsWithAccount(override val cause: ListsError.GetListsWithAccount) : FlowError, Error

        @JvmInline
        value class Retrieve(override val cause: ListsError.Retrieve) : FlowError, Error

        @JvmInline
        value class AddAccounts(override val cause: ListsError.AddAccounts) : Error, HasListId by cause

        @JvmInline
        value class DeleteAccounts(override val cause: ListsError.DeleteAccounts) : Error, HasListId by cause
    }
}
