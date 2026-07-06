/*
 * Copyright 2017 Andrew Dawson
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
 * Interface for anything that implements the minimum subset of [Account]
 * information that [TimelineAccount] does.
 *
 * @property serverId
 * @property localUsername The username of the account, without the domain.
 * @property username The webfinger account URI. Equal to [localUsername] for local users, or
 * [localUsername]@domain for remote users.
 * @property displayName
 * @property url
 * @property avatar
 * @property bot
 * @property emojis
 * @property createdAt When the account was created, if known. May not be
 * the exact time, the server may clamp it e.g., to midnight.
 * @property limited If true, indicates that the account should be hidden
 * behind a warning.
 * @property roles Roles associated with this account on this server.
 * @property pronouns Optional pronouns, derived from the account's fields.
 * @property name The account's [displayName], falling back to [localUsername] if
 * [displayName] is null or empty.
 * @property domain The account's domain (excluding the `@`), empty string if
 * the account is local.
 */
interface ITimelineAccount {
    val serverId: String
    val localUsername: String
    val username: String

    @Deprecated("prefer the `name` property, which is not empty")
    val displayName: String
    val url: String
    val avatar: String
    val bot: Boolean

    // nullable for backward compatibility
    val emojis: List<Emoji>

    // Should never be null per API definition, but some servers break the contract
    val createdAt: Instant?
    val limited: Boolean
    val roles: List<Role>
    val pronouns: String?

    @Suppress("DEPRECATION")
    val name: String
        get() = displayName.ifEmpty { localUsername }

    val domain: String
        get() {
            return username.indexOf('@').takeIf { it != -1 }?.let { index ->
                username.substring(index + 1)
            } ?: ""
        }
}

/**
 * A specialised version of [Account], with only the properties
 * required to show an account in a timeline.
 *
 * Prefer this class over [Account] because it requires less memory
 * and deserialises faster.
 */
@JsonClass(generateAdapter = true)
data class TimelineAccount(
    override val serverId: String,
    override val localUsername: String,
    override val username: String,

    @Deprecated("prefer the `name` property, which is not empty")
    override val displayName: String,
    override val url: String,
    override val avatar: String,
    override val bot: Boolean,
    override val emojis: List<Emoji> = emptyList(),
    override val createdAt: Instant?,
    override val limited: Boolean,
    override val roles: List<Role>,
    override val pronouns: String?,
) : ITimelineAccount

fun Account.asTimelineAccount() = TimelineAccount(
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
    roles = roles,
    pronouns = pronouns,
)

fun Iterable<Account>.asTimelineAccount() = map { it.asTimelineAccount() }
