/*
 * Copyright 2025 Pachli Association
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

package app.pachli.core.model

// Know that these fields are all used somewhere
//
// Server.Kt also uses v2.configuration.translation.enabled
data class InstanceInfo(
    val maxChars: Int = DEFAULT_CHARACTER_LIMIT,
    val pollMaxOptions: Int = DEFAULT_MAX_OPTION_COUNT,
    val pollMaxLength: Int = DEFAULT_MAX_OPTION_LENGTH,
    val pollMinDuration: Int = DEFAULT_MIN_POLL_DURATION,
    val pollMaxDuration: Long = DEFAULT_MAX_POLL_DURATION,
    val charactersReservedPerUrl: Int = DEFAULT_CHARACTERS_RESERVED_PER_URL,
    val videoSizeLimit: Long = DEFAULT_VIDEO_SIZE_LIMIT,
    val imageSizeLimit: Long = DEFAULT_IMAGE_SIZE_LIMIT,
    val imageMatrixLimit: Int = DEFAULT_IMAGE_MATRIX_LIMIT,
    val maxMediaAttachments: Int = DEFAULT_MAX_MEDIA_ATTACHMENTS,
    /** Maximum number of characters in a media description. */
    val maxMediaDescriptionChars: Int = DEFAULT_MAX_MEDIA_DESCRIPTION_CHARS,
    val maxFields: Int = DEFAULT_MAX_ACCOUNT_FIELDS,
    val maxFieldNameLength: Int? = null,
    val maxFieldValueLength: Int? = null,
    val version: String = "(Pachli defaults)",
) {
    companion object {
        const val DEFAULT_CHARACTER_LIMIT = 500
        const val DEFAULT_MAX_OPTION_COUNT = 4
        const val DEFAULT_MAX_OPTION_LENGTH = 50
        const val DEFAULT_MIN_POLL_DURATION = 300
        const val DEFAULT_MAX_POLL_DURATION = 604800L

        const val DEFAULT_VIDEO_SIZE_LIMIT = 40L * 1024 * 1024 // 40 MiB
        const val DEFAULT_VIDEO_MATRIX_LIMIX = 4096 * 4096
        const val DEFAULT_VIDEO_FRAME_RATE_LIMIT = 30
        const val DEFAULT_IMAGE_SIZE_LIMIT = 10L * 1024 * 1024 // 10 MiB
        const val DEFAULT_IMAGE_MATRIX_LIMIT = 4096 * 4096

        // Mastodon only counts URLs as this long in terms of status character limits
        const val DEFAULT_CHARACTERS_RESERVED_PER_URL = 23

        const val DEFAULT_MAX_MEDIA_ATTACHMENTS = 4

        // Default Mastodon limit
        const val DEFAULT_MAX_MEDIA_DESCRIPTION_CHARS = 1500
        const val DEFAULT_MAX_ACCOUNT_FIELDS = 4
    }
}
