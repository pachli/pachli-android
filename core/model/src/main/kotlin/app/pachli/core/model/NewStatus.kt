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

package app.pachli.core.model

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date
import kotlinx.parcelize.Parcelize

data class NewStatus(
    val status: String,
    val warningText: String,
    val inReplyToId: String?,
    val visibility: String,
    val sensitive: Boolean,
    val mediaIds: List<String>?,
    val mediaAttributes: List<MediaAttribute>?,
    val scheduledAt: Date?,
    val poll: NewPoll?,
    val language: String?,
    val quotedStatusId: String?,
    val quotePolicy: AccountSource.QuotePolicy?,
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
data class MediaAttribute(
    val id: String,
    val description: String?,
    val focus: String?,
    val thumbnail: String?,
) : Parcelable
