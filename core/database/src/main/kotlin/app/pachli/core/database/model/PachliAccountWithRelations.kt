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

import androidx.room3.Embedded
import androidx.room3.Relation

/**
 * Represents a complete Pachli account.
 *
 * Joins the different tables that make up the account data.
 */
data class PachliAccountWithRelations(
    @Embedded val pachliAccountEntity: PachliAccountEntity,

    @Relation(
        parentColumns = ["pachliAccountId"],
        entityColumns = ["pachliAccountId"],
    )
    val lists: List<MastodonListEntity>?,

    @Relation(
        parentColumns = ["pachliAccountId"],
        entityColumns = ["pachliAccountId"],
    )
    val server: ServerEntity?,

    @Relation(
        parentColumns = ["pachliAccountId"],
        entityColumns = ["pachliAccountId"],
    )
    val contentFilters: ContentFiltersEntity?,

    @Relation(
        parentColumns = ["pachliAccountId"],
        entityColumns = ["pachliAccountId"],
    )
    val announcements: List<AnnouncementEntity>?,

    @Relation(
        parentColumns = ["pachliAccountId"],
        entityColumns = ["pachliAccountId"],
    )
    val following: List<FollowingAccountEntity>,

    @Relation(
        parentColumns = ["pachliAccountId"],
        entityColumns = ["pachliAccountId"],
    )
    val followedHashtags: List<HashtagEntity>,
)
