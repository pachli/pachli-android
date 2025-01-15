/*
 * Copyright 2023 Pachli Association
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
import androidx.room.Upsert
import app.pachli.core.database.model.RemoteKeyEntity

@Dao
interface RemoteKeyDao {
    @Upsert
    suspend fun upsert(remoteKey: RemoteKeyEntity)

    @Query("SELECT * FROM RemoteKeyEntity WHERE accountId = :accountId AND timelineId = :timelineId AND kind = :kind")
    suspend fun remoteKeyForKind(
        accountId: Long,
        timelineId: String,
        kind: RemoteKeyEntity.RemoteKeyKind,
    ): RemoteKeyEntity?

    @Query("DELETE FROM RemoteKeyEntity WHERE accountId = :accountId AND timelineId = :timelineId")
    suspend fun delete(accountId: Long, timelineId: String)

    @Query(
        """
        DELETE
          FROM RemoteKeyEntity
         WHERE accountId = :accountId
           AND timelineId = :timelineId
           AND (kind = "PREV" OR kind = "NEXT")
        """,
    )
    suspend fun deletePrevNext(accountId: Long, timelineId: String)

    /** @return The remote key ID to use when refreshing. */
    @Query(
        """
        SELECT `key`
          FROM RemoteKeyEntity
         WHERE accountId = :pachliAccountId
           AND timelineId = :timelineId
           AND kind = "REFRESH"
        """,
    )
    suspend fun getRefreshKey(pachliAccountId: Long, timelineId: String): String?

    /** @return All remote keys for [pachliAccountId]. */
    @Query(
        """
        SELECT *
          FROM RemoteKeyEntity
         WHERE accountId = :pachliAccountId
        """,
    )
    suspend fun loadAllForAccount(pachliAccountId: Long): List<RemoteKeyEntity>
}
