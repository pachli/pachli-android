/* Copyright 2018 Levi Bard
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

import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_CHARACTERS_RESERVED_PER_URL
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_CHARACTER_LIMIT
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_IMAGE_MATRIX_LIMIT
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_IMAGE_SIZE_LIMIT
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_MAX_ACCOUNT_FIELDS
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_MAX_MEDIA_ATTACHMENTS
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_MAX_OPTION_COUNT
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_MAX_OPTION_LENGTH
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_MAX_POLL_DURATION
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_MIN_POLL_DURATION
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_VIDEO_FRAME_RATE_LIMIT
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_VIDEO_MATRIX_LIMIX
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_VIDEO_SIZE_LIMIT

/** https://docs.joinmastodon.org/entities/V1_Instance/ */
data class InstanceV1(
    val uri: String,
    // val title: String,
    // val description: String,
    // val email: String,
    val version: String,
    // val urls: Map<String, String>,
    // val stats: Map<String, Int>?,
    // val thumbnail: String?,
    // val languages: List<String>,
    // val contactAccount: Account,
    @Deprecated("Replaced with StatusConfiguration.max_characters")
    val maxTootChars: Int? = DEFAULT_CHARACTER_LIMIT,
    val pollConfiguration: PollConfiguration? = null,
    val configuration: InstanceConfiguration = InstanceConfiguration(),
    val maxMediaAttachments: Int = DEFAULT_MAX_MEDIA_ATTACHMENTS,
    val pleroma: PleromaConfiguration? = null,
    val uploadLimit: Long? = null,
    val rules: List<InstanceRules> = emptyList(),
) {
    override fun hashCode(): Int {
        return uri.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is InstanceV1) {
            return false
        }
        val instanceV1 = other as InstanceV1?
        return instanceV1?.uri.equals(uri)
    }
}

data class PollConfiguration(
    val maxOptions: Int = DEFAULT_MAX_OPTION_COUNT,
    val maxOptionChars: Int = DEFAULT_MAX_OPTION_LENGTH,
    val maxCharactersPerOption: Int = DEFAULT_MAX_OPTION_LENGTH,
    val minExpiration: Int = DEFAULT_MIN_POLL_DURATION,
    val maxExpiration: Long = DEFAULT_MAX_POLL_DURATION,
)

data class InstanceConfiguration(
    val statuses: StatusConfiguration = StatusConfiguration(),
    val mediaAttachments: MediaAttachmentConfiguration = MediaAttachmentConfiguration(),
    val polls: PollConfiguration = PollConfiguration(),
)

data class StatusConfiguration(
    val maxCharacters: Int? = null,
    val maxMediaAttachments: Int = DEFAULT_MAX_OPTION_COUNT,
    val charactersReservedPerUrl: Int = DEFAULT_CHARACTERS_RESERVED_PER_URL,
)

data class MediaAttachmentConfiguration(
    val supportedMimeTypes: List<String> = emptyList(),
    val imageSizeLimit: Long = DEFAULT_IMAGE_SIZE_LIMIT,
    val imageMatrixLimit: Int = DEFAULT_IMAGE_MATRIX_LIMIT,
    val videoSizeLimit: Long = DEFAULT_VIDEO_SIZE_LIMIT,
    val videoFrameRateLimit: Int? = DEFAULT_VIDEO_FRAME_RATE_LIMIT,
    val videoMatrixLimit: Int? = DEFAULT_VIDEO_MATRIX_LIMIX,
)

data class PleromaConfiguration(
    val metadata: PleromaMetadata?,
)

data class PleromaMetadata(
    val fieldLimits: PleromaFieldLimits,
)

data class PleromaFieldLimits(
    val maxFields: Int = DEFAULT_MAX_ACCOUNT_FIELDS,
    val nameLength: Int?,
    val valueLength: Int?,
)

data class InstanceRules(
    val id: String,
    val text: String,
)
