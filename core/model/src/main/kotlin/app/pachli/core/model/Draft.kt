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

/**
 * Represents a status draft that.
 *
 * @property id
 * @property contentWarning
 * @property content
 * @property sensitive
 * @property visibility
 * @property attachments
 * @property poll
 * @property failureMessage
 * @property scheduledAt
 * @property language
 * @property quotePolicy
 * @property inReplyToId
 * @property quotedStatusId
 * @property statusId
 * @property cursorPosition
 * @property state See [Draft.State]
 */
@Parcelize
data class Draft(
    val id: Long = 0,
    val contentWarning: String?,
    val content: String?,
    val sensitive: Boolean,
    val visibility: Status.Visibility,
    val attachments: List<DraftAttachment>,
    val poll: NewPoll?,
    val failureMessage: String?,
    val scheduledAt: Date?,
    val language: String?,
    val quotePolicy: AccountSource.QuotePolicy?,
    val inReplyToId: String?,
    val quotedStatusId: String?,
    val statusId: String?,
    val cursorPosition: Int,
    val state: State,
) : Parcelable {
    /** Draft's state. */
    enum class State {
        /** Draft is available for editing or sending. */
        DEFAULT,

        /**
         * User is actively editing this draft. Simultaneous attempts to
         * edit the same draft should be prevented.
         */
        EDITING,

        /**
         * Draft is being sent to the server. Editing should be prevented,
         * as should simultaneous attempts to send the same draft.
         */
        SENDING,
    }

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
