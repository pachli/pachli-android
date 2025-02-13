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
import androidx.room.MapColumn
import androidx.room.Query
import androidx.room.TypeConverters
import androidx.room.Upsert
import app.pachli.core.database.Converters
import app.pachli.core.database.model.StatusEntity
import app.pachli.core.database.model.StatusViewDataContentCollapsed
import app.pachli.core.database.model.StatusViewDataContentShowing
import app.pachli.core.database.model.StatusViewDataEntity
import app.pachli.core.database.model.StatusViewDataExpanded
import app.pachli.core.database.model.StatusViewDataTranslationState
import app.pachli.core.network.model.Poll

/**
 * Operations on individual statuses, irrespective of the timeline they are
 * part of.
 */
@Dao
@TypeConverters(Converters::class)
abstract class StatusDao {
    @Upsert
    abstract suspend fun upsertStatuses(statuses: Collection<StatusEntity>)

    @Upsert
    abstract suspend fun insertStatus(statusEntity: StatusEntity): Long

    @Query(
        """
SELECT *
FROM StatusEntity
WHERE timelineUserId = :pachliAccountId AND (serverId = :statusId)
        """,
    )
    abstract suspend fun getStatus(pachliAccountId: Long, statusId: String): StatusEntity?

    @Query(
        """
UPDATE StatusEntity
SET
    favourited = :favourited
WHERE timelineUserId = :pachliAccountId AND (serverId = :statusId OR reblogServerId = :statusId)
""",
    )
    abstract suspend fun setFavourited(pachliAccountId: Long, statusId: String, favourited: Boolean)

    @Query(
        """
UPDATE StatusEntity
SET
    bookmarked = :bookmarked
WHERE timelineUserId = :pachliAccountId AND (serverId = :statusId OR reblogServerId = :statusId)
""",
    )
    abstract suspend fun setBookmarked(pachliAccountId: Long, statusId: String, bookmarked: Boolean)

    @Query(
        """
UPDATE StatusEntity
SET
    reblogged = :reblogged
WHERE timelineUserId = :pachliAccountId AND (serverId = :statusId OR reblogServerId = :statusId)
""",
    )
    abstract suspend fun setReblogged(pachliAccountId: Long, statusId: String, reblogged: Boolean)

    @Query(
        """
UPDATE StatusEntity
SET
    muted = :muted
WHERE timelineUserId = :pachliAccountId AND (serverId = :statusId OR reblogServerId = :statusId)
        """,
    )
    abstract suspend fun setMuted(pachliAccountId: Long, statusId: String, muted: Boolean)

    @Query(
        """
DELETE
FROM StatusEntity
WHERE
    timelineUserId = :accountId
    AND serverId = :statusId
""",
    )
    abstract suspend fun delete(accountId: Long, statusId: String)

    @Query(
        """
UPDATE StatusEntity
SET
    poll = :poll
WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId = :statusId)
""",
    )
    abstract suspend fun setPoll(accountId: Long, statusId: String, poll: Poll)

    @Query(
        """
UPDATE StatusEntity
SET
    pinned = :pinned
WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId = :statusId)
""",
    )
    abstract suspend fun setPinned(accountId: Long, statusId: String, pinned: Boolean)

    @Query(
        """
UPDATE StatusEntity
SET
    filtered = NULL
WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId = :statusId)
""",
    )
    abstract suspend fun clearWarning(accountId: Long, statusId: String): Int

    // StatusViewData methods

    @Upsert
    abstract suspend fun upsertStatusViewData(svd: StatusViewDataEntity)

    /**
     * @param accountId the accountId to query
     * @param serverIds the IDs of the statuses to check
     * @return Map between serverIds and any cached viewdata for those statuses
     */
    @Query(
        """
SELECT *
FROM StatusViewDataEntity
WHERE
    pachliAccountId = :accountId
    AND serverId IN (:serverIds)
""",
    )
    abstract suspend fun getStatusViewData(
        accountId: Long,
        serverIds: List<String>,
    ): Map<
        @MapColumn(columnName = "serverId")
        String,
        StatusViewDataEntity,
        >

    /** Upserts [partial], setting the [expanded][StatusViewDataEntity.expanded] property. */
    @Upsert(entity = StatusViewDataEntity::class)
    abstract suspend fun setExpanded(partial: StatusViewDataExpanded)

    /**
     * Upserts [partial], setting the [contentShowing][StatusViewDataEntity.contentShowing]
     * property.
     */
    @Upsert(entity = StatusViewDataEntity::class)
    abstract suspend fun setContentShowing(partial: StatusViewDataContentShowing)

    /**
     * Upserts [partial], setting the [contentCollapsed][StatusViewDataEntity.contentCollapsed]
     * property.
     */
    @Upsert(entity = StatusViewDataEntity::class)
    abstract suspend fun setContentCollapsed(partial: StatusViewDataContentCollapsed)

    /**
     * Upserts [partial], setting the [translationState][StatusViewDataEntity.translationState]
     * property.
     */
    @Upsert(entity = StatusViewDataEntity::class)
    abstract suspend fun setTranslationState(partial: StatusViewDataTranslationState)
}
