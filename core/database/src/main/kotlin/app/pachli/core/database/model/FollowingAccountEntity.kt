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
import app.pachli.core.network.model.TimelineAccount

/**
 * An account the user is following.
 *
 * @param pachliAccountId ID of the local account that is following this account.
 * @param serverId Server's identifier for the account. Unique within a single server,
 * but not unique across the federated network.
 */
@Entity(primaryKeys = ["pachliAccountId", "serverId"])
data class FollowingAccountEntity(
    val pachliAccountId: Long,
    val serverId: String,
) {
    companion object {
        fun from(pachliAccountId: Long, timelineAccount: TimelineAccount) = FollowingAccountEntity(
            pachliAccountId = pachliAccountId,
            serverId = timelineAccount.id,
        )
    }
}
