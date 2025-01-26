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

package app.pachli.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.TypeConverters
import androidx.room.Upsert
import app.pachli.core.database.Converters
import app.pachli.core.database.model.ContentFiltersEntity
import kotlinx.coroutines.flow.Flow

@Dao
@TypeConverters(Converters::class)
interface ContentFiltersDao {
    @Query(
        """
SELECT *
FROM ContentFiltersEntity
WHERE accountId = :pachliAccountId
""",
    )
    suspend fun getByAccount(pachliAccountId: Long): ContentFiltersEntity?

    @Query(
        """
SELECT *
FROM ContentFiltersEntity
WHERE accountId = :pachliAccountId
""",
    )
    fun flowByAccount(pachliAccountId: Long): Flow<ContentFiltersEntity?>

    @Upsert
    suspend fun upsert(contentFiltersEntity: ContentFiltersEntity)
}
