/*
 * Copyright 2023 Pachli Association
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

package app.pachli.network.model

import app.pachli.entity.Account
import com.google.gson.annotations.SerializedName

/** https://docs.joinmastodon.org/entities/Instance/ */
data class InstanceV2(
    /** The domain name of the instance */
    val domain: String,

    /** The title of the website */
    val title: String,

    /** The version of Mastodon installed on the instance */
    val version: String,

    /**
     * The URL for the source code of the software running on this instance, in keeping with AGPL
     * license requirements.
     */
    @SerializedName("source_url") val sourceUrl: String,

    /** A short, plain-text description defined by the admin. */
    val description: String,

    val usage: Usage,

    val thumbnail: Thumbnail,

    val languages: List<String>,

    val configuration: Configuration,

    val registrations: Registrations,

    val contact: Contact,

    val rules: List<Rule>
)

data class Usage(
    val users: Users,
)

data class Users(
    val activeMonth: Int = 0
)

data class Thumbnail(
    val url: String,
    val blurhash: String?,
    val versions: ThumbnailVersions?,
)

data class ThumbnailVersions(
    @SerializedName("@1x") val oneX: String?,
    @SerializedName("@2x") val twoX: String?,
)

data class Configuration(
    val urls: InstanceV2Urls,
    val accounts: InstanceV2Accounts,
    val statuses: InstanceV2Statuses,
    @SerializedName("media_attachments") val mediaAttachments: MediaAttachments,
    val polls: InstanceV2Polls,
    val translation: InstanceV2Translation,
)

data class InstanceV2Urls(
    @SerializedName("streaming_api") val streamingApi: String
)

data class InstanceV2Accounts(
    @SerializedName("max_featured_tags") val maxFeaturedTags: Int,
)

data class InstanceV2Statuses(
    @SerializedName("max_characters") val maxCharacters: Int,
    @SerializedName("max_media_attachments") val maxMediaAttachments: Int,
    @SerializedName("characters_reserved_per_url") val charactersReservedPerUrl: Int,
)

data class MediaAttachments(
    @SerializedName("supported_mime_types") val supportedMimeTypes: List<String>,
    @SerializedName("image_size_limit") val imageSizeLimit: Int,
    @SerializedName("image_matrix_limit") val imageMatrixLimit: Int,
    @SerializedName("video_size_limit") val videoSizeLimit: Int,
    @SerializedName("video_frame_rate_limit") val videoFrameRateLimit: Int,
    @SerializedName("video_matrix_limit") val videoMatrixLimit: Int,
)

data class InstanceV2Polls(
    @SerializedName("max_options") val maxOptions: Int,
    @SerializedName("max_characters_per_option") val maxCharactersPerOption: Int,
    @SerializedName("min_expiration") val minExpiration: Int,
    @SerializedName("max_expiration") val maxExpiration: Int,
)

data class InstanceV2Translation(
    val enabled: Boolean
)

data class Registrations(
    val enabled: Boolean,
    @SerializedName("approval_required") val approvalRequired: Boolean,
    val message: String?,
)

data class Contact(
    val email: String,
    val account: Account,
)

data class Rule(
    val id: String,
    val text: String,
)
