/*
 * Copyright (c) 2026 Pachli Association
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

/**
 * Normalised information from the API `instance` endpoint, either
 * v1 or v2.
 *
 * @property maxPostCharacters Maximum number of characters allowed in a post.
 * @property maxPollOptions Maximum number of options allowed in a poll.
 * @property maxPollOptionLength Maximum number of characters allowed in a poll option.
 * @property minPollDuration Minimum duration allowed in a poll.
 * @property maxPollDuration Maximum duration allowed in a poll.
 * @property charactersReservedPerUrl Number of characters reserved per URL.
 * @property version Raw version string of the instance.
 * @property videoSizeLimit Maximum video size allowed in bytes.
 * @property imageSizeLimit Maximum image size allowed in bytes.
 * @property imageMatrixLimit Maximum image matrix size in bytes.
 * @property maxMediaAttachments Maximum number of media attachments.
 * @property maxMediaDescriptionChars Maximum number of characters in a media description.
 * @property maxFields Maximum number of fields in an account.
 * @property maxFieldNameLength Maximum length of an account field name.
 * @property maxFieldValueLength Maximum length of an account field value.
 * @property enabledTranslation True if the server's translation feature is enabled.
 */
data class InstanceInfo(
    val maxPostCharacters: Int,
    val maxPollOptions: Int,
    val maxPollOptionLength: Int,
    val minPollDuration: Int,
    val maxPollDuration: Long,
    val charactersReservedPerUrl: Int,
    val version: String,
    val videoSizeLimit: Long,
    val imageSizeLimit: Long,
    val imageMatrixLimit: Int,
    val maxMediaAttachments: Int,
    val maxMediaDescriptionChars: Int,
    val maxFields: Int,
    val maxFieldNameLength: Int?,
    val maxFieldValueLength: Int?,
    val enabledTranslation: Boolean = false,
)

/**
 * @return [ServerLimits] model; if this is null then returns the default
 * [ServerLimits] values.
 */
fun InstanceInfo?.asServerLimits(): ServerLimits {
    if (this == null) return ServerLimits()
    return ServerLimits(
        maxChars = maxPostCharacters,
        pollMaxOptions = maxPollOptions,
        pollMaxLength = maxPollOptionLength,
        pollMinDuration = minPollDuration,
        pollMaxDuration = maxPollDuration,
        charactersReservedPerUrl = charactersReservedPerUrl,
        videoSizeLimit = videoSizeLimit,
        imageSizeLimit = imageSizeLimit,
        imageMatrixLimit = imageMatrixLimit,
        maxMediaAttachments = maxMediaAttachments,
        maxMediaDescriptionChars = maxMediaDescriptionChars,
        maxFields = maxFields,
        maxFieldNameLength = maxFieldNameLength,
        maxFieldValueLength = maxFieldValueLength,
    )
}
