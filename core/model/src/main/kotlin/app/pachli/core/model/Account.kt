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

import java.util.Date

data class Account(
    val id: String,
    /** The username of the account, without the domain */
    val localUsername: String,
    /**
     * The webfinger account URI. Equal to [localUsername] for local users, or
     * [localUsername]@domain for remote users.
     */
    val username: String,
    // should never be null per API definition, but some servers break the contract
    val displayName: String?,
    // should never be null per API definition, but some servers break the contract
    val createdAt: Date?,
    val note: String,
    val url: String,
    val avatar: String,
    // Pixelfed might omit `header`
    val header: String = "",
    val locked: Boolean = false,
    val lastStatusAt: Date? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val statusesCount: Int = 0,
    val bot: Boolean = false,
    // nullable for backward compatibility
    val emojis: List<Emoji>? = emptyList(),
    // nullable for backward compatibility
    val fields: List<Field>? = emptyList(),
    val moved: Account? = null,
    val roles: List<Role>? = emptyList(),
) {
    val name: String
        get() = if (displayName.isNullOrEmpty()) {
            localUsername
        } else {
            displayName
        }

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
