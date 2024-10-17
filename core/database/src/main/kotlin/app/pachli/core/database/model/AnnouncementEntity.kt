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
import androidx.room.ForeignKey
import androidx.room.TypeConverters
import app.pachli.core.database.Converters
import app.pachli.core.network.model.Announcement

/**
 * Represents the announcements sent to an account.
 *
 * Although announcements are also associated with a server, the user may have
 * multiple accounts on the same server with announcements that are in different
 * "read" states. So each announcement is associated with exactly one
 * [AccountEntity] through the [accountId] property.
 */
@Entity(
    primaryKeys = ["accountId"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("accountId"),
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
)
@TypeConverters(Converters::class)
data class AnnouncementEntity(
    val accountId: Long,
    val announcementId: String,
    val announcement: Announcement,
)
