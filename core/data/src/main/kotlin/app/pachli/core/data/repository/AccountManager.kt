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

import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.model.InstanceInfo
import app.pachli.core.data.model.Server
import app.pachli.core.data.model.from
import app.pachli.core.data.repository.ContentFiltersError.GetContentFiltersError
import app.pachli.core.data.repository.ContentFiltersError.ServerDoesNotFilter
import app.pachli.core.data.repository.ServerRepository.Error
import app.pachli.core.data.repository.ServerRepository.Error.GetNodeInfo
import app.pachli.core.data.repository.ServerRepository.Error.GetWellKnownNodeInfo
import app.pachli.core.data.repository.ServerRepository.Error.UnsupportedSchema
import app.pachli.core.data.repository.ServerRepository.Error.ValidateNodeInfo
import app.pachli.core.database.dao.AccountDao
import app.pachli.core.database.dao.ContentFiltersDao
import app.pachli.core.database.dao.InstanceDao
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.ContentFiltersEntity
import app.pachli.core.database.model.EmojisEntity
import app.pachli.core.database.model.InstanceInfoEntity
import app.pachli.core.database.model.MastodonListEntity
import app.pachli.core.database.model.ServerCapabilitiesEntity
import app.pachli.core.model.ContentFilter
import app.pachli.core.model.ContentFilterVersion
import app.pachli.core.model.ServerOperation
import app.pachli.core.model.Timeline
import app.pachli.core.network.model.Emoji
import app.pachli.core.network.model.MastoList
import app.pachli.core.network.model.Status
import app.pachli.core.network.model.UserListRepliesPolicy
import app.pachli.core.network.model.nodeinfo.NodeInfo
import app.pachli.core.network.retrofit.InstanceSwitchAuthInterceptor
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.NodeInfoApi
import app.pachli.core.network.retrofit.apiresult.ApiError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.mapOr
import com.github.michaelbull.result.onSuccess
import io.github.z4kn4fein.semver.Version
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

sealed interface Loadable<T> {
    class Loading<T>() : Loadable<T>
    data class Loaded<T>(val data: T) : Loadable<T>
}

// TODO: Still not sure if it's better to have one class that contains everything,
// or provide dedicated functions that return specific flows for the different
// things, parameterised by the account ID.
data class PachliAccount(
    val id: Long,
    // TODO: Should be a core.data type
    val entity: AccountEntity,
    val instanceInfo: InstanceInfo,
    val lists: List<MastodonList>,
    val emojis: List<Emoji>,
    val capabilities: Map<ServerOperation, Version>,
    val contentFilters: ContentFilters,
) {
    companion object {
        fun make(
            account: app.pachli.core.database.model.PachliAccount,
        ): PachliAccount {
            return PachliAccount(
                id = account.account.id,
                entity = account.account,
                instanceInfo = InstanceInfo.from(account.instanceInfo),
                lists = account.lists.map { MastodonList.from(it) },
                emojis = account.emojis?.emojiList.orEmpty(),
                capabilities = account.serverCapabilities.capabilities,
                contentFilters = ContentFilters(
                    version = account.contentFilters.version,
                    contentFilters = account.contentFilters.contentFilters,
                ),
            )
        }
    }
}

data class MastodonList(
    val accountId: Long,
    val listId: String,
    val title: String,
    val repliesPolicy: UserListRepliesPolicy,
    val exclusive: Boolean,
) {
    fun entity() = MastodonListEntity(
        accountId = accountId,
        listId = listId,
        title = title,
        repliesPolicy = repliesPolicy,
        exclusive = exclusive,
    )
    companion object {
        fun from(entity: MastodonListEntity) = MastodonList(
            accountId = entity.accountId,
            listId = entity.listId,
            title = entity.title,
            repliesPolicy = entity.repliesPolicy,
            exclusive = entity.exclusive,
        )
    }
}

// Note to self: This is doing dual duty as a repository and as a collection of
// use cases, and should be refactored along those lines.

