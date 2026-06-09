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

data class InstanceInfo(
    val instance: String,
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
