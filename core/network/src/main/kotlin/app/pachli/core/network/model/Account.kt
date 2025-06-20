/* Copyright 2017 Andrew Dawson
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
import java.util.Date

@JsonClass(generateAdapter = true)
data class Account(
    val id: String,
    /** The username of the account, without the domain */
    @Json(name = "username") val localUsername: String,
    /**
     * The webfinger account URI. Equal to [localUsername] for local users, or
     * [localUsername]@domain for remote users.
     */
    @Json(name = "acct") val username: String,
    // should never be null per API definition, but some servers break the contract
    @Json(name = "display_name") val displayName: String?,
    // should never be null per API definition, but some servers break the contract
    @Json(name = "created_at") val createdAt: Date?,
    val note: String,
    val url: String,
    val avatar: String,
    // Pixelfed might omit `header`
    val header: String = "",
    val locked: Boolean = false,
    @Json(name = "last_status_at") val lastStatusAt: Date? = null,
    @Json(name = "followers_count") val followersCount: Int = 0,
    @Json(name = "following_count") val followingCount: Int = 0,
    @Json(name = "statuses_count") val statusesCount: Int = 0,
    val bot: Boolean = false,
    // nullable for backward compatibility
    val emojis: List<Emoji>? = emptyList(),
    // nullable for backward compatibility
    val fields: List<Field>? = emptyList(),
    val moved: Account? = null,
    val roles: List<Role>? = emptyList(),
) {
    fun asModel(): app.pachli.core.model.Account = app.pachli.core.model.Account(
        id = id,
        localUsername = localUsername,
        username = username,
        displayName = displayName,
        createdAt = createdAt,
        note = note,
        url = url,
        avatar = avatar,
        header = header,
        locked = locked,
        lastStatusAt = lastStatusAt,
        followersCount = followersCount,
        followingCount = followingCount,
        statusesCount = statusesCount,
        bot = bot,
        emojis = emojis?.asModel(),
        fields = fields?.asModel(),
        moved = moved?.asModel(),
        roles = roles?.asModel(),
    )
}

@JsonClass(generateAdapter = true)
data class Field(
    val name: String,
    val value: String,
    @Json(name = "verified_at") val verifiedAt: Date?,
) {
    fun asModel() = app.pachli.core.model.Field(
        name = name,
        value = value,
        verifiedAt = verifiedAt,
    )
}

@JvmName("iterableFieldAsModel")
fun Iterable<Field>.asModel() = map { it.asModel() }

@JsonClass(generateAdapter = true)
data class StringField(
    val name: String,
    val value: String,
) {
    fun asModel() = app.pachli.core.model.StringField(
        name = name,
        value = value,
    )
}

@JvmName("iterableStringFieldAsModel")
fun Iterable<StringField>.asModel() = map { it.asModel() }

/** [Mastodon Entities: Role](https://docs.joinmastodon.org/entities/Role) */
@JsonClass(generateAdapter = true)
data class Role(
    /** Displayable name of the role */
    val name: String,
    /** Colour to use for the role badge, may be the empty string */
    val color: String,
    // Default value is true, since the property may be missing and the observed
    // Mastodon behaviour when it is is to highlight the role. Also, this property
    // being missing breaks InstanceV2 parsing.
    // See https://github.com/mastodon/mastodon/issues/28327
    /** True if the badge should be displayed on the account profile */
    val highlighted: Boolean = true,
) {
    fun asModel() = app.pachli.core.model.Role(
        name = name,
        color = color,
        highlighted = highlighted,
    )
}

@JvmName("iterableRoleAsModel")
fun Iterable<Role>.asModel() = map { it.asModel() }
