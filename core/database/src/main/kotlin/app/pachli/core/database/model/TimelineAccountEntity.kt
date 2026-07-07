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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverters
import app.pachli.core.database.Converters
import app.pachli.core.model.Emoji
import app.pachli.core.model.Role
import app.pachli.core.model.TimelineAccount
import java.time.Instant

/**
 * An account associated with a status on a timeline or similar (e.g., an
 * account the user is following).
 *
 * This should contain just the information necessary to display an
 * account on a general timeline (e.g., of statuses or notifications).
 *
 * Areas that need more data should use [AccountEntity] instead.
 *
 * @property serverId
 * @property pachliAccountId The pachliAccountId for the logged-in account related
 * to this account.
 * @property localUsername
 * @property username
 * @property displayName
 * @property url
 * @property avatar
 * @property emojis
 * @property bot
 * @property createdAt
 */
@Entity(
    primaryKeys = ["serverId", "pachliAccountId"],
    foreignKeys = [
        ForeignKey(
            entity = PachliAccountEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("pachliAccountId"),
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index(value = ["pachliAccountId"])],
)
@TypeConverters(Converters::class)
data class TimelineAccountEntity(
    val serverId: String,
    val pachliAccountId: Long,
    val localUsername: String,
    val username: String,
    val displayName: String,
    val url: String,
    val avatar: String,
    val emojis: List<Emoji>,
    val bot: Boolean,
    val createdAt: Instant?,
    @ColumnInfo(defaultValue = "false")
    val limited: Boolean = false,
    @ColumnInfo(defaultValue = "")
    val roles: List<Role>?,
    @ColumnInfo(defaultValue = "")
    val pronouns: String?,
) {
    fun asModel() = TimelineAccount(
        serverId = serverId,
        localUsername = localUsername,
        username = username,
        displayName = displayName,
        url = url,
        avatar = avatar,
        bot = bot,
        emojis = emojis,
        createdAt = createdAt,
        limited = limited,
        roles = roles.orEmpty(),
        pronouns = pronouns,
    )
}

fun TimelineAccount.asEntity(pachliAccountId: Long) = TimelineAccountEntity(
    serverId = serverId,
    pachliAccountId = pachliAccountId,
    localUsername = localUsername,
    username = username,
    displayName = name,
    url = url,
    avatar = avatar,
    emojis = emojis.orEmpty(),
    bot = bot,
    createdAt = createdAt,
    limited = limited,
    roles = roles,
    pronouns = pronouns,
)

fun Iterable<TimelineAccount>.asEntity(pachliAccountId: Long) = map { it.asEntity(pachliAccountId) }
