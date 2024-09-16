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

import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.common.extensions.MiB
import app.pachli.core.database.dao.AccountDao
import app.pachli.core.database.dao.InstanceDao
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.InstanceInfoEntity
import app.pachli.core.database.model.MastodonListEntity
import app.pachli.core.model.Timeline
import app.pachli.core.network.model.Status
import app.pachli.core.network.model.UserListRepliesPolicy
import app.pachli.core.network.retrofit.InstanceSwitchAuthInterceptor
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.apiresult.ApiError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.mapBoth
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

sealed interface Loadable<T> {
    class Loading<T>() : Loadable<T>
    data class Loaded<T>(val data: T) : Loadable<T>
}

data class PachliAccount(
    val id: Long,
//    val instanceInfo: InstanceInfo,
    val lists: List<MastodonList>,
) {
    companion object {
        fun make(
            account: app.pachli.core.database.model.PachliAccount,
        ): PachliAccount {
            return PachliAccount(
                id = account.account.id,
                lists = account.lists.map { MastodonList.from(it) },
            )
        }
    }
}

data class MastodonList(
    val id: String,
    val title: String,
    val repliesPolicy: UserListRepliesPolicy,
    val exclusive: Boolean,
) {
    companion object {
        fun from(entity: MastodonListEntity) = MastodonList(
            id = entity.listId,
            title = entity.title,
            repliesPolicy = entity.repliesPolicy,
            exclusive = entity.exclusive,
        )
    }
}

