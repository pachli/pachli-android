/*
 * Copyright 2022 Tusky contributors
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

import androidx.annotation.VisibleForTesting
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.database.dao.InstanceDao
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.EmojisEntity
import app.pachli.core.database.model.InstanceInfoEntity
import app.pachli.core.database.model.asModel
import app.pachli.core.model.Emoji
import app.pachli.core.model.InstanceInfo
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_CHARACTER_LIMIT
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_MAX_ACCOUNT_FIELDS
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_MAX_MEDIA_DESCRIPTION_CHARS
import app.pachli.core.network.model.InstanceV1
import app.pachli.core.network.model.InstanceV2
import app.pachli.core.network.model.asModel
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.orElse
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

// TODO: Not a great bit of design here
//
// A few problems:
//
// - There's overlap with the responsibilties of ServerRepository, which makes
//   things unnecessarily confusing
// - This probably shouldn't be broadly exposed; the instance info (and the
//   Server information that ServerRepository provides) should be properties
//   of the user's account.
//
// A better design would be to fetch the server info when the account changes,
// and provide it as properties on the account. Better encapsulation and the
// data flow is less confusing.
//
// That would ensure you can't have a situation where the activeaccount is
// non-null but the instanceinfo flow here is the default (race condition),
// reduces the number of null checks or "!!" code smells, reduces the number
// of distinct objects that need to be passed around as parameters, etc.

@Singleton
class InstanceInfoRepository @Inject constructor(
    private val api: MastodonApi,
    private val instanceDao: InstanceDao,
    accountManager: AccountManager,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val _instanceInfo = MutableStateFlow(InstanceInfo())
    val instanceInfo = _instanceInfo.asStateFlow()

    private val _emojis = MutableStateFlow<List<Emoji>>(emptyList())
    val emojis = _emojis.asStateFlow()

    init {
        externalScope.launch {
            accountManager.activeAccountFlow
                .filterIsInstance<Loadable.Loaded<AccountEntity?>>()
                .distinctUntilChangedBy { it.data?.id }
                .collect { loadable ->
                    reload(loadable.data)
                }
        }
    }

    /** Reload instance info because the active account has changed */
    // This is a hack so that unit tests have a mechanism for triggering a refetch
    // of the data.
    @VisibleForTesting
    suspend fun reload(account: AccountEntity?) {
        if (account == null) {
            Timber.d("active account is null, using instance defaults")
            _instanceInfo.value = InstanceInfo()
            _emojis.value = emptyList()
            return
        }

        Timber.d("Fetching instance info for %s", account.domain)

        _instanceInfo.value = getInstanceInfo(account.domain)
        _emojis.value = getEmojis(account.id)
    }

    /**
     * Returns the custom emojis of the instance.
     * Will always try to fetch them from the api, falls back to cached Emojis in case it is not available.
     * Never throws, returns empty list in case of error.
     */
    private suspend fun getEmojis(accountId: Long): List<Emoji> = withContext(Dispatchers.IO) {
        return@withContext api.getCustomEmojis().mapBoth(
            { emojiList ->
                val emojis = emojiList.body.asModel()
                instanceDao.upsert(EmojisEntity(accountId, emojis))
                emojis
            },
            { error ->
                Timber.w(error.throwable, "failed to load custom emojis, falling back to cache")
                instanceDao.getEmojiInfo(accountId)?.emojiList.orEmpty()
            },
        )
    }

    /**
     * Refreshes the local cache of instance info, and returns it.
     *
     * If the network call fails then returns the cached copy. If there is no cached copy
     * then returns vanilla defaults.
     */
    private suspend fun getInstanceInfo(domain: String): InstanceInfo {
        return api.getInstanceV2()
            .map { it.body.asEntity(domain) }
            .orElse { api.getInstanceV1().map { it.body.asEntity(domain) } }
            .onSuccess { instanceDao.upsert(it) }
            .mapBoth({ it.asModel() }, { InstanceInfo() })
    }
}

/**
 * Returns [InstanceInfoEntity] for this [InstanceV1].
 *
 * There's no guarantee the [InstanceV1.uri] field will be just the domain, as some
 * servers return URLs or possibly other junk (https://akkoma.dev/AkkomaGang/akkoma/issues/907,
 * https://activitypub.software/TransFem-org/Sharkey/-/issues/1046), so require the
 * caller to explicitly provide the domain to use as the primary key for this entity.
 *
 * @param domain Primary key for this domain
 */
fun InstanceV1.asEntity(domain: String) = InstanceInfoEntity(
    instance = domain,
    maxPostCharacters = configuration.statuses.maxCharacters ?: maxTootChars ?: DEFAULT_CHARACTER_LIMIT,
    maxPollOptions = configuration.polls.maxOptions,
    maxPollOptionLength = configuration.polls.maxCharactersPerOption,
    minPollDuration = configuration.polls.minExpiration,
    maxPollDuration = configuration.polls.maxExpiration,
    charactersReservedPerUrl = configuration.statuses.charactersReservedPerUrl,
    version = version,
    videoSizeLimit = configuration.mediaAttachments.videoSizeLimit,
    imageSizeLimit = configuration.mediaAttachments.imageSizeLimit,
    imageMatrixLimit = configuration.mediaAttachments.imageMatrixLimit,
    maxMediaAttachments = configuration.statuses.maxMediaAttachments,
    maxMediaDescriptionChars = DEFAULT_MAX_MEDIA_DESCRIPTION_CHARS,
    maxFields = pleroma?.metadata?.fieldLimits?.maxFields ?: DEFAULT_MAX_ACCOUNT_FIELDS,
    maxFieldNameLength = null,
    maxFieldValueLength = null,
    enabledTranslation = false,
)

/**
 * Returns [InstanceInfoEntity] for this [InstanceV2].
 *
 * There's no guarantee the [InstanceV2.domain] field will be just the domain, as some
 * servers return URLs or possibly other junk (https://akkoma.dev/AkkomaGang/akkoma/issues/907,
 * https://activitypub.software/TransFem-org/Sharkey/-/issues/1046), so require the
 * caller to explicitly provide the domain to use as the primary key for this entity.
 *
 * @param domain Primary key for this domain
 */
fun InstanceV2.asEntity(domain: String) = InstanceInfoEntity(
    instance = domain,
    maxPostCharacters = configuration.statuses.maxCharacters,
    maxPollOptions = configuration.polls.maxOptions,
    maxPollOptionLength = configuration.polls.maxCharactersPerOption,
    minPollDuration = configuration.polls.minExpiration,
    maxPollDuration = configuration.polls.maxExpiration,
    charactersReservedPerUrl = configuration.statuses.charactersReservedPerUrl,
    version = version,
    videoSizeLimit = configuration.mediaAttachments.videoSizeLimit,
    imageSizeLimit = configuration.mediaAttachments.imageSizeLimit,
    imageMatrixLimit = configuration.mediaAttachments.imageMatrixLimit,
    maxMediaAttachments = configuration.statuses.maxMediaAttachments,
    maxMediaDescriptionChars = configuration.mediaAttachments.descriptionLimit,
    maxFields = DEFAULT_MAX_ACCOUNT_FIELDS,
    maxFieldNameLength = null,
    maxFieldValueLength = null,
    enabledTranslation = configuration.translation.enabled,
)
