/*
 * Copyright 2025 Pachli Association
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

import android.net.Uri
import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date
import kotlinx.parcelize.Parcelize

@Parcelize
data class Draft(
    val id: Long = 0,
    val contentWarning: String?,
    val content: String?,
    val sensitive: Boolean,
    val visibility: Status.Visibility,
    val attachments: List<DraftAttachment> = emptyList(),
    val poll: NewPoll? = null,
    val failedToSend: Boolean = false,
    val failedToSendNew: Boolean = false,
    val scheduledAt: Date? = null,
    val language: String?,
    val quotePolicy: AccountSource.QuotePolicy?,
    val inReplyToId: String? = null,
    val quotedStatusId: String? = null,
    val statusId: String? = null,
) : Parcelable {
    companion object
}

@Parcelize
@JsonClass(generateAdapter = true)
data class DraftAttachment(
    @Json(name = "uriString") val uri: Uri,
    val description: String?,
    val focus: Attachment.Focus?,
    val type: Attachment.Type,
) : Parcelable
