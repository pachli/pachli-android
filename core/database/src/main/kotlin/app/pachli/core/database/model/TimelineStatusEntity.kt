/*
 * Copyright (c) 2025 Pachli Association
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
import androidx.room.TypeConverters
import app.pachli.core.database.Converters

/**
 * A timeline that contains items.
 */
@Entity(
    primaryKeys = ["kind", "pachliAccountId", "statusId"],
)
@TypeConverters(Converters::class)
data class TimelineStatusEntity(
    val kind: Kind,
    val pachliAccountId: Long,
    val statusId: String,
) {
    /** Cacheable timeline kinds. */
    // TODO: Eventually these have to be the timeline kind types for
    // RemoteKeyEntity too.
    sealed interface Kind {
        data object Home: Kind
        data object Local: Kind
        data object Federated: Kind
        // data class RemoteLocal(val serverDomain: String): K ?
        data class Hashtag(val hashtag: String) : Kind
        data class Link(val url: String) : Kind
        data class List(val listId: String) : Kind
        data object Direct : Kind
    }
}
