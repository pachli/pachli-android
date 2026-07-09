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
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.CollectionAndOwnerEntities
import app.pachli.core.database.model.CollectionEntity
import app.pachli.core.database.model.CollectionItemEntity
import app.pachli.core.database.model.CollectionViewDataEntity
import app.pachli.core.database.model.TimelineCollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
@TypeConverters(Converters::class)
interface CollectionsDao {
    @Upsert
    suspend fun upsertCollection(collection: CollectionEntity)

    @Upsert
    suspend fun upsertCollections(collections: Collection<CollectionEntity>)

    @Upsert
    suspend fun upsertCollectionItems(collectionItems: Collection<CollectionItemEntity>)

    @Upsert
    suspend fun upsertTimelineCollections(timelineCollections: Collection<TimelineCollectionEntity>)

    @Upsert
    suspend fun upsertCollectionViewData(entity: CollectionViewDataEntity)

    @Query(
        """
DELETE FROM CollectionItemEntity
WHERE pachliAccountId = :pachliAccountId AND collectionServerId = :collectionId AND accountId = :accountId
        """,
    )
    suspend fun removeAccountFromCollection(pachliAccountId: Long, collectionId: String, accountId: String)

    @Transaction
    @Query(
        """
WITH CollectionWithAccount AS (
SELECT
    collection.*,
    owner.serverId AS 'owner_serverId',
    owner.pachliAccountId AS 'owner_pachliAccountId',
    owner.serverId AS 'owner_serverId',
    owner.localUsername AS 'owner_localUsername',
    owner.username AS 'owner_username',
    owner.displayName AS 'owner_displayName',
    owner.createdAt AS 'owner_createdAt',
    owner.url AS 'owner_url',
    owner.avatar AS 'owner_avatar',
    owner.note AS 'owner_note',
    owner.header AS 'owner_header',
    owner.locked AS 'owner_locked',
    owner.lastStatusAt AS 'owner_lastStatusAt',
    owner.followersCount AS 'owner_followersCount',
    owner.followingCount AS 'owner_followingCount',
    owner.statusesCount AS 'owner_statusesCount',
    owner.bot AS 'owner_bot',
    owner.emojis AS 'owner_emojis',
    owner.fields AS 'owner_fields',
    owner.movedAccount AS 'owner_movedAccount',
    owner.limited AS 'owner_limited',
    owner.roles AS 'owner_roles',
    owner.pronouns AS 'owner_pronouns'
 FROM CollectionEntity AS collection
 JOIN AccountEntity owner
  ON collection.accountId = owner.serverId
)
SELECT * FROM CollectionWithAccount collection
 JOIN AccountEntity account
 JOIN CollectionItemEntity item
  ON item.collectionServerId = collection.serverId AND account.serverId = item.accountId
 WHERE collection.pachliAccountId = :pachliAccountId AND collection.serverId = :collectionId
        """,
    )
    fun getCollection(pachliAccountId: Long, collectionId: String): Flow<Map<CollectionAndOwnerEntities, List<AccountEntity>>>
}
