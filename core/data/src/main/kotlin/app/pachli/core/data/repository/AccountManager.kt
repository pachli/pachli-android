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
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.R
import app.pachli.core.data.model.MastodonList
import app.pachli.core.data.model.Server
import app.pachli.core.data.repository.LogoutError.NoActiveAccount
import app.pachli.core.data.repository.ServerRepository.Error.GetNodeInfo
import app.pachli.core.data.repository.ServerRepository.Error.GetWellKnownNodeInfo
import app.pachli.core.data.repository.ServerRepository.Error.UnsupportedSchema
import app.pachli.core.data.repository.ServerRepository.Error.ValidateNodeInfo
import app.pachli.core.database.dao.AccountDao
import app.pachli.core.database.dao.AnnouncementsDao
import app.pachli.core.database.dao.InstanceDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.AnnouncementEntity
import app.pachli.core.database.model.EmojisEntity
import app.pachli.core.database.model.InstanceInfoEntity
import app.pachli.core.database.model.ServerEntity
import app.pachli.core.model.NodeInfo
import app.pachli.core.model.Timeline
import app.pachli.core.network.model.Account
import app.pachli.core.network.model.Status
import app.pachli.core.network.retrofit.InstanceSwitchAuthInterceptor
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.NodeInfoApi
import app.pachli.core.network.retrofit.apiresult.ApiError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.orElse
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

// Note to self: This is doing dual duty as a repository and as a collection of
// use cases, and should be refactored along those lines.

/** Errors that can occur when setting the active account. */
sealed interface SetActiveAccountError : PachliError {
    /**
     * Suggested account to fallback to on failure.
     *
     * The caller may want to present this as an option to the user if logging
     * in fails.
     *
     * May be null if there was no previous active account.
     */
    val fallbackAccount: AccountEntity?

    /**
     * The requested account could not be found in the local database. Should
     * never happen.
     *
     * @param fallbackAccount See [SetActiveAccountError.fallbackAccount]
     * @param accountId ID of the account that could not be found.
     */
    data class AccountDoesNotExist(
        override val fallbackAccount: AccountEntity?,
        val accountId: Long,
    ) : SetActiveAccountError {
        override val resourceId = R.string.account_manager_error_account_does_not_exist
        override val formatArgs = null
        override val cause = null
    }

    /**
     * An API error occurred while logging in.
     *
     * @param fallbackAccount See [SetActiveAccountError.fallbackAccount]
     * @param wantedAccount The account entity that could not be made active.
     */
    data class Api(
        override val fallbackAccount: AccountEntity?,
        val wantedAccount: AccountEntity,
        val apiError: ApiError,
    ) : SetActiveAccountError, PachliError by apiError

    /**
     * Catch-all for unexpected exceptions when logging in.
     *
     * @param fallbackAccount See [SetActiveAccountError.fallbackAccount]
     * @param wantedAccount The account entity that could not be made active.
     * @param throwable Throwable that caused the error
     */
    data class Unexpected(
        override val fallbackAccount: AccountEntity?,
        val wantedAccount: AccountEntity,
        val throwable: Throwable,
    ) : SetActiveAccountError {
        override val resourceId = R.string.account_manager_error_unexpected
        override val formatArgs: Array<String> = arrayOf(throwable.localizedMessage ?: "unknown")
        override val cause = null
    }
}

sealed interface RefreshAccountError : PachliError {
    @JvmInline
    value class General(private val pachliError: PachliError) : RefreshAccountError, PachliError by pachliError
}

/** Errors that can occur logging out. */
sealed interface LogoutError : PachliError {
    data object NoActiveAccount : LogoutError {
        override val resourceId = R.string.account_manager_error_no_active_account
        override val formatArgs = null
        override val cause = null
    }

    /** An API call failed during the logout process. */
    @JvmInline
    value class Api(val apiError: ApiError) : LogoutError, PachliError by apiError
}

