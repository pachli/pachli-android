/*
 * Copyright (c) 2025 Pachli Association
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
import androidx.room.Transaction
import androidx.room.TypeConverters
import app.pachli.core.database.Converters

@Dao
@TypeConverters(Converters::class)
interface DebugDao {
    @Transaction
    suspend fun clearCache() {
        deleteTimelineAccountEntity()
        deleteTimelineStatusEntity()
        deleteStatusEntity()
        deleteNotificationEntity()
        deleteConversationEntity()
        deleteStatusViewDataEntity()
        deleteTranslatedStatusEntity()
    }

    @Query("DELETE FROM TimelineAccountEntity")
    suspend fun deleteTimelineAccountEntity()

    @Query("DELETE FROM TimelineStatusEntity")
    suspend fun deleteTimelineStatusEntity()

    @Query("DELETE FROM StatusEntity")
    suspend fun deleteStatusEntity()

    @Query("DELETE FROM NotificationEntity")
    suspend fun deleteNotificationEntity()

    @Query("DELETE FROM ConversationEntity")
    suspend fun deleteConversationEntity()

    @Query("DELETE FROM StatusViewDataEntity")
    suspend fun deleteStatusViewDataEntity()

    @Query("DELETE FROM TranslatedStatusEntity")
    suspend fun deleteTranslatedStatusEntity()
}
