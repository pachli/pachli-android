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

package app.pachli.core.network.model

import app.pachli.core.network.json.Default
import app.pachli.core.network.json.HasDefault
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

/**
 * Summary of a moderation or block event that caused follow relationships to be severed.
 */
@JsonClass(generateAdapter = true)
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
    @Json(name = "target_name")
    val targetName: String,

    /** Number of follower accounts removed. */
    @Json(name = "followers_count")
    val followersCount: Int = 0,

    /** Number of followed accounts removed. */
    @Json(name = "following_count")
    val followingCount: Int = 0,

    /** When the event took place. */
    @Json(name = "created_at")
    val createdAt: Instant,
) {
    @HasDefault
    enum class Type {
        /** A moderator suspended a whole domain */
        @Json(name = "domain_block")
        DOMAIN_BLOCK,

        /** The user blocked a whole domain */
        @Json(name = "user_domain_block")
        USER_DOMAIN_BLOCK,

        /** A moderator suspended a specific account */
        @Json(name = "account_suspension")
        ACCOUNT_SUSPENSION,

        @Default
        UNKNOWN,

        ;

        fun asModel(): app.pachli.core.model.RelationshipSeveranceEvent.Type = when (this) {
            DOMAIN_BLOCK -> app.pachli.core.model.RelationshipSeveranceEvent.Type.DOMAIN_BLOCK
            USER_DOMAIN_BLOCK -> app.pachli.core.model.RelationshipSeveranceEvent.Type.USER_DOMAIN_BLOCK
            ACCOUNT_SUSPENSION -> app.pachli.core.model.RelationshipSeveranceEvent.Type.ACCOUNT_SUSPENSION
            UNKNOWN -> app.pachli.core.model.RelationshipSeveranceEvent.Type.UNKNOWN
        }
    }

    fun asModel() = app.pachli.core.model.RelationshipSeveranceEvent(
        id = id,
        type = type.asModel(),
        purged = purged,
        targetName = targetName,
        followersCount = followersCount,
        followingCount = followingCount,
        createdAt = createdAt,
    )
}
