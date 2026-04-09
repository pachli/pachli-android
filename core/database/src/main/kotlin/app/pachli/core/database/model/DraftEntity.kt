/*
 * Copyright 2020 Tusky Contributors
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

package app.pachli.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import app.pachli.core.database.Converters
import app.pachli.core.model.AccountSource
import app.pachli.core.model.Draft
import app.pachli.core.model.DraftAttachment
import app.pachli.core.model.NewPoll
import app.pachli.core.model.Status
import java.util.Date

/**
 * @property id Unique identifier for this draft (across all drafts, this is
 * **not** part of a composite key with [pachliAccountId]). See
 * [Draft.id].
 * @property pachliAccountId Account that owns this draft.
 * @property contentWarning [Draft.contentWarning].
 * @property content [Draft.content].
 * @property sensitive [Draft.sensitive].
 * @property visibility [Draft.visibility].
 * @property attachments [Draft.attachments].
 * @property poll [Draft.poll].
 * @property failureMessage [Draft.failureMessage].
 * @property scheduledAt [Draft.scheduledAt].
 * @property language [Draft.language].
 * @property quotePolicy [Draft.quotePolicy].
 * @property inReplyToId [Draft.inReplyToId].
 * @property quotedStatusId [Draft.quotedStatusId].
 * @property statusId [Draft.statusId].
 * @property cursorPosition [Draft.cursorPosition].
 * @property state [Draft.state].
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("pachliAccountId"),
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index(value = ["pachliAccountId"])],
)
@TypeConverters(Converters::class)
data class DraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pachliAccountId: Long,
    val inReplyToId: String?,
    val content: String?,
    val contentWarning: String?,
    val sensitive: Boolean,
    val visibility: Status.Visibility,
    val attachments: List<DraftAttachment>,
    val poll: NewPoll?,
    val failureMessage: String?,
    val scheduledAt: Date?,
    val language: String?,
    val statusId: String?,
    val quotePolicy: AccountSource.QuotePolicy?,
    val quotedStatusId: String?,
    @ColumnInfo(defaultValue = "0")
    val cursorPosition: Int = 0,
    @ColumnInfo(defaultValue = "DEFAULT")
    val state: Draft.State,
)

fun DraftEntity.asModel(): Draft {
    return Draft(
        id = id,
        contentWarning = contentWarning,
        content = content,
        sensitive = sensitive,
        visibility = visibility,
        attachments = attachments,
        poll = poll,
        failureMessage = failureMessage,
        scheduledAt = scheduledAt,
        language = language,
        statusId = statusId,
        quotePolicy = quotePolicy,
        inReplyToId = inReplyToId,
        quotedStatusId = quotedStatusId,
        cursorPosition = cursorPosition,
        state = state,
    )
}
