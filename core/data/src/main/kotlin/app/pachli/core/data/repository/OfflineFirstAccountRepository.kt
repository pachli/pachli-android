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

import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.repository.AccountRepository.AccountError
import app.pachli.core.data.repository.AccountRepository.AccountError.GetAccountError
import app.pachli.core.data.repository.AccountRepository.AccountError.GetAccountsError
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.model.asEntity
import app.pachli.core.database.model.asModel
import app.pachli.core.model.Account
import app.pachli.core.model.TimelineAccount
import app.pachli.core.network.model.asModel
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.onSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/**
 * Repository for managing data about Mastodon accounts (not the user's account,
 * accounts that can appear in statuses, notifications, etc).
 *
 * Data is cached locally, and fetched as necessary.
 */
@Singleton
class OfflineFirstAccountRepository @Inject internal constructor(
    private val localDataSource: AccountLocalDataSource,
    private val remoteDataSource: AccountRemoteDataSource,
) : AccountRepository {
    /**
     * Returns [Account] with [accountId] from the local cache. If the account
     * is not in the local cache then makes a network request for the account
     * (which may fail), and saves the account before returning.
     *
     * @param pachliAccountId
     * @param accountId ID of the account to fetch.
     * @return The account, or [AccountError.GetAccountError].
     */
    override suspend fun getAccount(pachliAccountId: Long, accountId: String): Result<Account, GetAccountError> {
        val account = localDataSource.getAccount(pachliAccountId, accountId)
        if (account != null) return Ok(account)

        return remoteDataSource.getAccount(pachliAccountId, accountId)
            .onSuccess { localDataSource.saveAccount(pachliAccountId, it) }
    }

    /**
     * Returns [Account]s from [accountIds] from the local cache. If any are
     * missing, makes a network request for the missing accounts (which may fail),
     * and saves the accounts before returning.
     *
     * @param pachliAccountId
     * @param accountIds
     * @return The accounts, or the error if a network request had to be made.
     */
    // TODO: Maybe the resturn should be a List<Result<Account, Error>>, to report
    // the errors on a per-account basis?
    override suspend fun getAccounts(pachliAccountId: Long, accountIds: Collection<String>): Result<List<Account>, GetAccountsError> {
        val accounts = localDataSource.getAccounts(pachliAccountId, accountIds)

        val gotIds = accounts.map { it.serverId }
        val missingIds = accountIds.filter { !gotIds.contains(it) }

        if (missingIds.isEmpty()) return Ok(accounts)

        return remoteDataSource.getAccounts(pachliAccountId, missingIds)
            .onSuccess { localDataSource.saveAccounts(pachliAccountId, it) }
            .map { it + accounts }
    }

    /**
     * Saves [accounts] to the local cache.
     */
    override suspend fun saveAccounts(pachliAccountId: Long, accounts: Collection<Account>) {
        localDataSource.saveAccounts(pachliAccountId, accounts)
    }

    /**
     * Saves [timelineAccount] to the local cache.
     */
    override suspend fun saveTimelineAccount(pachliAccountId: Long, timelineAccount: TimelineAccount) {
        localDataSource.saveTimelineAccount(pachliAccountId, timelineAccount)
    }
}

@Singleton
internal class AccountLocalDataSource @Inject constructor(
    private val timelineDao: TimelineDao,
) {
    suspend fun getAccount(pachliAccountId: Long, accountId: String) = timelineDao.getAccount(pachliAccountId, accountId)?.asModel()

    suspend fun saveAccount(pachliAccountId: Long, account: Account) = timelineDao.upsertAccounts(listOf(account.asEntity(pachliAccountId)))

    suspend fun saveAccounts(pachliAccountId: Long, accounts: Collection<Account>) = timelineDao.upsertAccounts(accounts.asEntity(pachliAccountId))

    suspend fun saveTimelineAccount(pachliAccountId: Long, timelineAccount: TimelineAccount) = timelineDao.upsertTimelineAccounts(listOf(timelineAccount).asEntity(pachliAccountId))

    suspend fun getAccounts(pachliAccountId: Long, accountIds: Collection<String>) = timelineDao.getAccounts(pachliAccountId, accountIds).asModel()
}

@Singleton
internal class AccountRemoteDataSource @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val mastodonApi: MastodonApi,
) {
    suspend fun getAccount(pachliAccountId: Long, accountId: String): Result<Account, GetAccountError> = binding {
        mastodonApi.account(accountId)
            .mapEither(
                { it.body.asModel() },
                { GetAccountError(it) },
            )
            .bind()
    }

    suspend fun getAccounts(pachliAccountId: Long, accountIds: Collection<String>) = binding {
        mastodonApi.accounts(accountIds)
            .mapEither(
                { it.body.asModel() },
                { GetAccountsError(it) },
            )
            .bind()
    }
}
