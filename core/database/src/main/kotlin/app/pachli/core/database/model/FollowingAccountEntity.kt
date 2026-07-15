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

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import app.pachli.core.model.ITimelineAccount

/**
 * An account the user is following.
 *
 * @property pachliAccountId ID of the local account that is following this account.
 * @property accountId Server's identifier for the account. Unique within a single server,
 * but not unique across the federated network.
 * @property domain The domain of the account identified by [accountId]. Allows for bulk
 * delete of all relationships on a particular domain, if the user blocks the domain.
 */
@Entity(
    primaryKeys = ["pachliAccountId", "accountId"],
    foreignKeys = [
        ForeignKey(
            entity = PachliAccountEntity::class,
            parentColumns = ["pachliAccountId"],
            childColumns = ["pachliAccountId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
)
data class FollowingAccountEntity(
    val pachliAccountId: Long,
    val accountId: String,
    @ColumnInfo(defaultValue = "")
    val domain: String,
) {
    companion object {
        fun from(pachliAccountId: Long, account: ITimelineAccount) = FollowingAccountEntity(
            pachliAccountId = pachliAccountId,
            accountId = account.accountId,
            domain = account.domain,
        )
    }
}
