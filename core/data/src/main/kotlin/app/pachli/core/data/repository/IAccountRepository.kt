/*
 * Copyright (c) 2026 Pachli Association
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
import app.pachli.core.data.repository.IAccountRepository.AccountError.GetAccountError
import app.pachli.core.data.repository.IAccountRepository.AccountError.GetAccountsError
import app.pachli.core.model.Account
import app.pachli.core.model.TimelineAccount
import app.pachli.core.network.retrofit.apiresult.ApiError
import com.github.michaelbull.result.Result

interface IAccountRepository {
    sealed interface AccountError : PachliError {
        @JvmInline
        value class GetAccountError(private val error: ApiError) :
            AccountError, PachliError by error

        @JvmInline
        value class GetAccountsError(private val error: ApiError) :
            AccountError, PachliError by error
    }

    /**
     * Get the data for [accountId].
     *
     * @param pachliAccountId
     * @param accountId Server ID of the account to fetch.
     */
    suspend fun getAccount(pachliAccountId: Long, accountId: String): Result<Account, GetAccountError>

    /**
     * Get the data for multiple [accountIds].
     *
     * May not return data for all the accounts, if some of them are missing.
     *
     * @param pachliAccountId
     * @param accountIds Collection of server IDs of accounts to fetch
     */
    suspend fun getAccounts(pachliAccountId: Long, accountIds: Collection<String>): Result<List<Account>, GetAccountsError>

    suspend fun saveAccounts(pachliAccountId: Long, accounts: Collection<Account>)

    suspend fun saveTimelineAccount(pachliAccountId: Long, timelineAccount: TimelineAccount)
}
