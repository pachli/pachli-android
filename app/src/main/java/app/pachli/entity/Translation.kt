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

package app.pachli.entity

import com.google.gson.annotations.SerializedName

/** https://docs.joinmastodon.org/entities/Translation/ */
data class Translation(
    /** The translated text of the status (HTML), equivalent to [Status.content] */
    val content: String,

    /**
     * The language of the source text, as auto-detected by the machine translation
     * (ISO 639 language code)
     */
    @SerializedName("detected_source_language") val detectedSourceLanguage: String,

    /**
     * The translated spoiler text of the status (text), if it exists, equivalent to
     * [Status.spoilerText]
     */
    // Not documented, see https://github.com/mastodon/documentation/issues/1248
    @SerializedName("spoiler_text") val spoilerText: String,

    /**
     * The translated poll (if it exists). Does not contain all the poll data, only the
     * translated text. Vote counts and other metadata has to be determined from the original
     * poll object.
     */
    // Not documented, see https://github.com/mastodon/documentation/issues/1248
    val poll: TranslatedPoll?,

    /**
     * Translated descriptions for media attachments, if any were attached. Other metadata has
     * to be determined from the original attachment.
     */
    // Not documented, see https://github.com/mastodon/documentation/issues/1248
    @SerializedName("media_attachments") val attachments: List<TranslatedAttachment>,

    /** The service that provided the machine translation */
    val provider: String,
)

data class TranslatedPoll(
    val id: String,
    val options: List<TranslatedPollOption>,
)

data class TranslatedPollOption(
    val title: String,
)

data class TranslatedAttachment(
    val id: String,
    val description: String,
)
