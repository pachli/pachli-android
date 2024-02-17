/*
 * Copyright 2024 Pachli Association
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

import android.util.Log
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import app.pachli.core.database.Converters
import java.time.Instant

/** Map log priority values to characters to use when displaying the log */
private val priorityToChar = mapOf(
    Log.VERBOSE to 'V',
    Log.DEBUG to 'D',
    Log.INFO to 'I',
    Log.WARN to 'W',
    Log.ERROR to 'E',
    Log.ASSERT to 'A',
)

/** An entry in the log. See [Log] for details. */
interface LogEntry {
    val instant: Instant
    val priority: Int?
    val tag: String?
    val message: String
    val t: Throwable?

    fun LogEntry.toString() = "%s %c/%s: %s%s".format(
        instant.toString(),
        priorityToChar[priority] ?: '?',
        tag,
        message,
        t?.let { t -> " $t" } ?: "",
    )
}

/**
 * @see [LogEntry]
 */
@Entity
@TypeConverters(Converters::class)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    override val instant: Instant,
    override val priority: Int? = null,
    override val tag: String? = null,
    override val message: String,
    override val t: Throwable? = null,
) : LogEntry
