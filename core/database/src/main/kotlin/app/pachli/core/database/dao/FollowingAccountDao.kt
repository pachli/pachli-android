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
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.TypeConverters
import app.pachli.core.database.Converters
import app.pachli.core.database.model.FollowingAccountEntity

@Dao
@TypeConverters(Converters::class)
interface FollowingAccountDao {
    @Query(
        """
        DELETE
          FROM FollowingAccountEntity
         WHERE pachliAccountId = :accountId
    """,
    )
    suspend fun deleteAllForAccount(accountId: Long)

    @Insert
    suspend fun insert(accounts: List<FollowingAccountEntity>)

    @Insert
    suspend fun insert(account: FollowingAccountEntity)

    @Delete
    suspend fun delete(account: FollowingAccountEntity)
}
