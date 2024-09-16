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

package app.pachli.core.data.model

import app.pachli.core.common.extensions.MiB

// Know that these fields are all used somewhere
//
// Server.Kt also uses v2.configuration.translation.enabled
data class InstanceInfo(
    val maxChars: Int = DEFAULT_CHARACTER_LIMIT,
    val pollMaxOptions: Int = DEFAULT_MAX_OPTION_COUNT,
    val pollMaxLength: Int = DEFAULT_MAX_OPTION_LENGTH,
    val pollMinDuration: Int = DEFAULT_MIN_POLL_DURATION,
    val pollMaxDuration: Int = DEFAULT_MAX_POLL_DURATION,
    val charactersReservedPerUrl: Int = DEFAULT_CHARACTERS_RESERVED_PER_URL,
    val videoSizeLimit: Long = DEFAULT_VIDEO_SIZE_LIMIT,
    val imageSizeLimit: Long = DEFAULT_IMAGE_SIZE_LIMIT,
    val imageMatrixLimit: Int = DEFAULT_IMAGE_MATRIX_LIMIT,
    val maxMediaAttachments: Int = DEFAULT_MAX_MEDIA_ATTACHMENTS,
    val maxFields: Int = DEFAULT_MAX_ACCOUNT_FIELDS,
    val maxFieldNameLength: Int? = null,
    val maxFieldValueLength: Int? = null,
    val version: String? = "(Pachli defaults)",
) {
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

        fun from(info: app.pachli.core.database.model.InstanceInfoEntity): InstanceInfo {
            return InstanceInfo(
                maxChars = info.maxPostCharacters ?: DEFAULT_CHARACTER_LIMIT,
                pollMaxOptions = info.maxPollOptions ?: DEFAULT_MAX_OPTION_COUNT,
                pollMaxLength = info.maxPollOptionLength ?: DEFAULT_MAX_OPTION_COUNT,
                pollMinDuration = info.minPollDuration ?: DEFAULT_MIN_POLL_DURATION,
                pollMaxDuration = info.maxPollDuration ?: DEFAULT_MAX_POLL_DURATION,
                charactersReservedPerUrl = info.charactersReservedPerUrl ?: DEFAULT_CHARACTERS_RESERVED_PER_URL,
                videoSizeLimit = info.videoSizeLimit ?: DEFAULT_VIDEO_SIZE_LIMIT,
                imageSizeLimit = info.imageSizeLimit ?: DEFAULT_IMAGE_SIZE_LIMIT,
                imageMatrixLimit = info.imageMatrixLimit ?: DEFAULT_IMAGE_MATRIX_LIMIT,
                maxMediaAttachments = info.maxMediaAttachments ?: DEFAULT_MAX_MEDIA_ATTACHMENTS,
                maxFields = info.maxFields ?: DEFAULT_MAX_ACCOUNT_FIELDS,
                maxFieldNameLength = info.maxFieldNameLength,
                maxFieldValueLength = info.maxFieldValueLength,
                version = info.version ?: "(Pachli defaults)",
            )
        }
    }
}
