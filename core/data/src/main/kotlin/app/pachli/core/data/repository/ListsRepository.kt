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

import app.pachli.core.common.PachliError
import app.pachli.core.network.model.MastoList
import app.pachli.core.network.model.TimelineAccount
import app.pachli.core.network.model.UserListRepliesPolicy
import app.pachli.core.network.retrofit.apiresult.ApiError
import com.github.michaelbull.result.Result
import java.text.Collator
import kotlinx.coroutines.flow.StateFlow

sealed interface Lists {
    data object Loading : Lists
    data class Loaded(val lists: List<MastoList>) : Lists
}

/** Marker for errors that include the ID of the list */
interface HasListId {
    val listId: String
}

/** Errors that can be returned from this repository */
interface ListsError : PachliError {
    @JvmInline
    value class Create(private val error: ApiError) : ListsError, PachliError by error

    @JvmInline
    value class Retrieve(private val error: ApiError) : ListsError, PachliError by error

    @JvmInline
    value class Update(private val error: ApiError) : ListsError, PachliError by error

    @JvmInline
    value class Delete(private val error: ApiError) : ListsError, PachliError by error

    data class GetListsWithAccount(val accountId: String, private val error: ApiError) : ListsError, PachliError by error

    data class GetAccounts(override val listId: String, private val error: ApiError) : ListsError, HasListId, PachliError by error

    data class AddAccounts(override val listId: String, private val error: ApiError) : ListsError, HasListId, PachliError by error

    data class DeleteAccounts(override val listId: String, private val error: ApiError) : ListsError, HasListId, PachliError by error
}

interface ListsRepository {
    val lists: StateFlow<Result<Lists, ListsError.Retrieve>>

    /** Make an API call to refresh [lists] */
    fun refresh()

    /**
     * Create a new list
     *
     * @param title The new lists title
     * @param exclusive True if the list is exclusive
     * @return Details of the new list if successfuly, or an error
     */
    suspend fun createList(title: String, exclusive: Boolean, repliesPolicy: UserListRepliesPolicy): Result<MastoList, ListsError.Create>

    /**
     * Edit an existing list.
     *
     * @param listId ID of the list to edit
     * @param title New title of the list
     * @param exclusive New exclusive vale for the list
     * @return Amended list, or an error
     */
    suspend fun editList(listId: String, title: String, exclusive: Boolean, repliesPolicy: UserListRepliesPolicy): Result<MastoList, ListsError.Update>

    /**
     * Delete an existing list
     *
     * @param listId ID of the list to delete
     * @return A successful result, or an error
     */
    suspend fun deleteList(listId: String): Result<Unit, ListsError.Delete>

    /**
     * Fetch the lists with [accountId] as a member
     *
     * @param accountId ID of the account to search for
     * @result List of Mastodon lists the account is a member of, or an error
     */
    suspend fun getListsWithAccount(accountId: String): Result<List<MastoList>, ListsError.GetListsWithAccount>

    /**
     * Fetch the members of a list
     *
     * @param listId ID of the list to fetch membership for
     * @return List of [TimelineAccount] that are members of the list, or an error
     */
    suspend fun getAccountsInList(listId: String): Result<List<TimelineAccount>, ListsError.GetAccounts>

    /**
     * Add one or more accounts to a list
     *
     * @param listId ID of the list to add accounts to
     * @param accountIds IDs of the accounts to add
     * @return A successful result, or an error
     */
    suspend fun addAccountsToList(listId: String, accountIds: List<String>): Result<Unit, ListsError.AddAccounts>

    /**
     * Remove one or more accounts from a list
     *
     * @param listId ID of the list to remove accounts from
     * @param accountIds IDs of the accounts to remove
     * @return A successful result, or an error
     */
    suspend fun deleteAccountsFromList(listId: String, accountIds: List<String>): Result<Unit, ListsError.DeleteAccounts>

    companion object {
        /**
         * Locale-aware comparator for lists. Case-insenstive comparison by
         * the list's title.
         */
        val compareByListTitle: Comparator<MastoList> = compareBy(
            Collator.getInstance().apply { strength = Collator.SECONDARY },
        ) { it.title }
    }
}
