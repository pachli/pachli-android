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
import androidx.room.MapColumn
import androidx.room.Query
import androidx.room.Upsert
import app.pachli.core.database.model.TranslatedStatusEntity

@Dao
interface TranslatedStatusDao {
    @Upsert
    suspend fun upsert(translatedStatusEntity: TranslatedStatusEntity)

    /**
     * @return map from statusIDs to known translations for those IDs
     */
    @Query(
        """
SELECT *
FROM TranslatedStatusEntity
WHERE
    timelineUserId = :accountId
    AND serverId IN (:serverIds)
""",
    )
    suspend fun getTranslations(
        accountId: Long,
        serverIds: List<String>,
    ): Map<
        @MapColumn(columnName = "serverId")
        String,
        TranslatedStatusEntity,
        >
}
