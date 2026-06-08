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

import app.pachli.core.database.model.InstanceInfoEntity
import app.pachli.core.model.ServerLimits.Companion.DEFAULT_CHARACTER_LIMIT
import app.pachli.core.model.ServerLimits.Companion.DEFAULT_MAX_ACCOUNT_FIELDS
import app.pachli.core.model.ServerLimits.Companion.DEFAULT_MAX_MEDIA_DESCRIPTION_CHARS
import app.pachli.core.network.model.InstanceV1
import app.pachli.core.network.model.InstanceV2

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
