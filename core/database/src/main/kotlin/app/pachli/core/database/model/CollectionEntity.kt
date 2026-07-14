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

import androidx.room3.ColumnTypeConverters
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import app.pachli.core.database.Converters
import app.pachli.core.model.CollectionItem
import app.pachli.core.model.ShallowHashtag
import app.pachli.core.model.TimelineCollection
import app.pachli.core.model.collection.CollectionDisplayAction
import java.time.Instant

/**
 * Data about a collection and it's owner.
 *
 * @property collection The collection
 * @property ownerAccount The collection's owner account.
 */
data class CollectionAndOwnerEntities(
    @Embedded val collection: CollectionEntity,
    @Embedded(prefix = "owner_") val ownerAccount: AccountEntity?,
)

/**
 * Represents an [app.pachli.core.model.Collection].
 *
 * @property pachliAccountId
 * @property collectionId [app.pachli.core.model.ICollection.collectionId]
 * @property accountId [app.pachli.core.model.ICollection.accountId]
 * @property name [app.pachli.core.model.ICollection.name]
 * @property description [app.pachli.core.model.ICollection.description]
 * @property local [app.pachli.core.model.ICollection.local]
 * @property sensitive [app.pachli.core.model.ICollection.sensitive]
 * @property discoverable [app.pachli.core.model.ICollection.discoverable]
 * @property hashtag [app.pachli.core.model.ICollection.hashtag]
 * @property createdAt [app.pachli.core.model.ICollection.createdAt]
 * @property updatedAt [app.pachli.core.model.ICollection.updatedAt]
 * @property items [app.pachli.core.model.Collection.items]
 */
