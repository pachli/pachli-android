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

package app.pachli.core.database.dao

import androidx.room3.ColumnTypeConverters
import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import app.pachli.core.database.Converters
import app.pachli.core.database.model.HashtagEntity
import kotlinx.coroutines.flow.Flow

@Dao
@ColumnTypeConverters(Converters::class)
interface HashtagsDao {
    @Query(
        """
DELETE
FROM HashtagEntity
WHERE pachliAccountId = :pachliAccountId and `following` = 1
""",
    )
    suspend fun deleteFollowedHashtagsForAccount(pachliAccountId: Long)

    @Query(
        """
SELECT *
FROM HashtagEntity
WHERE pachliAccountId = :pachliAccountId AND `following` = 1
""",
    )
    fun followingFlowByAccount(pachliAccountId: Long): Flow<List<HashtagEntity>>

    @Query(
        """
SELECT *
FROM HashtagEntity
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun get(pachliAccountId: Long): List<HashtagEntity>

    @Upsert
    suspend fun upsert(hashtag: HashtagEntity)

    @Upsert
    suspend fun upsert(hashtags: List<HashtagEntity>)

    @Query(
        """
DELETE
FROM HashtagEntity
WHERE pachliAccountId = :pachliAccountId AND name = :name
""",
    )
    suspend fun deleteForAccount(pachliAccountId: Long, name: String)
}