@Singleton
class AccountManager @Inject constructor(
    private val transactionProvider: TransactionProvider,
    private val mastodonApi: MastodonApi,
    private val nodeInfoApi: NodeInfoApi,
    private val accountDao: AccountDao,
    private val instanceDao: InstanceDao,
    private val contentFiltersRepository: ContentFiltersRepository,
    private val listsRepository: ListsRepository,
    private val announcementsDao: AnnouncementsDao,
    private val instanceSwitchAuthInterceptor: InstanceSwitchAuthInterceptor,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    @Deprecated("Caller should use getPachliAccountFlow with a specific account ID")
    val activeAccountFlow: StateFlow<Loadable<AccountEntity?>> =
        accountDao.getActiveAccountFlow()
            .distinctUntilChanged()
            .map { Loadable.Loaded(it) }
            .stateIn(externalScope, SharingStarted.Eagerly, Loadable.Loading())

    /** The active account, or null if there is no active account. */
    @Deprecated("Caller should use getPachliAccountFlow with a specific account ID")
    val activeAccount: AccountEntity?
        get() {
            return when (val loadable = activeAccountFlow.value) {
                is Loadable.Loading -> null
                is Loadable.Loaded -> loadable.data
            }
        }

    /** All logged in accounts. */
    val accountsFlow = accountDao.loadAllFlow().stateIn(externalScope, SharingStarted.Eagerly, emptyList())

    val accounts: List<AccountEntity>
        get() = accountsFlow.value

    private val accountsOrderedByActiveFlow = accountDao.getAccountsOrderedByActive()
        .stateIn(externalScope, SharingStarted.Eagerly, emptyList())

    val accountsOrderedByActive: List<AccountEntity>
        get() = accountsOrderedByActiveFlow.value

    @Deprecated("Caller should use getPachliAccountFlow with a specific account ID")
    val activePachliAccountFlow = accountDao.getActivePachliAccountFlow()
        .filterNotNull()
        .map { PachliAccount.make(it) }

    init {
        // Ensure InstanceSwitchAuthInterceptor is initially set with the credentials of
        // the active account, otherwise network requests that happen after a resume
        // (if setActiveAccount is not called) have no credentials.
        externalScope.launch {
            accountDao.loadAll().firstOrNull { it.isActive }?.let {
                instanceSwitchAuthInterceptor.credentials =
                    InstanceSwitchAuthInterceptor.Credentials(
                        accessToken = it.accessToken,
                        domain = it.domain,
                    )
            }
        }

        externalScope.launch {
            listsRepository.getListsFlow().collect { lists ->
                val listsById = lists.groupBy { it.accountId }
                listsById.forEach { (pachliAccountId, group) ->
                    newTabPreferences(pachliAccountId, group)?.let {
                        setTabPreferences(pachliAccountId, it)
                    }
                }
            }
        }
    }

    fun getPachliAccountFlow(pachliAccountId: Long): Flow<PachliAccount?> {
        val accountFlow = if (pachliAccountId == -1L) {
            accountDao.getActiveAccountId().flatMapLatest {
                accountDao.getPachliAccountFlow(it)
            }
        } else {
            accountDao.getPachliAccountFlow(pachliAccountId)
        }

        return accountFlow.map { it?.let { PachliAccount.make(it) } }
    }

    /**
     * Finds an account by its database id
     * @param accountId the id of the account
     * @return the requested account or null if it was not found
     */
    // TODO: Should be `suspend`, accessed through a ViewModel, but not all the
    // calling code has been converted yet.
    fun getAccountById(accountId: Long): AccountEntity? {
        return accounts.find { (id) ->
            id == accountId
        }
    }

    /**
     * Verifies the account has valid credentials according to the remote server
     * and adds it to the local database if it does.
     *
     * Does not make it the active account.
     *
     * @param accessToken the access token for the new account
     * @param domain the domain of the account's Mastodon instance
     * @param clientId the oauth client id used to sign in the account
     * @param clientSecret the oauth client secret used to sign in the account
     * @param oauthScopes the oauth scopes granted to the account
     */
    suspend fun verifyAndAddAccount(
        accessToken: String,
        domain: String,
        clientId: String,
        clientSecret: String,
        oauthScopes: String,
    ): Result<Long, ApiError> {
        val networkAccount = mastodonApi.accountVerifyCredentials(
            domain = domain,
            auth = "Bearer $accessToken",
        ).getOrElse { return Err(it) }.body

        return transactionProvider {
            val existingAccount = accountDao.getAccountByIdAndDomain(
                networkAccount.id,
                domain,
            )

            val newAccount = existingAccount?.copy(
                accessToken = accessToken,
                clientId = clientId,
                clientSecret = clientSecret,
                oauthScopes = oauthScopes,
            ) ?: AccountEntity(
                id = 0L,
                domain = domain.lowercase(Locale.ROOT),
                accessToken = accessToken,
                clientId = clientId,
                clientSecret = clientSecret,
                oauthScopes = oauthScopes,
                isActive = true,
                accountId = networkAccount.id,
            )

            Timber.d("addAccount: upsert account id: %d, isActive: %s", newAccount.id, newAccount.isActive)
            val newId = accountDao.upsert(newAccount)
            return@transactionProvider Ok(newId)
        }
    }

    suspend fun clearPushNotificationData(accountId: Long) {
        setPushNotificationData(accountId, "", "", "", "", "")
    }

    /**
     * Logs out the active account by deleting all data of the account.
     *
     * @return The next account to make active, or null if there are no more accounts.
     */
    suspend fun logActiveAccountOut(): Result<AccountEntity?, LogoutError> {
        return transactionProvider {
            val activeAccount = accountDao.getActiveAccount() ?: return@transactionProvider Err(NoActiveAccount)
            Timber.d("logout: Logging out %d", activeAccount.id)

            // Deleting credentials so they cannot be used again
            accountDao.clearLoginCredentials(activeAccount.id)

            accountDao.delete(activeAccount)

            val accounts = accountDao.loadAll()
            val newActiveAccount = accounts.firstOrNull()?.copy(isActive = true) ?: return@transactionProvider Ok(null)
            return@transactionProvider Ok(newActiveAccount)
        }
    }

    /**
     * Changes the active account.
     *
     * Does not refresh the account, call [refresh] for that.
     *
     * @param accountId the database id of the new active account
     * @return The account entity for the new active account, or an error.
     */
    suspend fun setActiveAccount(accountId: Long): Result<AccountEntity, SetActiveAccountError> {
        /** Wrapper to pass an API error out of the transaction. */
        data class ApiErrorException(val apiError: ApiError) : Exception()

        /** Account to fallback to if switching fails. */
        var fallbackAccount: AccountEntity? = null

        /** Account we're trying to switch to. */
        var accountEntity: AccountEntity? = null

        return try {
            transactionProvider {
                Timber.d("setActiveAcccount(%d)", accountId)

                // Handle "-1" as the accountId.
                val previousActiveAccount = accountDao.getActiveAccount()
                val newActiveAccount = if (accountId == -1L) {
                    previousActiveAccount
                } else {
                    accountDao.getAccountById(accountId)
                }

                // Fall back to either the previous account (if known), or the first account
                // that isn't this one.
                fallbackAccount = previousActiveAccount
                    ?: accountDao.loadAll().firstOrNull { it.id != accountId }

                if (newActiveAccount == null) {
                    Timber.d("Account %d not in database", accountId)
                    return@transactionProvider Err(
                        SetActiveAccountError.AccountDoesNotExist(
                            fallbackAccount,
                            accountId,
                        ),
                    )
                }

                accountEntity = newActiveAccount

                // Fetch data from the API, updating the account as necessary.
                // If this fails an exception is thrown to cancel the transaction.
                //
                // Note: Can't adjust InstanceSwitchAuthInterceptor before this,
                // because if this call fails the change would need to be undone as
                // part of cancelling the transaction. That's why it's modified at the
                // very end of this block.
                val account = mastodonApi.accountVerifyCredentials(
                    domain = newActiveAccount.domain,
                    auth = newActiveAccount.authHeader,
                ).getOrElse { throw ApiErrorException(it) }.body

                accountDao.clearActiveAccount()

                val finalAccount = newActiveAccount.copy(
                    isActive = true,
                    accountId = account.id,
                    username = account.username,
                    displayName = account.name,
                    profilePictureUrl = account.avatar,
                    profileHeaderPictureUrl = account.header,
                    defaultPostPrivacy = account.source?.privacy ?: Status.Visibility.PUBLIC,
                    defaultPostLanguage = account.source?.language.orEmpty(),
                    defaultMediaSensitivity = account.source?.sensitive ?: false,
                    emojis = account.emojis.orEmpty(),
                    locked = account.locked,
                )

                Timber.d("setActiveAccount: saving id: %d, isActive: %s", finalAccount.id, finalAccount.isActive)
                accountDao.update(finalAccount)

                // Now safe to update InstanceSwitchAuthInterceptor.
                Timber.d("Updating instanceSwitchAuthInterceptor with credentials for %s", newActiveAccount.fullName)
                instanceSwitchAuthInterceptor.credentials =
                    InstanceSwitchAuthInterceptor.Credentials(
                        accessToken = newActiveAccount.accessToken,
                        domain = newActiveAccount.domain,
                    )

                return@transactionProvider Ok(finalAccount)
            }
        } catch (e: ApiErrorException) {
            Err(SetActiveAccountError.Api(fallbackAccount, accountEntity!!, e.apiError))
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            Err(SetActiveAccountError.Unexpected(fallbackAccount, accountEntity!!, e))
        }
    }

    /**
     * Refreshes the local data for [account] from remote sources.
     *
     * @return Unit if the refresh completed successfully, or the error.
     */
    // TODO: Protect this with a mutex?
    suspend fun refresh(account: AccountEntity): Result<Unit, RefreshAccountError> = binding {
        // Kick off network fetches that can happen in parallel because they do not
        // depend on one another.
        val deferNodeInfo = externalScope.async {
            fetchNodeInfo().mapError { RefreshAccountError.General(it) }
        }

        val deferInstanceInfo = externalScope.async {
            fetchInstanceInfo(account.domain)
                .mapError { RefreshAccountError.General(it) }
                .onSuccess { instanceDao.upsert(it) }
        }

        val deferEmojis = externalScope.async {
            mastodonApi.getCustomEmojis()
                .mapError { RefreshAccountError.General(it) }
                .onSuccess { instanceDao.upsert(EmojisEntity(accountId = account.id, emojiList = it.body)) }
        }

        val deferAnnouncements = externalScope.async {
            mastodonApi.listAnnouncements(false)
                .mapError { RefreshAccountError.General(it) }
                .map {
                    it.body.map {
                        AnnouncementEntity(
                            accountId = account.id,
                            announcementId = it.id,
                            announcement = it,
                        )
                    }
                }
                .onSuccess {
                    transactionProvider {
                        announcementsDao.deleteAllForAccount(account.id)
                        announcementsDao.upsert(it)
                    }
                }
        }

        val nodeInfo = deferNodeInfo.await().bind()
        val instanceInfo = deferInstanceInfo.await().bind()

        // Create the server info so it can used for both server capabilities and filters.
        //
        // Can't use ServerRespository here because it depends on AccountManager.
        // TODO: Break that dependency, re-write ServerRepository to be offline-first.
        Server.from(nodeInfo.software, instanceInfo)
            .mapError { RefreshAccountError.General(it) }
            .onSuccess {
                instanceDao.upsert(
                    ServerEntity(
                        accountId = account.id,
                        serverKind = it.kind,
                        version = it.version,
                        capabilities = it.capabilities,
                    ),
                )
            }
            .bind()

        externalScope.async { contentFiltersRepository.refresh(account.id) }.await()
            .mapError { RefreshAccountError.General(it) }.bind()

        deferEmojis.await().bind()

        externalScope.async { listsRepository.refresh(account.id) }.await()
            .mapError { RefreshAccountError.General(it) }.bind()

        // Ignore errors when fetching announcements, they're non-fatal.
        // TODO: Add a capability for announcements.
        deferAnnouncements.await().orElse { Ok(emptyList()) }.bind()
    }

    /**
     * Updates [pachliAccountId] with data from [newAccount].
     *
     * Updates the values:
     *
     * - [displayName][AccountEntity.displayName]
     * - [profilePictureUrl][AccountEntity.profilePictureUrl]
     * - [profileHeaderPictureUrl][AccountEntity.profileHeaderPictureUrl]
     * - [locked][AccountEntity.locked]
     */
    suspend fun updateAccount(pachliAccountId: Long, newAccount: Account) {
        transactionProvider {
            val existingAccount = accountDao.getAccountById(pachliAccountId) ?: return@transactionProvider
            val updatedAccount = existingAccount.copy(
                displayName = newAccount.displayName ?: existingAccount.displayName,
                profilePictureUrl = newAccount.avatar,
                profileHeaderPictureUrl = newAccount.header,
                locked = newAccount.locked,
            )
            accountDao.upsert(updatedAccount)
        }
    }

    /**
     * Determines the user's tab preferences when lists are loaded.
     *
     * The user may have added one or more lists to tabs. If they have then:
     *
     * - A list-in-a-tab might have been deleted
     * - A list-in-a-tab might have been renamed
     *
     * Handle both of those scenarios.
     *
     * @param pachliAccountId The account to check
     * @param lists The account's latest lists
     * @return A list of new tab preferences for [pachliAccountId], or null if there are no changes.
     */
    private suspend fun newTabPreferences(pachliAccountId: Long, lists: List<MastodonList>): List<Timeline>? {
        val map = lists.associateBy { it.listId }
        val account = accountDao.getAccountById(pachliAccountId) ?: return null
        val oldTabPreferences = account.tabPreferences
        var changed = false
        val newTabPreferences = buildList {
            for (oldPref in oldTabPreferences) {
                if (oldPref !is Timeline.UserList) {
                    add(oldPref)
                    continue
                }

                // List has been deleted? Don't add this pref,
                // record there's been a change, and move on to the
                // next one.
                if (oldPref.listId !in map) {
                    changed = true
                    continue
                }

                // Title changed? Update the title in the pref and
                // add it.
                if (oldPref.title != map[oldPref.listId]?.title) {
                    changed = true
                    add(oldPref.copy(title = map[oldPref.listId]?.title!!))
                    continue
                }

                add(oldPref)
            }
        }
        return if (changed) newTabPreferences else null
    }

    // Based on ServerRepository.getServer(). This can be removed when AccountManager
    // can use ServerRepository directly.
    private suspend fun fetchNodeInfo(): Result<NodeInfo, ServerRepository.Error> = binding {
        // Fetch the /.well-known/nodeinfo document
        val nodeInfoJrd = nodeInfoApi.nodeInfoJrd()
            .mapError { GetWellKnownNodeInfo(it) }.bind().body

        // Find a link to a schema we can parse, prefering newer schema versions
        var nodeInfoUrlResult: Result<String, ServerRepository.Error> = Err(UnsupportedSchema)
        for (link in nodeInfoJrd.links.sortedByDescending { it.rel }) {
            if (SCHEMAS.contains(link.rel)) {
                nodeInfoUrlResult = Ok(link.href)
                break
            }
        }

        val nodeInfoUrl = nodeInfoUrlResult.bind()

        Timber.d("Loading node info from %s", nodeInfoUrl)
        val nodeInfo = nodeInfoApi.nodeInfo(nodeInfoUrl).mapBoth(
            { it.body.validate().mapError { ValidateNodeInfo(nodeInfoUrl, it) } },
            { Err(GetNodeInfo(nodeInfoUrl, it)) },
        ).bind()

        return@binding nodeInfo
    }

    // TODO: Maybe rename InstanceInfoEntity to ServerLimits or something like that, since that's
    // what it records.
    private suspend fun fetchInstanceInfo(domain: String): Result<InstanceInfoEntity, ApiError> {
        // TODO: InstanceInfoEntity needs to gain support for recording translation
        return mastodonApi.getInstanceV2()
            .map { InstanceInfoEntity.make(domain, it.body) }
            .orElse {
                mastodonApi.getInstanceV1().map { InstanceInfoEntity.make(domain, it.body) }
            }
    }

    /**
     * @return True if at least one account has Android notifications enabled
     */
    // TODO: Should be `suspend`, accessed through a ViewModel, but not all the
    // calling code has been converted yet.
    fun areAndroidNotificationsEnabled(): Boolean {
        return accounts.any { it.notificationsEnabled }
    }

    suspend fun setAlwaysShowSensitiveMedia(accountId: Long, value: Boolean) {
        accountDao.setAlwaysShowSensitiveMedia(accountId, value)
    }

    suspend fun setAlwaysOpenSpoiler(accountId: Long, value: Boolean) {
        accountDao.setAlwaysOpenSpoiler(accountId, value)
    }

    suspend fun setMediaPreviewEnabled(accountId: Long, value: Boolean) {
        accountDao.setMediaPreviewEnabled(accountId, value)
    }

    suspend fun setTabPreferences(accountId: Long, value: List<Timeline>) {
        Timber.d("setTabPreferences: %d, %s", accountId, value)
        accountDao.setTabPreferences(accountId, value)
    }

    suspend fun setNotificationMarkerId(accountId: Long, value: String) {
        accountDao.setNotificationMarkerId(accountId, value)
    }

    suspend fun setNotificationsFilter(accountId: Long, value: String) {
        accountDao.setNotificationsFilter(accountId, value)
    }

    suspend fun setLastNotificationId(accountId: Long, value: String) {
        accountDao.setLastNotificationId(accountId, value)
    }

    suspend fun setPushNotificationData(
        accountId: Long,
        unifiedPushUrl: String,
        pushServerKey: String,
        pushAuth: String,
        pushPrivKey: String,
        pushPubKey: String,
    ) {
        accountDao.setPushNotificationData(
            accountId,
            unifiedPushUrl,
            pushServerKey,
            pushAuth,
            pushPrivKey,
            pushPubKey,
        )
    }

    fun setDefaultPostPrivacy(accountId: Long, value: Status.Visibility) {
        accountDao.setDefaultPostPrivacy(accountId, value)
    }

    fun setDefaultMediaSensitivity(accountId: Long, value: Boolean) {
        accountDao.setDefaultMediaSensitivity(accountId, value)
    }

    fun setDefaultPostLanguage(accountId: Long, value: String) {
        accountDao.setDefaultPostLanguage(accountId, value)
    }

    fun setNotificationsEnabled(accountId: Long, value: Boolean) {
        accountDao.setNotificationsEnabled(accountId, value)
    }

    fun setNotificationsFollowed(accountId: Long, value: Boolean) {
        accountDao.setNotificationsFollowed(accountId, value)
    }

    fun setNotificationsFollowRequested(accountId: Long, value: Boolean) {
        accountDao.setNotificationsFollowRequested(accountId, value)
    }

    fun setNotificationsReblogged(accountId: Long, value: Boolean) {
        accountDao.setNotificationsReblogged(accountId, value)
    }

    fun setNotificationsFavorited(accountId: Long, value: Boolean) {
        accountDao.setNotificationsFavorited(accountId, value)
    }

    fun setNotificationsPolls(accountId: Long, value: Boolean) {
        accountDao.setNotificationsPolls(accountId, value)
    }

    fun setNotificationsSubscriptions(accountId: Long, value: Boolean) {
        accountDao.setNotificationsSubscriptions(accountId, value)
    }

    fun setNotificationsSignUps(accountId: Long, value: Boolean) {
        accountDao.setNotificationsSignUps(accountId, value)
    }

    fun setNotificationsUpdates(accountId: Long, value: Boolean) {
        accountDao.setNotificationsUpdates(accountId, value)
    }

    fun setNotificationsReports(accountId: Long, value: Boolean) {
        accountDao.setNotificationsReports(accountId, value)
    }

    fun setNotificationSound(accountId: Long, value: Boolean) {
        accountDao.setNotificationSound(accountId, value)
    }

    fun setNotificationVibration(accountId: Long, value: Boolean) {
        accountDao.setNotificationVibration(accountId, value)
    }

    fun setNotificationLight(accountId: Long, value: Boolean) {
        accountDao.setNotificationLight(accountId, value)
    }

    suspend fun setLastVisibleHomeTimelineStatusId(accountId: Long, value: String?) {
        Timber.d("setLastVisibleHomeTimelineStatusId: %d, %s", accountId, value)
        accountDao.setLastVisibleHomeTimelineStatusId(accountId, value)
    }

    // -- Announcements
    suspend fun deleteAnnouncement(accountId: Long, announcementId: String) {
        announcementsDao.deleteForAccount(accountId, announcementId)
    }
}
