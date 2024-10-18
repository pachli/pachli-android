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

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Represents a complete Pachli account.
 *
 * Joins the different tables that make up the account data.
 */
data class PachliAccount(
    @Embedded val account: AccountEntity,

    @Relation(
        parentColumn = "domain",
        entityColumn = "instance",
    )
    val instanceInfo: InstanceInfoEntity?,

    @Relation(
        parentColumn = "id",
        entityColumn = "accountId",
    )
    val lists: List<MastodonListEntity>?,

    @Relation(
        parentColumn = "id",
        entityColumn = "accountId",
    )
    val emojis: EmojisEntity?,

    @Relation(
        parentColumn = "id",
        entityColumn = "accountId",
    )
    val server: ServerEntity?,

    @Relation(
        parentColumn = "id",
        entityColumn = "accountId",
    )
    val contentFilters: ContentFiltersEntity?,

    @Relation(
        parentColumn = "id",
        entityColumn = "accountId",
    )
    val announcements: List<AnnouncementEntity>?,
)
