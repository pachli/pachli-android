/*
 * Copyright (c) 2026 Pachli Association
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
import app.pachli.core.database.model.DraftEntity
import app.pachli.core.model.Draft
import kotlinx.coroutines.flow.Flow

@Dao
@TypeConverters(Converters::class)
interface DraftDao {
    @Upsert
    suspend fun upsert(draft: DraftEntity): Long

    @Query(
        """
SELECT *
FROM DraftEntity
WHERE pachliAccountId = :pachliAccountId
ORDER BY id ASC
""",
    )
    fun draftsPagingSource(pachliAccountId: Long): PagingSource<Int, DraftEntity>

    @Query(
        """
SELECT *
FROM DraftEntity
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun loadDrafts(pachliAccountId: Long): List<DraftEntity>

    @Query(
        """
DELETE
FROM DraftEntity
WHERE id = :draftId
""",
    )
    suspend fun delete(draftId: Long)

    @Query(
        """
SELECT *
FROM DraftEntity
WHERE id = :draftId
""",
    )
    suspend fun find(draftId: Long): DraftEntity?

    /** Sets the [failureMessage] and [state] on [draftId]. */
    @Query(
        """
UPDATE DraftEntity
SET
    failureMessage = :failureMessage,
    state = :state
WHERE id = :draftId
        """,
    )
    suspend fun updateFailureState(
        draftId: Long,
        failureMessage: String?,
        state: Draft.State,
    )

    /** Sets the [state] on [draftId]. */
    @Query(
        """
UPDATE DraftEntity
SET
    state = :state
WHERE id = :draftId
        """,
    )
    suspend fun updateState(draftId: Long, state: Draft.State)

    /**
     * Resets the state of any drafts in [Draft.State.EDITING] state to [Draft.State.DEFAULT].
     */
    @Query(
        """
UPDATE DraftEntity
SET
    state = "DEFAULT"
WHERE state = "EDITING"
        """,
    )
    suspend fun resetEditingState()

    /** Flow counting the number of drafts owned by [pachliAccountId]. */
    @Query(
        """
SELECT COUNT(*)
FROM DraftEntity
WHERE pachliAccountId = :pachliAccountId
        """,
    )
    fun countDrafts(pachliAccountId: Long): Flow<Int>
}