@Singleton
class AccountManager @Inject constructor(
    private val transactionProvider: TransactionProvider,
    private val mastodonApi: MastodonApi,
    private val accountDao: AccountDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val instanceDao: InstanceDao,
    private val instanceSwitchAuthInterceptor: InstanceSwitchAuthInterceptor,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    val activeAccountFlow: StateFlow<Loadable<AccountEntity?>> =
        accountDao.getActiveAccountFlow()
            .distinctUntilChanged()
            .onEach { Timber.d("activeAccountFlow update: id: %d, isActive: %s", it?.id, it?.isActive) }
            .map { Loadable.Loaded(it) }
            .stateIn(externalScope, SharingStarted.Eagerly, Loadable.Loading())
    val activeAccount: AccountEntity?
        get() {
            Timber.d("get() activeAccount: %s", activeAccountFlow.value)
            return when (val loadable = activeAccountFlow.value) {
                is Loadable.Loading -> null
                is Loadable.Loaded -> loadable.data
            }
        }
    val accountsFlow = accountDao.loadAllFlow()
        .distinctUntilChanged()
        .onEach {
            Timber.d("accountsFlow update:")
            it.forEach { Timber.d("  id: %d, isActive: %s", it.id, it.isActive) }
        }
        .stateIn(externalScope, SharingStarted.Eagerly, emptyList())
    val accounts: List<AccountEntity>
        get() = accountsFlow.value
    val accountsOrderedByActiveFlow = accountDao.getAccountsOrderedByActive()
        .stateIn(externalScope, SharingStarted.Eagerly, emptyList())
    val accountsOrderedByActive: List<AccountEntity>
        get() = accountsOrderedByActiveFlow.value

    suspend fun getPachliAccount(accountId: Long): PachliAccount? {
        return accountDao.getPachliAccount(accountId)?.let {
            PachliAccount.make(it)
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
        // TODO: Check the account doesn't already exist

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
     * Sets the next account as active.
     *
     * @return The new active account, or null if there are no more accounts.
     */
    suspend fun logActiveAccountOut(): AccountEntity? {
        return transactionProvider {
            val activeAccount = accountDao.getActiveAccount() ?: return@transactionProvider null
            Timber.d("logout: Logging out %d", activeAccount.id)

            // Deleting credentials so they cannot be used again
            accountDao.clearLoginCredentials(activeAccount.id)

            accountDao.delete(activeAccount)
            remoteKeyDao.delete(activeAccount.id)

            val accounts = accountDao.loadAll()
            val newActiveAccount = accounts.firstOrNull()?.copy(isActive = true) ?: return@transactionProvider null
            setActiveAccount(newActiveAccount.id)
            return@transactionProvider newActiveAccount
        }
    }

    /**
     * Changes the active account.
     *
     * @param accountId the database id of the new active account
     */
    suspend fun setActiveAccount(accountId: Long): Result<Unit, ApiError> {
        data class ApiErrorException(val apiError: ApiError) : Exception()

        try {
            transactionProvider {
                Timber.d("setActiveAcccount(%d)", accountId)
                val newActiveAccount = if (accountId == -1L) {
                    accountDao.getActiveAccount()
                } else {
                    accountDao.getAccountById(accountId)
                }
                if (newActiveAccount == null) {
                    Timber.d("Account %d not in database", accountId)
                    return@transactionProvider
                }

                accountDao.clearActiveAccount()

                // Fetch data from the API, updating the account as necessary.
                // If this fails an exception is thrown to cancel the transaction.
                //
                // Note: Can't adjust InstanceSwitchAuthInterceptor before this,
                // because if this call fails the change would need to be undone as
                // part of cancelling the transaction. That's why it's modified at the
                // very end of this block.
                val account = mastodonApi.accountVerifyCredentials(
                    domain = newActiveAccount.domain,
                    auth = "Bearer ${newActiveAccount.accessToken}",
                ).getOrElse { throw ApiErrorException(it) }.body

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

                // To load:
                // - instance info
                // - server info
                // - filters
                // - lists
                //
                // InstanceInfoEntity will need to be a merger of whatever you get from
                // instancev1 and instance2.
                val instanceInfo = fetchInstanceInfo(finalAccount.domain)
                instanceDao.upsert(instanceInfo)

                val lists = mastodonApi.getLists().get()?.body.orEmpty().map {
                    MastodonListEntity(
                        finalAccount.id,
                        it.id,
                        it.title,
                        it.repliesPolicy,
                        it.exclusive ?: false,
                    )
                }
                accountDao.deleteMastodonListsForAccount(finalAccount.id)
                lists.forEach { accountDao.upsertMastodonList(it) }
            }

            return Ok(Unit)
        } catch (e: ApiErrorException) {
            return Err(e.apiError)
        }
    }

    private suspend fun fetchInstanceInfo(domain: String): InstanceInfoEntity {
        return mastodonApi.getInstanceV1().mapBoth(
            { result ->
                val instance = result.body
                InstanceInfoEntity(
                    instance = domain,
                    maximumTootCharacters = instance.configuration.statuses.maxCharacters ?: instance.maxTootChars ?: DEFAULT_CHARACTER_LIMIT,
                    maxPollOptions = instance.configuration.polls.maxOptions,
                    maxPollOptionLength = instance.configuration.polls.maxCharactersPerOption,
                    minPollDuration = instance.configuration.polls.minExpiration,
                    maxPollDuration = instance.configuration.polls.maxExpiration,
                    charactersReservedPerUrl = instance.configuration.statuses.charactersReservedPerUrl,
                    version = instance.version,
                    videoSizeLimit = instance.configuration.mediaAttachments.videoSizeLimit,
                    imageSizeLimit = instance.configuration.mediaAttachments.imageSizeLimit,
                    imageMatrixLimit = instance.configuration.mediaAttachments.imageMatrixLimit,
                    maxMediaAttachments = instance.configuration.statuses.maxMediaAttachments,
                    maxFields = instance.pleroma?.metadata?.fieldLimits?.maxFields,
                    maxFieldNameLength = instance.pleroma?.metadata?.fieldLimits?.nameLength,
                    maxFieldValueLength = instance.pleroma?.metadata?.fieldLimits?.valueLength,
                )
            },
            {
                InstanceInfoEntity(
                    instance = domain,
                    maximumTootCharacters = DEFAULT_CHARACTER_LIMIT,
                    maxPollOptions = DEFAULT_MAX_OPTION_COUNT,
                    maxPollOptionLength = DEFAULT_MAX_OPTION_LENGTH,
                    minPollDuration = DEFAULT_MIN_POLL_DURATION,
                    maxPollDuration = DEFAULT_MAX_POLL_DURATION,
                    charactersReservedPerUrl = DEFAULT_CHARACTERS_RESERVED_PER_URL,
                    videoSizeLimit = DEFAULT_VIDEO_SIZE_LIMIT,
                    imageSizeLimit = DEFAULT_IMAGE_SIZE_LIMIT,
                    imageMatrixLimit = DEFAULT_IMAGE_MATRIX_LIMIT,
                    maxMediaAttachments = DEFAULT_MAX_MEDIA_ATTACHMENTS,
                    maxFields = DEFAULT_MAX_ACCOUNT_FIELDS,
                    maxFieldNameLength = null,
                    maxFieldValueLength = null,
                    version = "(Pachli defaults)",
                )
            },
        )
    }

    /**
     * @return True if at least one account has Android notifications enabled
     */
    // TODO: Should be `suspend`, accessed through a ViewModel, but not all the
    // calling code has been converted yet.
    fun areAndroidNotificationsEnabled(): Boolean {
        return accounts.any { it.notificationsEnabled }
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

    suspend fun setAlwaysShowSensitiveMedia(accountId: Long, value: Boolean) {
        accountDao.setAlwaysShowSensitiveMedia(accountId, value)
    }

    suspend fun setAlwaysOpenSpoiler(accountId: Long, value: Boolean) {
        accountDao.setAlwaysShowSensitiveMedia(accountId, value)
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

    companion object {
        const val DEFAULT_CHARACTER_LIMIT = 500
        const val DEFAULT_MAX_OPTION_COUNT = 4
        const val DEFAULT_MAX_OPTION_LENGTH = 50
        const val DEFAULT_MIN_POLL_DURATION = 300
        const val DEFAULT_MAX_POLL_DURATION = 604800

        val DEFAULT_VIDEO_SIZE_LIMIT = 40L.MiB
        val DEFAULT_IMAGE_SIZE_LIMIT = 10L.MiB
        const val DEFAULT_IMAGE_MATRIX_LIMIT = 4096 * 4096

        // Mastodon only counts URLs as this long in terms of status character limits
        const val DEFAULT_CHARACTERS_RESERVED_PER_URL = 23

        const val DEFAULT_MAX_MEDIA_ATTACHMENTS = 4
        const val DEFAULT_MAX_ACCOUNT_FIELDS = 4
    }
}
