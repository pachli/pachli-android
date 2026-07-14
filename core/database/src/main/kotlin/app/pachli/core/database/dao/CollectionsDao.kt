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

import androidx.room3.ColumnTypeConverters
import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import app.pachli.core.database.Converters
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.CollectionAndOwnerEntities
import app.pachli.core.database.model.CollectionCardViewData
import app.pachli.core.database.model.CollectionEntity
import app.pachli.core.database.model.CollectionItemEntity
import app.pachli.core.database.model.CollectionViewDataEntity
import app.pachli.core.database.model.StatusToTimelineCollectionEntity
import app.pachli.core.database.model.TimelineCollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
@ColumnTypeConverters(Converters::class)
interface CollectionsDao {
    @Upsert
    suspend fun upsertCollection(collection: CollectionEntity)

    @Upsert
    suspend fun upsertCollections(collections: Collection<CollectionEntity>)

    @Upsert
    suspend fun upsertCollectionItems(collectionItems: Collection<CollectionItemEntity>)

    @Upsert
    suspend fun upsertTimelineCollection(timelineCollection: TimelineCollectionEntity)

    @Upsert
    suspend fun upsertTimelineCollections(timelineCollections: Collection<TimelineCollectionEntity>)

    @Upsert
    suspend fun upsertCollectionViewData(entity: CollectionViewDataEntity)

    @Query(
        """
DELETE FROM CollectionItemEntity
WHERE pachliAccountId = :pachliAccountId AND collectionId = :collectionId AND accountId = :accountId
        """,
    )
    suspend fun removeAccountFromCollection(pachliAccountId: Long, collectionId: String, accountId: String)

    @Query(
        """
WITH CollectionWithAccount AS (
SELECT
    collection.*,
    owner.pachliAccountId AS 'owner_pachliAccountId',
    owner.accountId AS 'owner_accountId',
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
  ON collection.accountId = owner.accountId
)
SELECT * FROM CollectionWithAccount AS collection
LEFT JOIN CollectionItemEntity AS item ON item.pachliAccountId = :pachliAccountId AND item.collectionId = :collectionId
LEFT JOIN AccountEntity AS account ON account.pachliAccountId = :pachliAccountId AND item.accountId = account.accountId
WHERE collection.pachliAccountId = :pachliAccountId AND collection.collectionId = :collectionId
        """,
    )
    fun getCollectionFlow(pachliAccountId: Long, collectionId: String): Flow<Map<CollectionAndOwnerEntities, List<AccountEntity>>>

    @Upsert
    suspend fun saveStatusToCollectionAssociation(statusToTimelineCollectionEntities: List<StatusToTimelineCollectionEntity>)

    @Query(
        """
SELECT *
FROM TimelineCollectionEntity
WHERE pachliAccountId = :pachliAccountId AND collectionId = :collectionId
    """,
    )
    suspend fun getTimelineCollection(pachliAccountId: Long, collectionId: String): TimelineCollectionEntity?

    @Query(
        """
SELECT *
FROM TimelineCollectionEntity
WHERE pachliAccountId = :pachliAccountId AND collectionId IN (:collectionIds)
    """,
    )
    suspend fun getTimelineCollections(
        pachliAccountId: Long,
        collectionIds: List<String>,
    ): List<TimelineCollectionEntity>

    @Query(
        """
WITH MemberOf AS (
  SELECT cie.pachliAccountId, cie.collectionId
  FROM CollectionItemEntity AS cie
  WHERE cie.accountId = (
    SELECT accountId
    FROM PachliAccountEntity
    WHERE cie.pachliAccountId = PachliAccountEntity.pachliAccountId
  )
)
SELECT c.*,
IFNULL(
  vd.displayAction,
  CASE
    WHEN NOT c.sensitive THEN '{"type":"show","originalAction":null}'
    WHEN c.sensitive AND p.alwaysShowSensitiveMedia THEN '{"type":"show","originalAction":{"reason":{"type":"sensitive"}}}'
    ELSE '{"type":"hide","reason":{"type":"sensitive"}}'
  END
) AS displayAction,
m.collectionId IS NOT NULL AS isMember
FROM TimelineCollectionEntity AS c
LEFT JOIN PachliAccountEntity AS p ON (c.pachliAccountId = p.pachliAccountId)
LEFT JOIN CollectionViewDataEntity AS vd ON (c.pachliAccountId = vd.pachliAccountId AND c.collectionId = vd.collectionId)
LEFT JOIN MemberOf AS m ON (c.pachliAccountId = m.pachliAccountId AND c.collectionId = m.collectionId)
WHERE c.pachliAccountId = :pachliAccountId AND c.collectionId IN (:collectionIds)
        """,
    )
    suspend fun getCollectionCardViewData(
        pachliAccountId: Long,
        collectionIds: Collection<String>,
    ): List<CollectionCardViewData>
}
