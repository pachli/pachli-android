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

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.pachli.core.database.model.ConversationEntity

@Dao
interface ConversationsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversations: List<ConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Query(
        """
DELETE
FROM ConversationEntity
WHERE id = :id AND accountId = :accountId
""",
    )
    suspend fun delete(id: String, accountId: Long)

    @Query(
        """
SELECT *
FROM ConversationEntity
WHERE accountId = :accountId
ORDER BY `order` ASC
""",
    )
    fun conversationsForAccount(accountId: Long): PagingSource<Int, ConversationEntity>

    @Deprecated("Use conversationsForAccount, this is only for use in tests")
    @Query(
        """
SELECT *
FROM ConversationEntity
WHERE accountId = :pachliAccountId
""",
    )
    suspend fun loadAllForAccount(pachliAccountId: Long): List<ConversationEntity>

    @Query(
        """
DELETE
FROM ConversationEntity
WHERE accountId = :accountId
""",
    )
    suspend fun deleteForAccount(accountId: Long)

    @Query(
        """
UPDATE ConversationEntity
SET
    s_bookmarked = :bookmarked
WHERE accountId = :accountId AND s_id = :lastStatusId
""",
    )
    suspend fun setBookmarked(accountId: Long, lastStatusId: String, bookmarked: Boolean)

    @Query(
        """
UPDATE ConversationEntity
SET
    s_collapsed = :collapsed
WHERE accountId = :accountId AND s_id = :lastStatusId
""",
    )
    suspend fun setCollapsed(accountId: Long, lastStatusId: String, collapsed: Boolean)

    @Query(
        """
UPDATE ConversationEntity
SET
    s_expanded = :expanded
WHERE accountId = :accountId AND s_id = :lastStatusId
""",
    )
    suspend fun setExpanded(accountId: Long, lastStatusId: String, expanded: Boolean)

    @Query(
        """
UPDATE ConversationEntity
SET
    s_favourited = :favourited
WHERE accountId = :accountId AND s_id = :lastStatusId
""",
    )
    suspend fun setFavourited(accountId: Long, lastStatusId: String, favourited: Boolean)

    @Query(
        """
UPDATE ConversationEntity
SET
    s_muted = :muted
WHERE accountId = :accountId AND s_id = :lastStatusId
""",
    )
    suspend fun setMuted(accountId: Long, lastStatusId: String, muted: Boolean)

    @Query(
        """
UPDATE ConversationEntity
SET
    s_showingHiddenContent = :showingHiddenContent
WHERE accountId = :accountId AND s_id = :lastStatusId
""",
    )
    suspend fun setShowingHiddenContent(accountId: Long, lastStatusId: String, showingHiddenContent: Boolean)

    @Query(
        """
UPDATE ConversationEntity
SET
    s_poll = :poll
WHERE accountId = :accountId AND s_id = :lastStatusId
""",
    )
    suspend fun setVoted(accountId: Long, lastStatusId: String, poll: String)
}
