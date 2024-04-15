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

import app.pachli.core.accounts.AccountManager
import app.pachli.core.common.extensions.MiB
import app.pachli.core.data.model.InstanceInfo
import app.pachli.core.database.dao.InstanceDao
import app.pachli.core.database.model.EmojisEntity
import app.pachli.core.database.model.InstanceInfoEntity
import app.pachli.core.network.model.Emoji
import app.pachli.core.network.retrofit.MastodonApi
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.getOrElse
import at.connyduck.calladapter.networkresult.onSuccess
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class InstanceInfoRepository @Inject constructor(
    private val api: MastodonApi,
    private val instanceDao: InstanceDao,
    accountManager: AccountManager,
) {
    private val instanceName = accountManager.activeAccount!!.domain

    /**
     * Returns the custom emojis of the instance.
     * Will always try to fetch them from the api, falls back to cached Emojis in case it is not available.
     * Never throws, returns empty list in case of error.
     */
    suspend fun getEmojis(): List<Emoji> = withContext(Dispatchers.IO) {
        api.getCustomEmojis()
            .onSuccess { emojiList -> instanceDao.upsert(EmojisEntity(instanceName, emojiList)) }
            .getOrElse { throwable ->
                Timber.w(throwable, "failed to load custom emojis, falling back to cache")
                instanceDao.getEmojiInfo(instanceName)?.emojiList.orEmpty()
            }
    }

    /**
     * Returns information about the instance.
     * Will always try to fetch the most up-to-date data from the api, falls back to cache in case it is not available.
     * Never throws, returns defaults of vanilla Mastodon in case of error.
     */
    suspend fun getInstanceInfo(): InstanceInfo = withContext(Dispatchers.IO) {
        api.getInstanceV1()
            .fold(
                { instance ->
                    val instanceEntity = InstanceInfoEntity(
                        instance = instanceName,
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
                { throwable ->
                    Timber.w(throwable, "failed to instance, falling back to cache and default values")
                    try {
                        instanceDao.getInstanceInfo(instanceName)
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

    companion object {
        const val DEFAULT_CHARACTER_LIMIT = 500
        private const val DEFAULT_MAX_OPTION_COUNT = 4
        private const val DEFAULT_MAX_OPTION_LENGTH = 50
        private const val DEFAULT_MIN_POLL_DURATION = 300
        private const val DEFAULT_MAX_POLL_DURATION = 604800

        private val DEFAULT_VIDEO_SIZE_LIMIT = 40L.MiB
        private val DEFAULT_IMAGE_SIZE_LIMIT = 10L.MiB
        private const val DEFAULT_IMAGE_MATRIX_LIMIT = 4096 * 4096

        // Mastodon only counts URLs as this long in terms of status character limits
        const val DEFAULT_CHARACTERS_RESERVED_PER_URL = 23

        const val DEFAULT_MAX_MEDIA_ATTACHMENTS = 4
        const val DEFAULT_MAX_ACCOUNT_FIELDS = 4
    }
}
