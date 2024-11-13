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
import app.pachli.core.data.model.MastodonList
import app.pachli.core.network.model.TimelineAccount
import app.pachli.core.network.model.UserListRepliesPolicy
import app.pachli.core.network.retrofit.apiresult.ApiError
import com.github.michaelbull.result.Result
import java.text.Collator
import kotlinx.coroutines.flow.Flow

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
    /** @return Known lists for [pachliAccountId]. */
    fun getLists(pachliAccountId: Long): Flow<List<MastodonList>>

    /** @return All known lists for all accounts. */
    fun getListsFlow(): Flow<List<MastodonList>>

    /**
     * Refresh lists for [pachliAccountId].
     *
     * @return Latests lists, or an error.
     */
    suspend fun refresh(pachliAccountId: Long): Result<List<MastodonList>, ListsError.Retrieve>

    /**
     * Creates a new list
     *
     * @param pachliAccountId Account that will own the new list.
     * @param title Title for the new list.
     * @param exclusive True if the new list is exclusive.
     * @return Details of the new list if successfuly, or an error.
     */
    suspend fun createList(
        pachliAccountId: Long,
        title: String,
        exclusive: Boolean,
        repliesPolicy: UserListRepliesPolicy,
    ): Result<MastodonList, ListsError.Create>

    /**
     * Updates an existing list.
     *
     * @param pachliAccountId Account that owns the list to update.
     * @param listId ID of the list to update.
     * @param title New title of the list.
     * @param exclusive New exclusive value for the list.
     * @return Amended list, or an error.
     */
    suspend fun updateList(
        pachliAccountId: Long,
        listId: String,
        title: String,
        exclusive: Boolean,
        repliesPolicy: UserListRepliesPolicy,
    ): Result<MastodonList, ListsError.Update>

    /**
     * Deletes an existing list
     *
     * @param list The list to delete
     * @return A successful result, or an error
     */
    suspend fun deleteList(list: MastodonList): Result<Unit, ListsError.Delete>

    /**
     * Fetches the lists with [accountId] as a member
     *
     * @param accountId ID of the account to search for
     * @result List of Mastodon lists the account is a member of, or an error
     */
    suspend fun getListsWithAccount(
        pachliAccountId: Long,
        accountId: String,
    ): Result<List<MastodonList>, ListsError.GetListsWithAccount>

    /**
     * Fetches the members of a list
     *
     * @param listId ID of the list to fetch membership for
     * @return List of [TimelineAccount] that are members of the list, or an error
     */
    suspend fun getAccountsInList(
        pachliAccountId: Long,
        listId: String,
    ): Result<List<TimelineAccount>, ListsError.GetAccounts>

    /**
     * Adds one or more accounts to a list
     *
     * @param listId ID of the list to add accounts to
     * @param accountIds IDs of the accounts to add
     * @return A successful result, or an error
     */
    suspend fun addAccountsToList(
        pachliAccountId: Long,
        listId: String,
        accountIds: List<String>,
    ): Result<Unit, ListsError.AddAccounts>

    /**
     * Removes one or more accounts from a list
     *
     * @param listId ID of the list to remove accounts from
     * @param accountIds IDs of the accounts to remove
     * @return A successful result, or an error
     */
    suspend fun deleteAccountsFromList(
        pachliAccountId: Long,
        listId: String,
        accountIds: List<String>,
    ): Result<Unit, ListsError.DeleteAccounts>

    companion object {
        /**
         * Locale-aware comparator for lists. Case-insenstive comparison by
         * the list's title.
         */
        val compareByListTitle: Comparator<MastodonList> = compareBy(
            Collator.getInstance().apply { strength = Collator.SECONDARY },
        ) { it.title }
    }
}
