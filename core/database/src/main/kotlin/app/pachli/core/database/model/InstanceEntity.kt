/*
 * Copyright 2018 Conny Duck
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
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import app.pachli.core.database.Converters
import app.pachli.core.network.model.Emoji

// data class InstanceInfo(
//    @Embedded val instanceInfo: InstanceInfoEntity,
// //    @Relation(
// //        parentColumn = "instance",
// //        entityColumn = "instance",
// //    )
//    val emojis: EmojisEntity,
// )

@Entity
@TypeConverters(Converters::class)
data class InstanceInfoEntity(
    @PrimaryKey val instance: String,
    val maxPostCharacters: Int?,
    val maxPollOptions: Int?,
    val maxPollOptionLength: Int?,
    val minPollDuration: Int?,
    val maxPollDuration: Int?,
    val charactersReservedPerUrl: Int?,
    val version: String?,
    val videoSizeLimit: Long?,
    val imageSizeLimit: Long?,
    val imageMatrixLimit: Int?,
    val maxMediaAttachments: Int?,
    val maxFields: Int?,
    val maxFieldNameLength: Int?,
    val maxFieldValueLength: Int?,
)

@Entity(
    primaryKeys = ["accountId"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("accountId"),
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
@TypeConverters(Converters::class)
data class EmojisEntity(
    val accountId: Long,
    val emojiList: List<Emoji>,
)

// // Version of InstanceEntity without emojiList for partial updates and fetches
// // (obsolete)
// data class InstanceInfoEntity(
//    @PrimaryKey val instance: String,
//    val maximumTootCharacters: Int,
//    val maxPollOptions: Int,
//    val maxPollOptionLength: Int,
//    val minPollDuration: Int,
//    val maxPollDuration: Int,
//    val charactersReservedPerUrl: Int,
//    val version: String,
//    val videoSizeLimit: Long,
//    val imageSizeLimit: Long,
//    val imageMatrixLimit: Int,
//    val maxMediaAttachments: Int,
//    val maxFields: Int?,
//    val maxFieldNameLength: Int?,
//    val maxFieldValueLength: Int?,
// )
