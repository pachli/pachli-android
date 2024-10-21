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

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date
import kotlinx.parcelize.Parcelize

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
)

@Parcelize
@JsonClass(generateAdapter = true)
data class NewPoll(
    val options: List<String>,
    @Json(name = "expires_in") val expiresIn: Int,
    val multiple: Boolean,
) : Parcelable

// It would be nice if we could reuse MediaToSend,
// but the server requires a different format for focus
@Parcelize
@JsonClass(generateAdapter = true)
data class MediaAttribute(
    val id: String,
    val description: String?,
    val focus: String?,
    val thumbnail: String?,
) : Parcelable
