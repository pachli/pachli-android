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

import androidx.room3.ColumnTypeConverters
import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Query
import androidx.room3.Upsert
import app.pachli.core.database.Converters
import app.pachli.core.database.model.FollowingAccountEntity

@Dao
@ColumnTypeConverters(Converters::class)
interface FollowingAccountDao {
    @Query(
        """
DELETE
FROM FollowingAccountEntity
WHERE pachliAccountId = :accountId
""",
    )
    suspend fun deleteAllForAccount(accountId: Long)

    @Upsert
    suspend fun upsert(accounts: List<FollowingAccountEntity>)

    @Upsert
    suspend fun upsert(account: FollowingAccountEntity)

    @Delete
    suspend fun delete(account: FollowingAccountEntity)

    @Query(
        """
DELETE
FROM FollowingAccountEntity
WHERE pachliAccountId = :pachliAccountId AND accountId = :accountId
    """,
    )
    suspend fun delete(pachliAccountId: Long, accountId: String)

    @Query(
        """
SELECT *
FROM FollowingAccountEntity
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun loadAllForAccount(pachliAccountId: Long): List<FollowingAccountEntity>

    /**
     * Delete cached following relationships with [domain].
     */
    @Query(
        """
DELETE
FROM FollowingAccountEntity
WHERE pachliAccountId = :pachliAccountId AND domain = :domain
    """,
    )
    suspend fun deleteAllByDomain(pachliAccountId: Long, domain: String)
}
