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

package app.pachli.core.database.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.TypeConverters
import app.pachli.core.database.Converters
import app.pachli.core.model.CollectionItem
import app.pachli.core.model.TimelineCollection
import app.pachli.core.model.collection.CollectionDisplayAction
import java.time.Instant

/**
 * Data about a Collection
 */
data class CollectionWithAccountsData(
    @Embedded val collection: CollectionEntity,
    @Embedded(prefix = "owner_") val ownerAccount: TimelineAccountEntity,

)

// data class CollectionViewData(
//    val collection: Collection,
//    val accounts
// )

@Entity(
    primaryKeys = ["pachliAccountId", "serverId"],
    foreignKeys = [
        ForeignKey(
            entity = PachliAccountEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("pachliAccountId"),
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
//    indices = [Index(value = ["pachliAccountId", "serverId"])],
)
@TypeConverters(Converters::class)
data class CollectionEntity(
    val pachliAccountId: Long,
    val serverId: String,
    val accountId: String,
    val name: String,
    val description: String,
    val local: Boolean,
    val sensitive: Boolean,
    val discoverable: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val items: List<CollectionItem>,
) {
    fun asModel() = app.pachli.core.model.Collection(
        serverId = serverId,
        accountId = accountId,
        name = name,
        description = description,
        local = local,
        sensitive = sensitive,
        discoverable = discoverable,
        createdAt = createdAt,
        updatedAt = updatedAt,
        items = items,
    )
}

fun app.pachli.core.model.Collection.asEntity(pachliAccountId: Long) = CollectionEntity(
    pachliAccountId = pachliAccountId,
    serverId = serverId,
    accountId = accountId,
    name = name,
    description = description,
    local = local,
    sensitive = sensitive,
    discoverable = discoverable,
    createdAt = createdAt,
    updatedAt = updatedAt,
    items = items,
)

@JvmName("iterableCollectionAsEntity")
fun Iterable<app.pachli.core.model.Collection>.asEntity(pachliAccountId: Long) = map { it.asEntity(pachliAccountId) }

@Entity(
    primaryKeys = ["pachliAccountId", "serverId"],
    foreignKeys = [
        ForeignKey(
            entity = PachliAccountEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("pachliAccountId"),
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
)
@TypeConverters(Converters::class)
data class TimelineCollectionEntity(
    val pachliAccountId: Long,
    val serverId: String,
    val ownerAccountId: String,
    @Embedded(prefix = "owner_") val ownerAccount: TimelineAccountEntity?,
    val name: String,
    val description: String,
    val local: Boolean,
    val sensitive: Boolean,
    val discoverable: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val items: List<CollectionItem>,
    val itemIconUrls: List<String?>,
) {
    fun asModel() = TimelineCollection(
        serverId = serverId,
        ownerAccountId = ownerAccountId,
        ownerAccount = ownerAccount?.asModel(),
        name = name,
        description = description,
        local = local,
        sensitive = sensitive,
        discoverable = discoverable,
        createdAt = createdAt,
        updatedAt = updatedAt,
        items = items,
        itemIconUrls = itemIconUrls,
    )

    // TODO: Check if this function is needed. If not then `accountId`
    // can be removed from TimelineCollection and TimelineCollectionEntity
    fun asCollectionModel() = app.pachli.core.model.Collection(
        serverId = serverId,
        accountId = ownerAccountId,
        name = name,
        description = description,
        local = local,
        sensitive = sensitive,
        discoverable = discoverable,
        createdAt = createdAt,
        updatedAt = updatedAt,
        items = items,
    )
}

fun TimelineCollection.asEntity(pachliAccountId: Long) = TimelineCollectionEntity(
    pachliAccountId = pachliAccountId,
    serverId = serverId,
    ownerAccountId = ownerAccountId,
    ownerAccount = ownerAccount?.asEntity(pachliAccountId),
    name = name,
    description = description,
    local = local,
    sensitive = sensitive,
    discoverable = discoverable,
    createdAt = createdAt,
    updatedAt = updatedAt,
    items = items,
    itemIconUrls = itemIconUrls,
)

fun Iterable<TimelineCollection>.asEntity(pachliAccountId: Long) = map { it.asEntity(pachliAccountId) }

/**
 * @property pachliAccountId
 * @property collectionServerId [CollectionEntity.serverId] this item belongs to.
 * @property serverId Server ID for this item.
 * @property accountId [CollectionEntity.accountId] for this item.
 * @property state
 * @property createdAt
 */
@Entity(
    primaryKeys = ["pachliAccountId", "collectionServerId", "serverId"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = arrayOf("pachliAccountId", "serverId"),
            childColumns = arrayOf("pachliAccountId", "collectionServerId"),
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
)
@TypeConverters(Converters::class)
data class CollectionItemEntity(
    val pachliAccountId: Long,
    val collectionServerId: String,
    val serverId: String,
    val accountId: String?,
    val state: State,
    val createdAt: Instant,
) {
    enum class State {
        UNKNOWN,
        PENDING,
        ACCEPTED,

        ;

        fun asModel() = when (this) {
            UNKNOWN -> CollectionItem.State.UNKNOWN
            PENDING -> CollectionItem.State.PENDING
            ACCEPTED -> CollectionItem.State.ACCEPTED
        }
    }
}

fun CollectionItem.asEntity(pachliAccountId: Long, collectionServerId: String) = CollectionItemEntity(
    pachliAccountId = pachliAccountId,
    collectionServerId = collectionServerId,
    serverId = serverId,
    accountId = accountId,
    state = state.asEntity(),
    createdAt = createdAt,
)

fun CollectionItem.State.asEntity() = when (this) {
    CollectionItem.State.UNKNOWN -> CollectionItemEntity.State.UNKNOWN
    CollectionItem.State.PENDING -> CollectionItemEntity.State.PENDING
    CollectionItem.State.ACCEPTED -> CollectionItemEntity.State.ACCEPTED
}

fun Iterable<CollectionItem>.asEntity(pachliAccountId: Long, collectionServerId: String) = map { it.asEntity(pachliAccountId, collectionServerId) }

/**
 * Pachli-specific viewdata for the collection.
 *
 * @property pachliAccountId
 * @property serverId Collection's remote server ID.
 * @property displayAction The user's [CollectionDisplayAction] for
 * this collection.
 */
@Entity(
    primaryKeys = ["pachliAccountId", "serverId"],
    foreignKeys = (
        [
            ForeignKey(
                entity = PachliAccountEntity::class,
                parentColumns = ["id"],
                childColumns = ["pachliAccountId"],
                onDelete = ForeignKey.CASCADE,
                deferred = true,
            ),
        ]
        ),
)
@TypeConverters(Converters::class)
data class CollectionViewDataEntity(
    val pachliAccountId: Long,
    val serverId: String,
    val displayAction: CollectionDisplayAction? = null,
)
