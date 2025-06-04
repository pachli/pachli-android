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

package app.pachli.core.network.model

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
import app.pachli.core.network.json.DefaultIfNull
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** https://docs.joinmastodon.org/entities/V1_Instance/ */
@JsonClass(generateAdapter = true)
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
    // @Json(name = "contact_account") val contactAccount: Account,
    @Deprecated("Replaced with StatusConfiguration.max_characters")
    @DefaultIfNull
    @Json(name = "max_toot_chars") val maxTootChars: Int? = DEFAULT_CHARACTER_LIMIT,
    @Json(name = "poll_limits") val pollConfiguration: PollConfiguration? = null,
    val configuration: InstanceConfiguration = InstanceConfiguration(),
    @Json(name = "max_media_attachments") val maxMediaAttachments: Int = DEFAULT_MAX_MEDIA_ATTACHMENTS,
    val pleroma: PleromaConfiguration? = null,
    @Json(name = "upload_limit") val uploadLimit: Long? = null,
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

@JsonClass(generateAdapter = true)
data class PollConfiguration(
    @Json(name = "max_options") val maxOptions: Int = DEFAULT_MAX_OPTION_COUNT,
    @Json(name = "max_option_chars") val maxOptionChars: Int = DEFAULT_MAX_OPTION_LENGTH,
    @Json(name = "max_characters_per_option") val maxCharactersPerOption: Int = DEFAULT_MAX_OPTION_LENGTH,
    @Json(name = "min_expiration") val minExpiration: Int = DEFAULT_MIN_POLL_DURATION,
    @Json(name = "max_expiration") val maxExpiration: Long = DEFAULT_MAX_POLL_DURATION,
)

@JsonClass(generateAdapter = true)
data class InstanceConfiguration(
    val statuses: StatusConfiguration = StatusConfiguration(),
    @Json(name = "media_attachments") val mediaAttachments: MediaAttachmentConfiguration = MediaAttachmentConfiguration(),
    val polls: PollConfiguration = PollConfiguration(),
)

@JsonClass(generateAdapter = true)
data class StatusConfiguration(
    @Json(name = "max_characters") val maxCharacters: Int? = null,
    @Json(name = "max_media_attachments") val maxMediaAttachments: Int = DEFAULT_MAX_OPTION_COUNT,
    @Json(name = "characters_reserved_per_url") val charactersReservedPerUrl: Int = DEFAULT_CHARACTERS_RESERVED_PER_URL,
)

@JsonClass(generateAdapter = true)
data class MediaAttachmentConfiguration(
    @Json(name = "supported_mime_types") val supportedMimeTypes: List<String> = emptyList(),
    @Json(name = "image_size_limit") val imageSizeLimit: Long = DEFAULT_IMAGE_SIZE_LIMIT,
    @Json(name = "image_matrix_limit") val imageMatrixLimit: Int = DEFAULT_IMAGE_MATRIX_LIMIT,
    @Json(name = "video_size_limit") val videoSizeLimit: Long = DEFAULT_VIDEO_SIZE_LIMIT,
    @Json(name = "video_frame_rate_limit") val videoFrameRateLimit: Int? = DEFAULT_VIDEO_FRAME_RATE_LIMIT,
    @Json(name = "video_matrix_limit") val videoMatrixLimit: Int? = DEFAULT_VIDEO_MATRIX_LIMIX,
)

@JsonClass(generateAdapter = true)
data class PleromaConfiguration(
    val metadata: PleromaMetadata?,
)

@JsonClass(generateAdapter = true)
data class PleromaMetadata(
    @Json(name = "fields_limits") val fieldLimits: PleromaFieldLimits,
)

@JsonClass(generateAdapter = true)
data class PleromaFieldLimits(
    @Json(name = "max_fields") val maxFields: Int = DEFAULT_MAX_ACCOUNT_FIELDS,
    @Json(name = "name_length") val nameLength: Int?,
    @Json(name = "value_length") val valueLength: Int?,
)

@JsonClass(generateAdapter = true)
data class InstanceRules(
    val id: String,
    val text: String,
)
