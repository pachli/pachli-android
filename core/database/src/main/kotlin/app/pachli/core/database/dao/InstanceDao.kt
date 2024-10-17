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
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import androidx.room.Upsert
import app.pachli.core.database.model.EmojisEntity
import app.pachli.core.database.model.InstanceInfoEntity
import app.pachli.core.database.model.ServerEntity

@Dao
interface InstanceDao {

    @Upsert
    suspend fun upsert(instance: InstanceInfoEntity)

    @Upsert
    suspend fun upsert(emojis: EmojisEntity)

    @Upsert
    suspend fun upsert(serverEntity: ServerEntity)

    @Transaction
    @Query("SELECT * FROM InstanceInfoEntity WHERE instance = :instance LIMIT 1")
    suspend fun getInstanceInfo(instance: String): InstanceInfoEntity?

    @Query("SELECT * FROM ServerEntity WHERE accountId = :pachliAccountId")
    suspend fun getServer(pachliAccountId: Long): ServerEntity?

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM EmojisEntity WHERE accountId = :pachliAccountId")
    suspend fun getEmojiInfo(pachliAccountId: Long): EmojisEntity?
}
