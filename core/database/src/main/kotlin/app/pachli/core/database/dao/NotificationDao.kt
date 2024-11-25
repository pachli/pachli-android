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

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.TypeConverters
import androidx.room.Upsert
import app.pachli.core.database.Converters
import app.pachli.core.database.model.NotificationData
import app.pachli.core.database.model.NotificationEntity

@Dao
@TypeConverters(Converters::class)
interface NotificationDao {
    @Upsert
    suspend fun insertAll(notifications: List<NotificationEntity>)

    //    @Transaction
//    @Query(
//        """
//        SELECT *
//          FROM NotificationEntity
//         WHERE pachliAccountId = :pachliAccountId
//    """,
//    )
//    fun pagingSource(pachliAccountId: Long): PagingSource<Int, NotificationData>
    @Query(
        """
            SELECT n.pachliAccountId,
                   n.serverId,
                   n.type,
                   n.createdAt,
                   n.accountServerId,
                   n.statusServerId,
                   a.serverId as 'a_serverId', a.timelineUserId as 'a_timelineUserId',
a.localUsername as 'a_localUsername', a.username as 'a_username',
a.displayName as 'a_displayName', a.url as 'a_url', a.avatar as 'a_avatar',
a.emojis as 'a_emojis', a.bot as 'a_bot', a.createdAt as 'a_createdAt', a.limited as 'a_limited'
              FROM NotificationEntity n
              LEFT JOIN TimelineAccountEntity a ON (n.pachliAccountId = a.timelineUserId AND n.accountServerId = a.serverId)
             WHERE n.pachliAccountId = :pachliAccountId
        """,
    )
    fun pagingSource(pachliAccountId: Long): PagingSource<Int, NotificationData>

    @Query("DELETE FROM NotificationEntity WHERE pachliAccountId = :pachliAccountId")
    fun clearAll(pachliAccountId: Long)
}
