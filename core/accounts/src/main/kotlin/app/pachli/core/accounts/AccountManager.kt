/*
 * Copyright 2018 Conny Duck
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

package app.pachli.core.accounts

import android.content.Context
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.database.dao.AccountDao
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.network.model.Account
import app.pachli.core.network.model.Status
import app.pachli.core.network.retrofit.InstanceSwitchAuthInterceptor
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@Singleton
class AccountManager @Inject constructor(
    private val accountDao: AccountDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
    private val instanceSwitchAuthInterceptor: InstanceSwitchAuthInterceptor,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val _activeAccountFlow = MutableStateFlow<AccountEntity?>(null)
    val activeAccountFlow = _activeAccountFlow.asStateFlow()

    @Volatile
    var activeAccount: AccountEntity? = null
        private set(value) {
            field = value
            instanceSwitchAuthInterceptor.credentials = value?.let {
                InstanceSwitchAuthInterceptor.Credentials(
                    accessToken = it.accessToken,
                    domain = it.domain,
                )
            }
            externalScope.launch { _activeAccountFlow.emit(value) }
        }

    var accounts: MutableList<AccountEntity> = mutableListOf()
        private set

    init {
        accounts = accountDao.loadAll().toMutableList()

        activeAccount = accounts.find { acc -> acc.isActive }
            ?: accounts.firstOrNull()?.also { acc -> acc.isActive = true }
    }

    /**
     * Adds a new account and makes it the active account.
     * @param accessToken the access token for the new account
     * @param domain the domain of the account's Mastodon instance
     * @param clientId the oauth client id used to sign in the account
     * @param clientSecret the oauth client secret used to sign in the account
     * @param oauthScopes the oauth scopes granted to the account
     * @param newAccount the [Account] as returned by the Mastodon Api
     */
    fun addAccount(
        accessToken: String,
        domain: String,
        clientId: String,
        clientSecret: String,
        oauthScopes: String,
        newAccount: Account,
    ) {
        activeAccount?.let {
            it.isActive = false
            Timber.d("addAccount: saving account with id %d", it.id)

            accountDao.insertOrReplace(it)
        }
        // check if this is a relogin with an existing account, if yes update it, otherwise create a new one
        val existingAccountIndex = accounts.indexOfFirst { account ->
            domain == account.domain && newAccount.id == account.accountId
        }
        val newAccountEntity = if (existingAccountIndex != -1) {
            accounts[existingAccountIndex].copy(
                accessToken = accessToken,
                clientId = clientId,
                clientSecret = clientSecret,
                oauthScopes = oauthScopes,
                isActive = true,
            ).also { accounts[existingAccountIndex] = it }
        } else {
            val maxAccountId = accounts.maxByOrNull { it.id }?.id ?: 0
            val newAccountId = maxAccountId + 1
            AccountEntity(
                id = newAccountId,
                domain = domain.lowercase(Locale.ROOT),
                accessToken = accessToken,
                clientId = clientId,
                clientSecret = clientSecret,
                oauthScopes = oauthScopes,
                isActive = true,
                accountId = newAccount.id,
            ).also { accounts.add(it) }
        }

        activeAccount = newAccountEntity
        updateActiveAccount(newAccount)
    }

    /**
     * Saves an already known account to the database.
     * New accounts must be created with [addAccount]
     * @param account the account to save
     */
    fun saveAccount(account: AccountEntity) {
        if (account.id != 0L) {
            Timber.d("saveAccount: saving account with id %d", account.id)
            accountDao.insertOrReplace(account)
        }
    }

    /**
     * Logs the current account out by deleting all data of the account.
     * @return the new active account, or null if no other account was found
     */
    fun logActiveAccountOut(): AccountEntity? {
        return activeAccount?.let { account ->

            account.logout()

            accounts.remove(account)
            accountDao.delete(account)
            remoteKeyDao.delete(account.id)

            if (accounts.size > 0) {
                accounts[0].isActive = true
                activeAccount = accounts[0]
                Timber.d("logActiveAccountOut: saving account with id %d", accounts[0].id)
                accountDao.insertOrReplace(accounts[0])
            } else {
                activeAccount = null
            }
            activeAccount
        }
    }

    /**
     * updates the current account with new information from the mastodon api
     * and saves it in the database
     * @param account the [Account] object returned from the api
     */
    fun updateActiveAccount(account: Account) {
        activeAccount?.let {
            it.accountId = account.id
            it.username = account.username
            it.displayName = account.name
            it.profilePictureUrl = account.avatar
            it.defaultPostPrivacy = account.source?.privacy ?: Status.Visibility.PUBLIC
            it.defaultPostLanguage = account.source?.language.orEmpty()
            it.defaultMediaSensitivity = account.source?.sensitive ?: false
            it.emojis = account.emojis.orEmpty()
            it.locked = account.locked

            Timber.d("updateActiveAccount: saving account with id %d", it.id)
            accountDao.insertOrReplace(it)
        }
    }

    /**
     * changes the active account
     * @param accountId the database id of the new active account
     */
    fun setActiveAccount(accountId: Long) {
        val newActiveAccount = accounts.find { (id) ->
            id == accountId
        } ?: return // invalid accountId passed, do nothing

        activeAccount?.let {
            Timber.d("setActiveAccount: saving account with id %d", it.id)
            it.isActive = false
            saveAccount(it)
        }

        activeAccount = newActiveAccount

        activeAccount?.let {
            it.isActive = true
            accountDao.insertOrReplace(it)
        }
    }

    /**
     * @return an immutable list of all accounts in the database with the active account first
     */
    fun getAllAccountsOrderedByActive(): List<AccountEntity> {
        val accountsCopy = accounts.toMutableList()
        accountsCopy.sortWith { l, r ->
            when {
                l.isActive && !r.isActive -> -1
                r.isActive && !l.isActive -> 1
                else -> 0
            }
        }

        return accountsCopy
    }

    /**
     * @return true if at least one account has notifications enabled
     */
    fun areNotificationsEnabled(): Boolean {
        return accounts.any { it.notificationsEnabled }
    }

    /**
     * Finds an account by its database id
     * @param accountId the id of the account
     * @return the requested account or null if it was not found
     */
    fun getAccountById(accountId: Long): AccountEntity? {
        return accounts.find { (id) ->
            id == accountId
        }
    }

    /**
     * Finds an account by its string identifier
     * @param identifier the string identifier of the account
     * @return the requested account or null if it was not found
     */
    fun getAccountByIdentifier(identifier: String): AccountEntity? {
        return accounts.find {
            identifier == it.identifier
        }
    }

    /**
     * @return true if the name of the currently-selected account should be displayed in UIs
     */
    fun shouldDisplaySelfUsername(context: Context): Boolean {
        val showUsernamePreference = sharedPreferencesRepository.getString(PrefKeys.SHOW_SELF_USERNAME, "disambiguate")
        if (showUsernamePreference == "always") {
            return true
        }
        if (showUsernamePreference == "never") {
            return false
        }

        return accounts.size > 1 // "disambiguate"
    }
}
