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

package app.pachli.core.database.dao

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.TypeConverters
import androidx.room.Upsert
import app.pachli.core.database.Converters
import app.pachli.core.database.model.DraftEntity

@Dao
@TypeConverters(Converters::class)
interface DraftDao {
    @Upsert
    suspend fun upsert(draft: DraftEntity)

    @Query(
        """
SELECT *
FROM DraftEntity
WHERE accountId = :accountId
ORDER BY id ASC
""",
    )
    fun draftsPagingSource(accountId: Long): PagingSource<Int, DraftEntity>

    @Query(
        """
SELECT COUNT(*)
FROM DraftEntity
WHERE accountId = :accountId AND failedToSendNew = 1
""",
    )
    fun draftsNeedUserAlert(accountId: Long): LiveData<Int>

    @Query(
        """
UPDATE DraftEntity
SET
    failedToSendNew = 0
WHERE accountId = :accountId AND failedToSendNew = 1
""",
    )
    suspend fun draftsClearNeedUserAlert(accountId: Long)

    @Query(
        """
SELECT *
FROM DraftEntity
WHERE accountId = :accountId
""",
    )
    suspend fun loadDrafts(accountId: Long): List<DraftEntity>

    @Query(
        """
DELETE
FROM DraftEntity
WHERE id = :id
""",
    )
    suspend fun delete(id: Int)

    @Query(
        """
SELECT *
FROM DraftEntity
WHERE id = :id
""",
    )
    suspend fun find(id: Int): DraftEntity?
}
