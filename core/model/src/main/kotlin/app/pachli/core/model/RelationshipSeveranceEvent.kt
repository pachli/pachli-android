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

package app.pachli.core.model

import java.time.Instant

/**
 * Summary of a moderation or block event that caused follow relationships to be severed.
 */
data class RelationshipSeveranceEvent(
    /** The ID of the relationship severance event in the database. */
    val id: String,
    /** Type of event. */
    val type: Type = Type.UNKNOWN,
    /**
     * True if the list of severed relationships is unavailable because the underlying
     * issue has been purged.
     */
    val purged: Boolean,

    /**
     * Name of the target of the moderation/block event. This is either a domain name or
     * a user handle, depending on the event type.
     */
    val targetName: String,

    /** Number of follower accounts removed. */
    val followersCount: Int = 0,

    /** Number of followed accounts removed. */
    val followingCount: Int = 0,

    /** When the event took place. */
    val createdAt: Instant,
) {
    enum class Type {
        /** A moderator suspended a whole domain */
        DOMAIN_BLOCK,

        /** The user blocked a whole domain */
        USER_DOMAIN_BLOCK,

        /** A moderator suspended a specific account */
        ACCOUNT_SUSPENSION,

        UNKNOWN,
    }
}
