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
import app.pachli.core.accounts.AccountManager
import app.pachli.core.accounts.Loadable
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.model.InstanceInfo
import app.pachli.core.data.model.InstanceInfo.Companion.DEFAULT_CHARACTERS_RESERVED_PER_URL
import app.pachli.core.data.model.InstanceInfo.Companion.DEFAULT_CHARACTER_LIMIT
import app.pachli.core.data.model.InstanceInfo.Companion.DEFAULT_IMAGE_MATRIX_LIMIT
import app.pachli.core.data.model.InstanceInfo.Companion.DEFAULT_IMAGE_SIZE_LIMIT
import app.pachli.core.data.model.InstanceInfo.Companion.DEFAULT_MAX_ACCOUNT_FIELDS
import app.pachli.core.data.model.InstanceInfo.Companion.DEFAULT_MAX_MEDIA_ATTACHMENTS
import app.pachli.core.data.model.InstanceInfo.Companion.DEFAULT_MAX_OPTION_COUNT
import app.pachli.core.data.model.InstanceInfo.Companion.DEFAULT_MAX_OPTION_LENGTH
import app.pachli.core.data.model.InstanceInfo.Companion.DEFAULT_MAX_POLL_DURATION
import app.pachli.core.data.model.InstanceInfo.Companion.DEFAULT_MIN_POLL_DURATION
import app.pachli.core.data.model.InstanceInfo.Companion.DEFAULT_VIDEO_SIZE_LIMIT
import app.pachli.core.database.dao.InstanceDao
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.EmojisEntity
import app.pachli.core.database.model.InstanceInfoEntity
import app.pachli.core.network.model.Emoji
import app.pachli.core.network.retrofit.MastodonApi
import at.connyduck.calladapter.networkresult.getOrElse
import at.connyduck.calladapter.networkresult.onSuccess
import com.github.michaelbull.result.mapBoth
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
        _emojis.value = getEmojis(account.domain)
    }

    /**
     * Returns the custom emojis of the instance.
     * Will always try to fetch them from the api, falls back to cached Emojis in case it is not available.
     * Never throws, returns empty list in case of error.
     */
    private suspend fun getEmojis(domain: String): List<Emoji> = withContext(Dispatchers.IO) {
        api.getCustomEmojis()
            .onSuccess { emojiList -> instanceDao.upsert(EmojisEntity(domain, emojiList)) }
            .getOrElse { throwable ->
                Timber.w(throwable, "failed to load custom emojis, falling back to cache")
                instanceDao.getEmojiInfo(domain)?.emojiList.orEmpty()
            }
    }

    /**
     * Returns information about the instance.
     * Will always try to fetch the most up-to-date data from the api, falls back to cache in case it is not available.
     * Never throws, returns defaults of vanilla Mastodon in case of error.
     */
    private suspend fun getInstanceInfo(domain: String): InstanceInfo {
        return api.getInstanceV1()
            .mapBoth(
                { result ->
                    val instance = result.body
                    val instanceEntity = InstanceInfoEntity(
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
                    try {
                        instanceDao.upsert(instanceEntity)
                    } catch (_: Exception) { }
                    instanceEntity
                },
                { error ->
                    Timber.w(error.throwable, "failed to instance, falling back to cache and default values")
                    try {
                        instanceDao.getInstanceInfo(domain)
                    } catch (_: Exception) {
                        null
                    }
                },
            ).let { instanceInfo: InstanceInfoEntity? ->
                InstanceInfo(
                    maxChars = instanceInfo?.maximumTootCharacters ?: DEFAULT_CHARACTER_LIMIT,
                    pollMaxOptions = instanceInfo?.maxPollOptions ?: DEFAULT_MAX_OPTION_COUNT,
                    pollMaxLength = instanceInfo?.maxPollOptionLength ?: DEFAULT_MAX_OPTION_LENGTH,
                    pollMinDuration = instanceInfo?.minPollDuration ?: DEFAULT_MIN_POLL_DURATION,
                    pollMaxDuration = instanceInfo?.maxPollDuration ?: DEFAULT_MAX_POLL_DURATION,
                    charactersReservedPerUrl = instanceInfo?.charactersReservedPerUrl ?: DEFAULT_CHARACTERS_RESERVED_PER_URL,
                    videoSizeLimit = instanceInfo?.videoSizeLimit ?: DEFAULT_VIDEO_SIZE_LIMIT,
                    imageSizeLimit = instanceInfo?.imageSizeLimit ?: DEFAULT_IMAGE_SIZE_LIMIT,
                    imageMatrixLimit = instanceInfo?.imageMatrixLimit ?: DEFAULT_IMAGE_MATRIX_LIMIT,
                    maxMediaAttachments = instanceInfo?.maxMediaAttachments ?: DEFAULT_MAX_MEDIA_ATTACHMENTS,
                    maxFields = instanceInfo?.maxFields ?: DEFAULT_MAX_ACCOUNT_FIELDS,
                    maxFieldNameLength = instanceInfo?.maxFieldNameLength,
                    maxFieldValueLength = instanceInfo?.maxFieldValueLength,
                    version = instanceInfo?.version,
                )
            }
    }
}
