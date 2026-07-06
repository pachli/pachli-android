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

package app.pachli.core.model

import com.squareup.moshi.JsonClass
import java.time.Instant
import java.util.Date

/**
 * @property note (HTML) The profile’s bio or description.
 */
data class Account(
    override val serverId: String,
    override val localUsername: String,
    override val username: String,
    // should never be null per API definition, but some servers break the contract
    @Deprecated("prefer the `name` property, which is not-null and not-empty")
    override val displayName: String?,
    // should never be null per API definition, but some servers break the contract
    override val createdAt: Instant?,
    override val note: String,
    override val url: String,
    override val avatar: String,
    // Pixelfed might omit `header`
    val header: String = "",
    val locked: Boolean = false,
    val lastStatusAt: Date? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val statusesCount: Int = 0,
    override val bot: Boolean = false,
    // nullable for backward compatibility
    override val emojis: List<Emoji>? = emptyList(),
    // nullable for backward compatibility
    val fields: List<Field>? = emptyList(),
    val moved: Account? = null,
    override val limited: Boolean,
    override val roles: List<Role>,
    override val pronouns: String?,
) : ITimelineAccount {
    fun isRemote(): Boolean = this.username != this.localUsername
}

data class Field(
    val name: String,
    val value: String,
    val verifiedAt: Date?,
)

data class StringField(
    val name: String,
    val value: String,
)

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
)
