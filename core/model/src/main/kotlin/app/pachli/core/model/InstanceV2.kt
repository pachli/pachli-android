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

package app.pachli.core.model

import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_CHARACTERS_RESERVED_PER_URL
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_CHARACTER_LIMIT
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_IMAGE_MATRIX_LIMIT
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_IMAGE_SIZE_LIMIT
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_MAX_MEDIA_ATTACHMENTS
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_MAX_MEDIA_DESCRIPTION_CHARS
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_MAX_OPTION_COUNT
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_MAX_OPTION_LENGTH
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_MAX_POLL_DURATION
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_MIN_POLL_DURATION
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_VIDEO_FRAME_RATE_LIMIT
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_VIDEO_MATRIX_LIMIX
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_VIDEO_SIZE_LIMIT

/** https://docs.joinmastodon.org/entities/Instance/ */
data class InstanceV2(
    /** The domain name of the instance */
    val domain: String,

    /** The title of the website */
    val title: String,

    /** The version of Mastodon installed on the instance */
    val version: String,

    // Missing in some Friendica servers, https://github.com/friendica/friendica/issues/13941
    /**
     * The URL for the source code of the software running on this instance, in keeping with AGPL
     * license requirements.
     */
    val sourceUrl: String? = null,

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
    val rules: List<Rule> = emptyList(),
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
    val oneX: String?,

    /** The URL for the thumbnail image at 2x resolution. */
    val twoX: String?,
)

data class Configuration(
    // May be missing (e.g., Friendica), so provide a default
    /** URLs of interest for clients apps. */
    val urls: InstanceV2Urls = InstanceV2Urls(),

    // Missing in some Pleroma servers, https://git.pleroma.social/pleroma/pleroma/-/issues/3251
    /** Limits related to accounts. */
    val accounts: InstanceV2Accounts = InstanceV2Accounts(),

    /** Limits related to authoring statuses. */
    val statuses: InstanceV2Statuses,

    /** Hints for which attachments will be accepted. */
    val mediaAttachments: MediaAttachments,

    /** Limits related to polls. */
    val polls: InstanceV2Polls,

    // May be missing, so provide a default
    /** Hints related to translation. */
    val translation: InstanceV2Translation = InstanceV2Translation(enabled = false),
)

data class InstanceV2Urls(
    /**
     * The Websockets URL for connecting to the streaming API. This is the
     * documented property name
     */
    val streamingApi: String? = null,

    /**
     * The Websockets URL for connecting to the streaming API. This is the
     * undocumented property name, see https://github.com/mastodon/mastodon/pull/29124
     */
    val streaming: String? = null,
)

data class InstanceV2Accounts(
    /** The maximum number of featured tags allowed for each account. */
    val maxFeaturedTags: Int = 0,
)

data class InstanceV2Statuses(
    /** The maximum number of allowed characters per status. */
    val maxCharacters: Int = DEFAULT_CHARACTER_LIMIT,

    // Missing in some Friendica servers until https://github.com/friendica/friendica/pull/13664
    /** The maximum number of media attachments that can be added to a status. */
    val maxMediaAttachments: Int = DEFAULT_MAX_MEDIA_ATTACHMENTS,

    // Missing in some Pleroma servers, https://git.pleroma.social/pleroma/pleroma/-/issues/3250
    /** Each URL in a status will be assumed to be exactly this many characters. */
    val charactersReservedPerUrl: Int = DEFAULT_CHARACTERS_RESERVED_PER_URL,
)

data class MediaAttachments(
    /** Contains MIME types that can be uploaded. */
    val supportedMimeTypes: List<String> = emptyList(),

    /** The maximum size of any uploaded image, in bytes. */
    val imageSizeLimit: Long = DEFAULT_IMAGE_SIZE_LIMIT,

    /** The maximum number of pixels (width x height) for image uploads. */
    val imageMatrixLimit: Int = DEFAULT_IMAGE_MATRIX_LIMIT,

    /** The maximum size of any uploaded video, in bytes. */
    val videoSizeLimit: Long = DEFAULT_VIDEO_SIZE_LIMIT,

    /** The maximum frame rate for any uploaded video. */
    val videoFrameRateLimit: Int = DEFAULT_VIDEO_FRAME_RATE_LIMIT,

    /** The maximum number of pixels (width times height) for video uploads. */
    val videoMatrixLimit: Int = DEFAULT_VIDEO_MATRIX_LIMIX,

    /** Maximum number of characters in a media description. */
    val descriptionLimit: Int = DEFAULT_MAX_MEDIA_DESCRIPTION_CHARS,
)

data class InstanceV2Polls(
    // Some Pleroma servers omit this
    /** Each poll is allowed to have up to this many options. */
    val maxOptions: Int = DEFAULT_MAX_OPTION_COUNT,

    /** Each poll option is allowed to have this many characters. */
    val maxCharactersPerOption: Int = DEFAULT_MAX_OPTION_LENGTH,

    /** The shortest allowed poll duration, in seconds. */
    val minExpiration: Int = DEFAULT_MIN_POLL_DURATION,

    /** The longest allowed poll duration, in seconds. */
    val maxExpiration: Long = DEFAULT_MAX_POLL_DURATION,
)

data class InstanceV2Translation(
    /** Whether the Translations API is available on this instance. */
    val enabled: Boolean,
)

data class Registrations(
    /** Whether registrations are enabled. */
    val enabled: Boolean,

    /** Whether registrations require moderator approval. */
    val approvalRequired: Boolean,

    /** A custom message to be shown when registrations are closed. */
    val message: String?,
)

data class Contact(
    // Pixelfed can return null, see https://github.com/pixelfed/pixelfed/issues/4957
    /** An email address that can be messaged regarding inquiries or issues. */
    val email: String? = null,

    // Mastodon can return null, see https://github.com/mastodon/mastodon/issues/29418
    /** An account that can be contacted natively over the network regarding inquiries or issues. */
    val account: Account? = null,
)

/** https://docs.joinmastodon.org/entities/Rule/ */

data class Rule(
    /** An identifier for the rule. */
    val id: String,

    /** The rule to be followed. */
    val text: String,
)
