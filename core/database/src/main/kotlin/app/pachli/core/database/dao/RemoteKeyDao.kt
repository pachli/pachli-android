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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.RemoteKeyKind

@Dao
interface RemoteKeyDao {
    // TODO(https://issuetracker.google.com/issues/243039555), switch to @Upsert
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(remoteKey: RemoteKeyEntity)

    @Query("SELECT * FROM RemoteKeyEntity WHERE accountId = :accountId AND timelineId = :timelineId AND kind = :kind")
    suspend fun remoteKeyForKind(accountId: Long, timelineId: String, kind: RemoteKeyKind): RemoteKeyEntity?

    @Query("DELETE FROM RemoteKeyEntity WHERE accountId = :accountId AND timelineId = :timelineId")
    suspend fun delete(accountId: Long, timelineId: String)
}
