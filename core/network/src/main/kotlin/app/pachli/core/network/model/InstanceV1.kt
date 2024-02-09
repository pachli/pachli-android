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
    @Json(name = "max_toot_chars") val maxTootChars: Int?,
    @Json(name = "poll_limits") val pollConfiguration: PollConfiguration?,
    val configuration: InstanceConfiguration?,
    @Json(name = "max_media_attachments") val maxMediaAttachments: Int?,
    val pleroma: PleromaConfiguration?,
    @Json(name = "upload_limit") val uploadLimit: Int?,
    val rules: List<InstanceRules>?,
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
    @Json(name = "max_options") val maxOptions: Int?,
    @Json(name = "max_option_chars") val maxOptionChars: Int?,
    @Json(name = "max_characters_per_option") val maxCharactersPerOption: Int?,
    @Json(name = "min_expiration") val minExpiration: Int?,
    @Json(name = "max_expiration") val maxExpiration: Int?,
)

@JsonClass(generateAdapter = true)
data class InstanceConfiguration(
    val statuses: StatusConfiguration?,
    @Json(name = "media_attachments") val mediaAttachments: MediaAttachmentConfiguration?,
    val polls: PollConfiguration?,
)

@JsonClass(generateAdapter = true)
data class StatusConfiguration(
    @Json(name = "max_characters") val maxCharacters: Int?,
    @Json(name = "max_media_attachments") val maxMediaAttachments: Int?,
    @Json(name = "characters_reserved_per_url") val charactersReservedPerUrl: Int?,
)

@JsonClass(generateAdapter = true)
data class MediaAttachmentConfiguration(
    @Json(name = "supported_mime_types") val supportedMimeTypes: List<String>?,
    @Json(name = "image_size_limit") val imageSizeLimit: Int?,
    @Json(name = "image_matrix_limit") val imageMatrixLimit: Int?,
    @Json(name = "video_size_limit") val videoSizeLimit: Int?,
    @Json(name = "video_frame_rate_limit") val videoFrameRateLimit: Int?,
    @Json(name = "video_matrix_limit") val videoMatrixLimit: Int?,
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
    @Json(name = "max_fields") val maxFields: Int?,
    @Json(name = "name_length") val nameLength: Int?,
    @Json(name = "value_length") val valueLength: Int?,
)

@JsonClass(generateAdapter = true)
data class InstanceRules(
    val id: String,
    val text: String,
)
