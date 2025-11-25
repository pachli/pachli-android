/* Copyright 2019 Tusky Contributors
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
import java.util.Date

@JsonClass(generateAdapter = true)
data class NewStatus(
    val status: String,
    @Json(name = "spoiler_text") val warningText: String,
    @Json(name = "in_reply_to_id") val inReplyToId: String?,
    val visibility: String,
    val sensitive: Boolean,
    @Json(name = "media_ids") val mediaIds: List<String>?,
    @Json(name = "media_attributes") val mediaAttributes: List<MediaAttribute>?,
    @Json(name = "scheduled_at") val scheduledAt: Date?,
    val poll: NewPoll?,
    val language: String?,
    @Json(name = "quoted_status_id") val quotedStatusId: String?,
    @Json(name = "quote_approval_policy") val quotePolicy: String?,
)

fun app.pachli.core.model.NewStatus.asNetworkModel() = NewStatus(
    status = status,
    warningText = warningText,
    inReplyToId = inReplyToId,
    visibility = visibility,
    sensitive = sensitive,
    mediaIds = mediaIds,
    mediaAttributes = mediaAttributes?.asNetworkModel(),
    scheduledAt = scheduledAt,
    poll = poll?.asNetworkModel(),
    language = language,
    quotedStatusId = quotedStatusId,
    quotePolicy = quotePolicy?.asNetworkModel()?.asFormValue(),
)

@JsonClass(generateAdapter = true)
data class NewPoll(
    val options: List<String>,
    @Json(name = "expires_in") val expiresIn: Int,
    val multiple: Boolean,
) {
    fun asModel() = app.pachli.core.model.NewPoll(
        options = options,
        expiresIn = expiresIn,
        multiple = multiple,
    )
}

fun app.pachli.core.model.NewPoll.asNetworkModel() = NewPoll(
    options = options,
    expiresIn = expiresIn,
    multiple = multiple,
)

// It would be nice if we could reuse MediaToSend,
// but the server requires a different format for focus
@JsonClass(generateAdapter = true)
data class MediaAttribute(
    val id: String,
    val description: String?,
    val focus: String?,
    val thumbnail: String?,
)

fun app.pachli.core.model.MediaAttribute.asNetworkModel() = MediaAttribute(
    id = id,
    description = description,
    focus = focus,
    thumbnail = thumbnail,
)

fun Iterable<app.pachli.core.model.MediaAttribute>.asNetworkModel() = map { it.asNetworkModel() }
