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
 * A draft status.
 *
 * The status may or may not be saved to local storage, depending on [id].
 *
 * @property id Unique identifier for the draft. `0` means the draft has not
 * yet been saved. Anything else means the draft has been saved at least once
 * (but the current content of the class may be dirty).
 * @property contentWarning The content warning (spoiler text).
 * @property content The main text body.
 * @property sensitive Whether the media in the draft should be marked as sensitive.
 * @property visibility The visibility level of the draft.
 * @property attachments Any attachments to the draft.
 * @property poll If non-null, the poll to attach to the draft.
 * @property failureMessage If non-null, the most recent user-facing error
 * message explaining why this status couldn't be sent. If null then there
 * have been no unsuccessful attempts to send this status.
 * @property scheduledAt If non-null, the intended scheduled time for this draft.
 * @property language The language the draft is written in. If null the user's
 * default language is used.
 * @property quotePolicy The draft's [AccountSource.QuotePolicy]. If null the user's
 * default quote policy is used.
 * @property inReplyToId If non-null this draft is a reply and this is the ID of the
 * status being replied to.
 * @property quotedStatusId If non-null this draft quotes another status and this is
 * the ID of the status being quoted.
 * @property statusId If non-null this draft represents an edit to an existing status,
 * this is the ID of the status being edited.
 * @property cursorPosition The position of the cursor, restored when the user starts
 * editing the draft.
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
