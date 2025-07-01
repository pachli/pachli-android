/*
 * Copyright 2024 Pachli Association
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

import androidx.room.Entity
import androidx.room.ForeignKey
import app.pachli.core.model.MastodonList
import app.pachli.core.model.UserListRepliesPolicy

/**
 * Represents a Mastodon list definition.
 *
 * Does not include details about the lists's membership.
 *
 * Each list is associated with exactly one [AccountEntity] through the [accountId]
 * property.
 */
@Entity(
    primaryKeys = ["accountId", "listId"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("accountId"),
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
)
data class MastodonListEntity(
    val accountId: Long,
    val listId: String,
    val title: String,
    val repliesPolicy: UserListRepliesPolicy,
    val exclusive: Boolean,
) {
    fun asModel() = MastodonList(
        listId = listId,
        title = title,
        repliesPolicy = repliesPolicy,
        exclusive = exclusive,
    )
}

fun Iterable<MastodonListEntity>.asModel() = map { it.asModel() }

fun MastodonList.asEntity(pachliAccountId: Long) = MastodonListEntity(
    accountId = pachliAccountId,
    listId = listId,
    title = title,
    repliesPolicy = repliesPolicy,
    exclusive = exclusive,
)

fun Iterable<MastodonList>.asEntity(pachliAccountId: Long) = map { it.asEntity(pachliAccountId) }
