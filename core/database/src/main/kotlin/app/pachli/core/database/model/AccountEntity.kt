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
import androidx.room.TypeConverters
import app.pachli.core.database.Converters
import app.pachli.core.model.Account
import app.pachli.core.model.Emoji
import app.pachli.core.model.Field
import app.pachli.core.model.MovedAccount
import app.pachli.core.model.Role
import java.time.Instant
import java.util.Date

/**
 * An account associated with a status on a timeline or similar (e.g., an
 * account the user is following).
 *
 * @property pachliAccountId The pachliAccountId for the logged-in account related
 * to this account.
 * @property serverId
 * @property localUsername
 * @property username
 * @property displayName
 * @property url
 * @property avatar
 * @property emojis
 * @property bot
 * @property createdAt
 * @property note (HTML) The profile’s bio or description.
 */
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
data class AccountEntity(
    val pachliAccountId: Long,
    val serverId: String,
    val localUsername: String,
    val username: String,
    val displayName: String,
    val createdAt: Instant?,
    val url: String,
    val avatar: String,
    val note: String,
    @ColumnInfo(defaultValue = "")
    val header: String,
    @ColumnInfo(defaultValue = "0")
    val locked: Boolean,
    val lastStatusAt: Date?,
    @ColumnInfo(defaultValue = "0")
    val followersCount: Int,
    @ColumnInfo(defaultValue = "0")
    val followingCount: Int,
    @ColumnInfo(defaultValue = "0")
    val statusesCount: Int,
    val bot: Boolean,
    val emojis: List<Emoji>,
    @ColumnInfo(defaultValue = "")
    val fields: List<Field>,
    val movedAccount: MovedAccount?,
    val limited: Boolean,
    val roles: List<Role>,
    val pronouns: String?,
) {
    fun asModel() = Account(
        serverId = serverId,
        localUsername = localUsername,
        username = username,
        displayName = displayName,
        createdAt = createdAt,
        url = url,
        avatar = avatar,
        note = note,
        header = header,
        locked = locked,
        lastStatusAt = lastStatusAt,
        followersCount = followersCount,
        followingCount = followingCount,
        statusesCount = statusesCount,
        bot = bot,
        emojis = emojis,
        fields = fields,
        movedAccount = movedAccount,
        limited = limited,
        roles = roles,
        pronouns = pronouns,
    )
}

fun Account.asEntity(pachliAccountId: Long) = AccountEntity(
    pachliAccountId = pachliAccountId,
    serverId = serverId,
    localUsername = localUsername,
    username = username,
    displayName = displayName,
    createdAt = createdAt,
    url = url,
    avatar = avatar,
    note = note,
    header = header,
    locked = locked,
    lastStatusAt = lastStatusAt,
    followersCount = followersCount,
    followingCount = followingCount,
    statusesCount = statusesCount,
    bot = bot,
    emojis = emojis,
    fields = fields,
    movedAccount = movedAccount,
    limited = limited,
    roles = roles,
    pronouns = pronouns,
)

fun Iterable<Account>.asEntity(pachliAccountId: Long) = map { it.asEntity(pachliAccountId) }
