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

    /** Usage data for this instance. */
    val usage: Usage,

    /** An image used to represent this instance. */
    val thumbnail: Thumbnail,

    /** Primary languages of the website and its staff (ISD 639-1 2 letter language codes) */
    val languages: List<String>,

    /** Configured values and limits for this website. */
    val configuration: Configuration,

    /** Information about registering for this website. */
    val registrations: Registrations,

    /** Hints related to contacting a representative of the website. */
    val contact: Contact,

    /** An itemized list of rules for this website. */
    val rules: List<Rule>,
)

data class Usage(
    /** Usage data related to users on this instance. */
    val users: Users,
)

data class Users(
    /** The number of active users in the past 4 weeks. */
    val activeMonth: Int = 0,
)

data class Thumbnail(
    /** The URL for the thumbnail image. */
    val url: String,

    /**
     * A hash computed by the BlurHash algorithm, for generating colorful preview thumbnails
     * when media has not been downloaded yet.
     */
    val blurhash: String?,

    /** Links to scaled resolution images, for high DPI screens. */
    val versions: ThumbnailVersions?,
)

data class ThumbnailVersions(
    /** The URL for the thumbnail image at 1x resolution. */
    @SerializedName("@1x") val oneX: String?,

    /** The URL for the thumbnail image at 2x resolution. */
    @SerializedName("@2x") val twoX: String?,
)

data class Configuration(
    /** URLs of interest for clients apps. */
    val urls: InstanceV2Urls,

    /** Limits related to accounts. */
    val accounts: InstanceV2Accounts,

    /** Limits related to authoring statuses. */
    val statuses: InstanceV2Statuses,

    /** Hints for which attachments will be accepted. */
    @SerializedName("media_attachments") val mediaAttachments: MediaAttachments,

    /** Limits related to polls. */
    val polls: InstanceV2Polls,

    /** Hints related to translation. */
    val translation: InstanceV2Translation,
)

data class InstanceV2Urls(
    /** The Websockets URL for connecting to the streaming API. */
    @SerializedName("streaming_api") val streamingApi: String,
)

data class InstanceV2Accounts(
    /** The maximum number of featured tags allowed for each account. */
    @SerializedName("max_featured_tags") val maxFeaturedTags: Int,
)

data class InstanceV2Statuses(
    /** The maximum number of allowed characters per status. */
    @SerializedName("max_characters") val maxCharacters: Int,

    /** The maximum number of media attachments that can be added to a status. */
    @SerializedName("max_media_attachments") val maxMediaAttachments: Int,

    /** Each URL in a status will be assumed to be exactly this many characters. */
    @SerializedName("characters_reserved_per_url") val charactersReservedPerUrl: Int,
)

data class MediaAttachments(
    /** Contains MIME types that can be uploaded. */
    @SerializedName("supported_mime_types") val supportedMimeTypes: List<String>,

    /** The maximum size of any uploaded image, in bytes. */
    @SerializedName("image_size_limit") val imageSizeLimit: Int,

    /** The maximum number of pixels (width times height) for image uploads. */
    @SerializedName("image_matrix_limit") val imageMatrixLimit: Int,

    /** The maximum size of any uploaded video, in bytes. */
    @SerializedName("video_size_limit") val videoSizeLimit: Int,

    /** The maximum frame rate for any uploaded video. */
    @SerializedName("video_frame_rate_limit") val videoFrameRateLimit: Int,

    /** The maximum number of pixels (width times height) for video uploads. */
    @SerializedName("video_matrix_limit") val videoMatrixLimit: Int,
)

data class InstanceV2Polls(
    /** Each poll is allowed to have up to this many options. */
    @SerializedName("max_options") val maxOptions: Int,

    /** Each poll option is allowed to have this many characters. */
    @SerializedName("max_characters_per_option") val maxCharactersPerOption: Int,

    /** The shortest allowed poll duration, in seconds. */
    @SerializedName("min_expiration") val minExpiration: Int,

    /** The longest allowed poll duration, in seconds. */
    @SerializedName("max_expiration") val maxExpiration: Int,
)

data class InstanceV2Translation(
    /** Whether the Translations API is available on this instance. */
    val enabled: Boolean,
)

data class Registrations(
    /** Whether registrations are enabled. */
    val enabled: Boolean,

    /** Whether registrations require moderator approval. */
    @SerializedName("approval_required") val approvalRequired: Boolean,

    /** A custom message to be shown when registrations are closed. */
    val message: String?,
)

data class Contact(
    /** An email address that can be messaged regarding inquiries or issues. */
    val email: String,

    /** An account that can be contacted natively over the network regarding inquiries or issues. */
    val account: Account,
)

/** https://docs.joinmastodon.org/entities/Rule/ */
data class Rule(
    /** An identifier for the rule. */
    val id: String,

    /** The rule to be followed. */
    val text: String,
)