@Singleton
class AccountManager @Inject constructor(
    private val transactionProvider: TransactionProvider,
    private val mastodonApi: MastodonApi,
    private val nodeInfoApi: NodeInfoApi,
    private val accountDao: AccountDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val instanceDao: InstanceDao,
    private val contentFiltersDao: ContentFiltersDao,
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
//            Timber.d("get() activeAccount: %s", activeAccountFlow.value)
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

    val activePachliAccountFlow = accountDao.getActivePachliAccountFlow()
        .filterNotNull()
//        .distinctUntilChanged()
//        .onEach { Timber.d("AM PachliAccount: %s", it) }
        .map { PachliAccount.make(it) }
    // .stateIn(externalScope, SharingStarted.Eagerly, Loadable.Loading())

    suspend fun getPachliAccount(accountId: Long): PachliAccount? {
        val id = accountId.takeIf { it != -1L } ?: accountDao.getActiveAccount()?.id
        id ?: return null

        return accountDao.getPachliAccount(id)?.let {
            PachliAccount.make(it)
        }
    }

    suspend fun getPachliAccountFlow(accountId: Long): Flow<PachliAccount?> {
        val id = accountId.takeIf { it != -1L } ?: accountDao.getActiveAccount()?.id
        Timber.d("PachliAccount id: %d", id)
        id ?: return flowOf(null)

        return accountDao.getPachliAccountFlow(id)
//        .distinctUntilChanged()
//            .onEach { Timber.d("AM PachliAccount: %s", it) }
            .map { it?.let { PachliAccount.make(it) } }
            .stateIn(externalScope, SharingStarted.Eagerly, null)
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
                val now = TimeSource.Monotonic.markNow()

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
                // - lists
                // - server info
                // - filters (depends on server info)
                //
                // InstanceInfoEntity will need to be a merger of whatever you get from
                // instancev1 and instance2.

                // Caution here -- doing database operations in any of the externalScope blocks
                // appears not to release database connections properly, resulting in starvation.

                val deferNodeInfo = externalScope.async {
                    fetchNodeInfo()
                }
                val deferInstanceInfo = externalScope.async {
                    fetchInstanceInfo(finalAccount.domain)
                }
                val deferEmojis = externalScope.async {
                    mastodonApi.getCustomEmojis()
                }
                val deferLists = externalScope.async {
                    mastodonApi.getLists().get()?.body.orEmpty().map {
                        MastodonListEntity(
                            finalAccount.id,
                            it.id,
                            it.title,
                            it.repliesPolicy,
                            it.exclusive ?: false,
                        )
                    }
                }

                val nodeInfo = deferNodeInfo.await()

                deferInstanceInfo.await().also { instanceInfoEntity ->
                    instanceDao.upsert(instanceInfoEntity)
                }

                // Create the server info so it can used for both server capabilities and filters.
                val server = deferInstanceInfo.await().let { instanceInfoEntity ->
                    nodeInfo.map { Server.from(it.software, instanceInfoEntity).get() }
                }.get()

                server?.let {
                    ServerCapabilitiesEntity(accountId = finalAccount.id, capabilities = it.capabilities).also {
                        instanceDao.upsert(it)
                    }
                }

                val deferContentFilters = server?.let {
                    externalScope.async {
                        getContentFilters(server)
                    }
                }

                deferContentFilters?.await()?.let { result ->
                    result.onSuccess { contentFilters ->
                        contentFiltersDao.upsert(
                            ContentFiltersEntity(
                                accountId = finalAccount.id,
                                version = contentFilters.version,
                                contentFilters = contentFilters.contentFilters,
                            ),
                        )
                    }
                }

                deferEmojis.await().also { result ->
                    result.onSuccess {
                        instanceDao.upsert(EmojisEntity(accountId = finalAccount.id, emojiList = it.body))
                    }
                }

                deferLists.await().also { lists ->
                    accountDao.deleteMastodonListsForAccount(finalAccount.id)
                    lists.forEach { accountDao.upsertMastodonList(it) }
                    newTabPreferences(finalAccount.id, lists)?.let {
                        setTabPreferences(finalAccount.id, it)
                    }
                }

                Timber.d("Switched accounts took %d ms", now.elapsedNow().inWholeMilliseconds)
            }

            return Ok(Unit)
        } catch (e: ApiErrorException) {
            return Err(e.apiError)
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
     * @param account The account to check
     * @param lists The account's latest lists
     * @return A list of new tab preferences for [account], or null if there are no changes.
     */
    private suspend fun newTabPreferences(accountId: Long, lists: List<MastodonListEntity>): List<Timeline>? {
        val map = lists.associateBy { it.listId }
        val account = accountDao.getAccountById(accountId) ?: return null
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

    private suspend fun fetchNodeInfo(): Result<NodeInfo, Error> = binding {
        /**
         * NodeInfo schema versions we can parse.
         *
         * See https://nodeinfo.diaspora.software/schema.html.
         */
        val SCHEMAS = listOf(
            "http://nodeinfo.diaspora.software/ns/schema/2.1",
            "http://nodeinfo.diaspora.software/ns/schema/2.0",
            "http://nodeinfo.diaspora.software/ns/schema/1.1",
            "http://nodeinfo.diaspora.software/ns/schema/1.0",
        )

        // Fetch the /.well-known/nodeinfo document
        val nodeInfoJrd = nodeInfoApi.nodeInfoJrd()
            .mapError { GetWellKnownNodeInfo(it) }.bind().body

        // Find a link to a schema we can parse, prefering newer schema versions
        var nodeInfoUrlResult: Result<String, Error> = Err(UnsupportedSchema)
        for (link in nodeInfoJrd.links.sortedByDescending { it.rel }) {
            if (SCHEMAS.contains(link.rel)) {
                nodeInfoUrlResult = Ok(link.href)
                break
            }
        }

        val nodeInfoUrl = nodeInfoUrlResult.bind()

        Timber.d("Loading node info from %s", nodeInfoUrl)
        val nodeInfo = nodeInfoApi.nodeInfo(nodeInfoUrl).mapBoth(
            { NodeInfo.from(it.body).mapError { ValidateNodeInfo(nodeInfoUrl, it) } },
            { Err(GetNodeInfo(nodeInfoUrl, it)) },
        ).bind()

        return@binding nodeInfo
    }

    // TODO: Maybe rename InstanceInfoEntity to ServerLimits or something like that, since that's
    // what it records.
    private suspend fun fetchInstanceInfo(domain: String): InstanceInfoEntity {
        // This needs to start with v2, and fall back to v1
        // InstanceInfoEntity needs to gain support for recording translation

        return mastodonApi.getInstanceV1().mapOr(InstanceInfoEntity.defaultForDomain(domain)) { result ->
            InstanceInfoEntity.make(domain, result.body)
        }
    }

    /** Get the current set of content filters. */
    // Copied from ContentFiltersRepository
    private suspend fun getContentFilters(server: Server): Result<ContentFilters, ContentFiltersError> = binding {
        when {
            server.canFilterV2() -> mastodonApi.getContentFilters().map {
                ContentFilters(
                    contentFilters = it.body.map { ContentFilter.from(it) },
                    version = ContentFilterVersion.V2,
                )
            }

            server.canFilterV1() -> mastodonApi.getContentFiltersV1().map {
                ContentFilters(
                    contentFilters = it.body.map { ContentFilter.from(it) },
                    version = ContentFilterVersion.V1,
                )
            }

            else -> Err(ServerDoesNotFilter)
        }.mapError { GetContentFiltersError(it) }.bind()
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

    // -- Lists

    fun getLists(accountId: Long) = accountDao.getMastodonListsForAccountFlow(accountId).map {
        it.map { MastodonList.from(it) }
    }

    suspend fun refreshLists(accountId: Long, lists: List<MastoList>) {
        transactionProvider {
            accountDao.deleteMastodonListsForAccount(accountId)
            val entities = lists.map {
                MastodonListEntity(
                    accountId,
                    it.id,
                    it.title,
                    it.repliesPolicy,
                    it.exclusive ?: false,
                )
            }
            entities.forEach { accountDao.upsertMastodonList(it) }
            newTabPreferences(accountId, entities)?.let {
                setTabPreferences(accountId, it)
            }
        }
    }

    suspend fun createList(list: MastodonList) = accountDao.upsertMastodonList(list.entity())

    suspend fun deleteList(list: MastodonList) {
        transactionProvider {
            accountDao.deleteMastodonListForAccount(list.accountId, list.listId)
            val lists = accountDao.getMastodonListsForAccount(list.accountId)
            newTabPreferences(list.accountId, lists)?.let {
                setTabPreferences(list.accountId, it)
            }
        }
    }

    suspend fun editList(list: MastodonList) {
        transactionProvider {
            accountDao.upsertMastodonList(list.entity())
            val lists = accountDao.getMastodonListsForAccount(list.accountId)
            newTabPreferences(list.accountId, lists)?.let {
                setTabPreferences(list.accountId, it)
            }
        }
    }
}
