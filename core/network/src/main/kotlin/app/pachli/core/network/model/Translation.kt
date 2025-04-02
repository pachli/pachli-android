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

package app.pachli.core.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** https://docs.joinmastodon.org/entities/Translation/ */
@JsonClass(generateAdapter = true)
data class Translation(
    /** The translated text of the status (HTML), equivalent to [Status.content] */
    val content: String,

    /**
     * The language of the source text, as auto-detected by the machine translation
     * (ISO 639 language code)
     */
    @Json(name = "detected_source_language") val detectedSourceLanguage: String,

    // Not documented, see https://github.com/mastodon/documentation/issues/1248
    /**
     * The translated spoiler text of the status (text), if it exists, equivalent to
     * [Status.spoilerText]
     */
    @Json(name = "spoiler_text") val spoilerText: String,

    // Not documented, see https://github.com/mastodon/documentation/issues/1248
    /** The translated poll (if it exists) */
    val poll: TranslatedPoll?,

    // Not documented, see https://github.com/mastodon/documentation/issues/1248
    /**
     * Translated descriptions for media attachments, if any were attached. Other metadata has
     * to be determined from the original attachment.
     */
    @Json(name = "media_attachments") val attachments: List<TranslatedAttachment>,

    /** The service that provided the machine translation */
    val provider: String,
) {
    fun toModel() = app.pachli.core.model.translation.TranslatedStatus(
        content = content,
        detectedSourceLanguage = detectedSourceLanguage,
        spoilerText = spoilerText,
        poll = this.poll?.toModel(),
        attachments = attachments.map { it.toModel() },
        provider = this.provider,
    )
}

/**
 * A translated poll. Does not contain all the poll data, only the translated text.
 * Vote counts and other metadata has to be determined from the original poll object.
 */
@JsonClass(generateAdapter = true)
data class TranslatedPoll(
    val id: String,
    val options: List<TranslatedPollOption>,
) {
    fun toModel() = app.pachli.core.model.translation.TranslatedPoll(
        id = this.id,
        options = this.options.map { it.toModel() },
    )
}

/** A translated poll option. */
@JsonClass(generateAdapter = true)
data class TranslatedPollOption(
    val title: String,
) {
    fun toModel() = app.pachli.core.model.translation.TranslatedPollOption(title)
}

/** A translated attachment. Only the description is translated */
@JsonClass(generateAdapter = true)
data class TranslatedAttachment(
    val id: String,
    val description: String?,
) {
    fun toModel() = app.pachli.core.model.translation.TranslatedAttachment(
        id = id,
        description = description,
    )
}

fun app.pachli.core.model.translation.TranslatedPoll.toNetworkModel() = TranslatedPoll(
    id = this.id,
    options = this.options.map { it.toNetworkModel() },
)

fun app.pachli.core.model.translation.TranslatedPollOption.toNetworkModel() = TranslatedPollOption(this.title)

fun app.pachli.core.model.translation.TranslatedAttachment.toNetworkModel() = TranslatedAttachment(
    id = this.id,
    description = this.description,
)