@Entity(
    primaryKeys = ["pachliAccountId", "collectionId"],
    foreignKeys = [
        ForeignKey(
            entity = PachliAccountEntity::class,
            parentColumns = ["pachliAccountId"],
            childColumns = ["pachliAccountId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
)
@ColumnTypeConverters(Converters::class)
data class CollectionEntity(
    val pachliAccountId: Long,
    val collectionId: String,
    val accountId: String,
    val name: String,
    val description: String,
    val local: Boolean,
    val sensitive: Boolean,
    val discoverable: Boolean,
    val hashtag: ShallowHashtag?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val items: List<CollectionItem>,
) {
    fun asModel() = app.pachli.core.model.Collection(
        collectionId = collectionId,
        accountId = accountId,
        name = name,
        description = description,
        local = local,
        sensitive = sensitive,
        discoverable = discoverable,
        hashtag = hashtag,
        createdAt = createdAt,
        updatedAt = updatedAt,
        items = items,
    )
}

fun app.pachli.core.model.Collection.asEntity(pachliAccountId: Long) = CollectionEntity(
    pachliAccountId = pachliAccountId,
    collectionId = collectionId,
    accountId = accountId,
    name = name,
    description = description,
    local = local,
    sensitive = sensitive,
    discoverable = discoverable,
    hashtag = hashtag,
    createdAt = createdAt,
    updatedAt = updatedAt,
    items = items,
)

@JvmName("iterableCollectionAsEntity")
fun Iterable<app.pachli.core.model.Collection>.asEntity(pachliAccountId: Long) = map { it.asEntity(pachliAccountId) }

/**
 * Represents an [app.pachli.core.model.TimelineCollection].
 *
 * @property pachliAccountId
 * @property collectionId [app.pachli.core.model.ICollection.collectionId]
 * @property accountId [app.pachli.core.model.TimelineCollection.accountId]
 * @property ownerAccount [app.pachli.core.model.TimelineCollection.account]
 * @property name [app.pachli.core.model.ICollection.name]
 * @property description [app.pachli.core.model.ICollection.description]
 * @property local [app.pachli.core.model.ICollection.local]
 * @property sensitive [app.pachli.core.model.ICollection.sensitive]
 * @property discoverable [app.pachli.core.model.ICollection.discoverable]
 * @property hashtag [app.pachli.core.model.ICollection.hashtag]
 * @property createdAt [app.pachli.core.model.ICollection.createdAt]
 * @property updatedAt [app.pachli.core.model.ICollection.updatedAt]
 * @property items [app.pachli.core.model.Collection.items]
 * @property itemIconUrls [app.pachli.core.model.TimelineCollection.itemIconUrls]
 */
@Entity(
    primaryKeys = ["pachliAccountId", "collectionId"],
    foreignKeys = [
        ForeignKey(
            entity = PachliAccountEntity::class,
            parentColumns = ["pachliAccountId"],
            childColumns = ["pachliAccountId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
)
@ColumnTypeConverters(Converters::class)
data class TimelineCollectionEntity(
    val pachliAccountId: Long,
    val collectionId: String,
    val accountId: String,
    @Embedded(prefix = "owner_") val ownerAccount: TimelineAccountEntity?,
    val name: String,
    val description: String,
    val local: Boolean,
    val sensitive: Boolean,
    val discoverable: Boolean,
    val hashtag: ShallowHashtag?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val items: List<CollectionItem>,
    val itemIconUrls: List<String?>,
) {
    fun asModel() = TimelineCollection(
        collectionId = collectionId,
        accountId = accountId,
        account = ownerAccount?.asModel(),
        name = name,
        description = description,
        local = local,
        sensitive = sensitive,
        discoverable = discoverable,
        hashtag = hashtag,
        createdAt = createdAt,
        updatedAt = updatedAt,
        items = items,
        itemIconUrls = itemIconUrls,
    )

    /**
     * @return as a [Collection][app.pachli.core.model.Collection].
     */
    fun asCollectionModel() = app.pachli.core.model.Collection(
        collectionId = collectionId,
        accountId = accountId,
        name = name,
        description = description,
        local = local,
        sensitive = sensitive,
        discoverable = discoverable,
        hashtag = hashtag,
        createdAt = createdAt,
        updatedAt = updatedAt,
        items = items,
    )
}

fun Iterable<TimelineCollectionEntity>.asModel() = map { it.asModel() }

fun TimelineCollection.asEntity(pachliAccountId: Long) = TimelineCollectionEntity(
    pachliAccountId = pachliAccountId,
    collectionId = collectionId,
    accountId = accountId,
    ownerAccount = account?.asEntity(pachliAccountId),
    name = name,
    description = description,
    local = local,
    sensitive = sensitive,
    discoverable = discoverable,
    hashtag = hashtag,
    createdAt = createdAt,
    updatedAt = updatedAt,
    items = items,
    itemIconUrls = itemIconUrls,
)

fun Iterable<TimelineCollection>.asEntity(pachliAccountId: Long) = map { it.asEntity(pachliAccountId) }

/**
 * @property pachliAccountId
 * @property collectionId [CollectionEntity.collectionId] this item belongs to.
 * @property collectionItemId Server ID for this item.
 * @property accountId [CollectionEntity.accountId] for this item.
 * @property state
 * @property createdAt
 */
@Entity(
    primaryKeys = ["pachliAccountId", "collectionId", "collectionItemId"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["pachliAccountId", "collectionId"],
            childColumns = ["pachliAccountId", "collectionId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
)
@ColumnTypeConverters(Converters::class)
data class CollectionItemEntity(
    val pachliAccountId: Long,
    val collectionId: String,
    val collectionItemId: String,
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

fun CollectionItem.asEntity(pachliAccountId: Long, collectionId: String) = CollectionItemEntity(
    pachliAccountId = pachliAccountId,
    collectionId = collectionId,
    collectionItemId = collectionItemId,
    accountId = accountId,
    state = state.asEntity(),
    createdAt = createdAt,
)

fun CollectionItem.State.asEntity() = when (this) {
    CollectionItem.State.UNKNOWN -> CollectionItemEntity.State.UNKNOWN
    CollectionItem.State.PENDING -> CollectionItemEntity.State.PENDING
    CollectionItem.State.ACCEPTED -> CollectionItemEntity.State.ACCEPTED
}

fun Iterable<CollectionItem>.asEntity(pachliAccountId: Long, collectionId: String) = map { it.asEntity(pachliAccountId, collectionId) }

/**
 * Pachli-specific viewdata for the collection.
 *
 * @property pachliAccountId
 * @property collectionId Collection's remote server ID.
 * @property displayAction The user's [CollectionDisplayAction] for
 * this collection.
 */
@Entity(
    primaryKeys = ["pachliAccountId", "collectionId"],
    foreignKeys = (
        [
            ForeignKey(
                entity = PachliAccountEntity::class,
                parentColumns = ["pachliAccountId"],
                childColumns = ["pachliAccountId"],
                onDelete = ForeignKey.CASCADE,
                deferred = true,
            ),
        ]
        ),
)
@ColumnTypeConverters(Converters::class)
data class CollectionViewDataEntity(
    val pachliAccountId: Long,
    val collectionId: String,
    val displayAction: CollectionDisplayAction? = null,
)

data class CollectionCardViewData(
    @Embedded
    val timelineCollectionEntity: TimelineCollectionEntity,
    val displayAction: CollectionDisplayAction,
    val isMember: Boolean,
) {
    fun asModel() = app.pachli.core.model.collection.CollectionCardViewData(
        timelineCollection = timelineCollectionEntity.asModel(),
        displayAction = displayAction,
        isMember = isMember,
    )
}
