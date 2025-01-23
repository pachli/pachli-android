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

package app.pachli.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.TypeConverters
import app.pachli.core.database.Converters
import app.pachli.core.database.model.LogEntryEntity
import java.time.Instant

/**
 * Read and write [LogEntryEntity].
 */
@Dao
interface LogEntryDao {
    @Insert
    suspend fun insert(logEntry: LogEntryEntity): Long

    /** Load all [LogEntryEntity], ordered oldest first */
    @Query(
        """
SELECT *
  FROM LogEntryEntity
 ORDER BY id ASC
 """,
    )
    suspend fun loadAll(): List<LogEntryEntity>

    /** Delete all [LogEntryEntity] older than [cutoff] */
    @TypeConverters(Converters::class)
    @Query(
        """
DELETE
  FROM LogEntryEntity
 WHERE instant < :cutoff
 """,
    )
    suspend fun prune(cutoff: Instant)
}
