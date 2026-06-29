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

package app.pachli.core.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

/**
 * Collection.
 *
 * @property id The server ID of the collection.
 * @property accountId The server ID of the account that owns the collection.
 * @property uri The URI (ActivityPub identifier) of the collection.
 * @property url The URL (web UI link) of the collection.
 * @property name The name of the collection.
 * @property description An optional description.
 * @property language The language of the collection (ISO 639-1 two letter code)
 * @property local True if the collection was created by an account on the
 * user's server.
 * @property sensitive True if the collection is marked as sensitive.
 * @property discoverable True if the collection should appear on the owner's
 * profile, in search results, and recommendations.
 *
 * See https://docs.joinmastodon.org/entities/Collection/
 */
@JsonClass(generateAdapter = true)
data class Collection(
    val id: String,
    @Json(name = "account_id")
    val accountId: String,
    val uri: String,
    val url: String,
    val name: String,
    val description: String,
    val language: String? = null,
    val local: Boolean = false,
    val sensitive: Boolean = false,
    val discoverable: Boolean = true,
    val tag: ShallowTag? = null,
    @Json(name = "created_at")
    val createdAt: Instant,
    @Json(name = "updated_at")
    val updatedAt: Instant,
    @Json(name = "item_count")
    val itemCount: Int,
    val items: List<CollectionItem>,
) {
    fun asModel() = app.pachli.core.model.Collection(
        serverId = id,
        accountId = accountId,
        name = name,
        description = description,
        local = local,
        sensitive = sensitive,
        discoverable = discoverable,
        hashtag = tag?.asModel(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        items = items.asModel(),
    )
}

/**
 * See https://docs.joinmastodon.org/entities/ShallowTag/
 */
@JsonClass(generateAdapter = true)
data class ShallowTag(
    val name: String,
    val url: String,
) {
    fun asModel() = app.pachli.core.model.ShallowHashtag(
        name = name,
        url = url,
    )
}

/**
 * CollectionItem.
 *
 * See https://docs.joinmastodon.org/entities/CollectionItem/
 */
@JsonClass(generateAdapter = true)
data class CollectionItem(
    val id: String,
    @Json(name = "account_id")
    val accountId: String? = null,
    val state: String,
    @Json(name = "created_at")
    val createdAt: Instant,
) {
    fun asModel() = app.pachli.core.model.CollectionItem(
        serverId = id,
        accountId = accountId,
        state = when (state) {
            "pending" -> app.pachli.core.model.CollectionItem.State.PENDING
            "accepted" -> app.pachli.core.model.CollectionItem.State.ACCEPTED
            else -> app.pachli.core.model.CollectionItem.State.UNKNOWN
        },
        createdAt = createdAt,
    )
}

fun Iterable<CollectionItem>.asModel() = map { it.asModel() }

@JsonClass(generateAdapter = true)
data class CollectionWithAccounts(
    val accounts: List<Account>,
    val collection: Collection,
)
