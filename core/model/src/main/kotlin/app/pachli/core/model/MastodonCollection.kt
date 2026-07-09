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

package app.pachli.core.model

import com.squareup.moshi.JsonClass
import java.time.Instant

/**
 * @property serverId
 * @property accountId
 * @property name
 * @property description
 * @property local
 * @property sensitive
 * @property discoverable
 * @property hashtag
 * @property createdAt
 * @property updatedAt
 */
interface ICollection {
    val serverId: String
    val accountId: String
    val name: String
    val description: String
    val local: Boolean
    val sensitive: Boolean
    val discoverable: Boolean
    val hashtag: ShallowHashtag?
    val createdAt: Instant
    val updatedAt: Instant
}

data class Collection(
    override val serverId: String,
    override val accountId: String,
    override val name: String,
    override val description: String,
    override val local: Boolean,
    override val sensitive: Boolean,
    override val discoverable: Boolean,
    override val hashtag: ShallowHashtag?,
    override val createdAt: Instant,
    override val updatedAt: Instant,
    val items: List<CollectionItem> = emptyList(),
) : ICollection

/**
 * @property serverId Server ID for this item.
 * @property accountId Server ID for the account in this item.
 * @property state
 * @property createdAt
 */
@JsonClass(generateAdapter = true)
data class CollectionItem(
    val serverId: String,
    val accountId: String?,
    val state: State,
    val createdAt: Instant,
) {
    enum class State {
        UNKNOWN,
        PENDING,
        ACCEPTED,
    }
}

/**
 * @property ownerAccountId ID of the account that owns the collection.
 * Required to convert a [TimelineCollection] back to a [Collection].
 * @property ownerAccount Account that owns the collection. Nullable because
 * the call to retrieve owner account information may fail.
 * @property items Same as [Collection.items]. This needs to be stored so
 * a [TimelineCollection] can be converted back to a [Collection] in
 * [NotificationData.asModel].
 * @property itemIconUrls URLs for the avatar icons of the accounts in this
 * collection. Allows nulls in case an account does not have an avatar or
 * could not be fetched. This ensures [itemIconUrls] is the same size as
 * [Collection.items] in the [Collection] it was created from.
 */
data class TimelineCollection(
    override val serverId: String,
    val ownerAccountId: String,
    val ownerAccount: TimelineAccount? = null,
    override val name: String,
    override val description: String,
    override val local: Boolean,
    override val sensitive: Boolean,
    override val discoverable: Boolean,
    override val hashtag: ShallowHashtag?,
    override val createdAt: Instant,
    override val updatedAt: Instant,
    val items: List<CollectionItem> = emptyList(),
    val itemIconUrls: List<String?>,
) : ICollection {
    override val accountId = ownerAccountId
}

fun Collection.asTimelineCollection(accounts: Map<String, Account>) = TimelineCollection(
    serverId = serverId,
    ownerAccountId = accountId,
    ownerAccount = accounts[accountId]?.asTimelineAccount(),
    name = name,
    description = description,
    local = local,
    sensitive = sensitive,
    discoverable = discoverable,
    hashtag = hashtag,
    createdAt = createdAt,
    updatedAt = updatedAt,
    items = items,
    itemIconUrls = items.map { accounts[it.accountId]?.avatar },
)

/**
 * A [Collection], its [owner][Account] account, and the [accounts][Account]
 * in the collection.
 *
 * Note: The network model keeps the collection owner as the first account,
 * this model keeps the owner as a separate property.
 *
 * @property collection
 * @property owner [Account] that owns the collection. May be null if that
 * couldn't be determined.
 * @property accounts The (resolved) accounts in the collection. May be empty,
 * or not equal the size of [collection.items][Collection.items] if some
 * accounts could not be resolved.
 */
data class CollectionWithAccounts(
    val collection: Collection,
    val owner: Account?,
    val accounts: List<Account>,
)
