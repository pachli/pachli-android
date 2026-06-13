/*
 * Copyright (c) 2026 Pachli Association
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
import androidx.room.TypeConverters
import app.pachli.core.database.Converters
import app.pachli.core.model.Hashtag
import app.pachli.core.model.HashtagHistory

/**
 * Represents a [Hashtag].
 *
 * @property name Hashtag name, without the leading '#'.
 */
@TypeConverters(Converters::class)
@Entity(
    primaryKeys = ["pachliAccountId", "name"],
    foreignKeys = [
        ForeignKey(
            entity = PachliAccountEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("pachliAccountId"),
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
)
data class HashtagEntity(
    val pachliAccountId: Long,
    val name: String,
    val url: String,
    val history: List<HashtagHistory>,
    val following: Boolean,
) {
    fun asModel() = Hashtag(
        name = name,
        url = url,
        history = history,
        following = following,
    )
}

@JvmName("iterableHashtagEntityAsModel")
fun Iterable<HashtagEntity>.asModel() = map { it.asModel() }

fun Hashtag.asEntity(pachliAccountId: Long) = HashtagEntity(
    pachliAccountId = pachliAccountId,
    name = name,
    url = url,
    history = history,
    following = following == true,
)

fun Iterable<Hashtag>.asEntity(pachliAccountId: Long) = map { it.asEntity(pachliAccountId) }
