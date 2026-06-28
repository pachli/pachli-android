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

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Upsert
import app.pachli.core.database.Converters
import app.pachli.core.database.model.CollectionEntity
import app.pachli.core.database.model.CollectionItemEntity
import app.pachli.core.database.model.CollectionViewDataEntity
import app.pachli.core.database.model.CollectionWithAccountsData
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.database.model.TimelineCollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
@TypeConverters(Converters::class)
interface CollectionsDao {
    @Upsert
    suspend fun upsertCollections(collections: Collection<CollectionEntity>)

    @Upsert
    suspend fun upsertCollectionItems(collectionItems: Collection<CollectionItemEntity>)

    @Upsert
    suspend fun upsertTimelineCollections(timelineCollections: Collection<TimelineCollectionEntity>)

    @Upsert
    suspend fun upsertCollectionViewData(entity: CollectionViewDataEntity)

    @Transaction
    @Query(
        """
SELECT
    collection.*,
    owner.serverId AS 'owner_serverId',
    owner.timelineUserId AS 'owner_timelineUserId',
    owner.localUsername AS 'owner_localUsername',
    owner.username AS 'owner_username',
    owner.displayName AS 'owner_displayName',
    owner.url AS 'owner_url',
    owner.avatar AS 'owner_avatar',
    owner.emojis AS 'owner_emojis',
    owner.bot AS 'owner_bot',
    owner.createdAt AS 'owner_createdAt',
    owner.limited AS 'owner_limited',
    owner.note AS 'owner_note',
    owner.roles AS 'owner_roles',
    owner.pronouns AS 'owner_pronouns'
FROM CollectionEntity AS collection
JOIN TimelineAccountEntity owner
  ON collection.accountId = owner.serverId
WHERE collection.pachliAccountId = :pachliAccountId AND collection.serverId = :collectionId
        """,
    )
    fun getCollection2(pachliAccountId: Long, collectionId: String): Flow<CollectionWithAccountsData>

    @Transaction
    @Query(
        """
WITH CollectionWithAccount AS (
SELECT
    collection.*,
    owner.serverId AS 'owner_serverId',
    owner.timelineUserId AS 'owner_timelineUserId',
    owner.localUsername AS 'owner_localUsername',
    owner.username AS 'owner_username',
    owner.displayName AS 'owner_displayName',
    owner.url AS 'owner_url',
    owner.avatar AS 'owner_avatar',
    owner.emojis AS 'owner_emojis',
    owner.bot AS 'owner_bot',
    owner.createdAt AS 'owner_createdAt',
    owner.limited AS 'owner_limited',
    owner.note AS 'owner_note',
    owner.roles AS 'owner_roles',
    owner.pronouns AS 'owner_pronouns'
 FROM CollectionEntity AS collection
 JOIN TimelineAccountEntity owner
  ON collection.accountId = owner.serverId
)
SELECT * FROM CollectionWithAccount collection
 JOIN TimelineAccountEntity account
 JOIN CollectionItemEntity item
  ON item.collectionServerId = collection.serverId AND account.serverId = item.accountId
 WHERE collection.pachliAccountId = :pachliAccountId AND collection.serverId = :collectionId
        """,
    )
    fun getCollection(pachliAccountId: Long, collectionId: String): Flow<Map<CollectionWithAccountsData, List<TimelineAccountEntity>>>
}
