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

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import app.pachli.core.database.Converters
import app.pachli.core.model.AccountSource
import app.pachli.core.model.DraftAttachment
import app.pachli.core.model.NewPoll
import app.pachli.core.model.Status
import java.util.Date

/**
 * @property quotePolicy The quote policy the user set while editing the draft.
 * @property quotedStatusId If non-null, the ID of the status the user was
 * quoting while editing the draft.
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("accountId"),
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index(value = ["accountId"])],
)
@TypeConverters(Converters::class)
data class DraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val accountId: Long,
    val inReplyToId: String?,
    val content: String?,
    val contentWarning: String?,
    val sensitive: Boolean,
    val visibility: Status.Visibility,
    val attachments: List<DraftAttachment>,
    val poll: NewPoll?,
    val failedToSend: Boolean,
    val failedToSendNew: Boolean,
    val scheduledAt: Date?,
    val language: String?,
    val statusId: String?,
    val quotePolicy: AccountSource.QuotePolicy?,
    val quotedStatusId: String?,
)
