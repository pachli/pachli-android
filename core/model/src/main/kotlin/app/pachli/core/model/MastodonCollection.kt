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

data class Collection(
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
)

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
 * @property items Same as [Collection.items]. This needs to be stored so
 * a [TimelineCollection] can be converted back to a [Collection] in
 * [NotificationData.asModel].
 * @property itemIconUrls URLs for the avatar icons of the accounts in this
 * collection. Allows nulls in case an account does not have an avatar or
 * could not be fetched. This ensures [itemIconUrls] is the same size as
 * [Collection.items] in the [Collection] it was created from.
 */
data class TimelineCollection(
    val serverId: String,
    // TODO: Needs to be a TimelineAccount for name, emojis, etc.
    val accountId: String,
    val name: String,
    val description: String,
    val local: Boolean,
    val sensitive: Boolean,
    val discoverable: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val items: List<CollectionItem>,
    val itemIconUrls: List<String?>,
)

fun Collection.asTimelineCollection(accounts: Map<String, TimelineAccount>) = TimelineCollection(
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
    itemIconUrls = items.map { accounts[it.accountId]?.avatar },
)
