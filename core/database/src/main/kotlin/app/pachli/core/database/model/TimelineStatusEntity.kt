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
import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.DefaultNull
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

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
    // remotekeys
    // TODO: See also core.model.Timeline
    @DefaultNull
    @JsonClass(generateAdapter = true, generator = "sealed:type")
    sealed interface Kind {
        @TypeLabel("home")
        data object Home: Kind

        @TypeLabel("local")
        data object Local: Kind

        @TypeLabel("federated")
        data object Federated: Kind

        // data class RemoteLocal(val serverDomain: String): K ?

        @TypeLabel("hashtag")
        @JsonClass(generateAdapter = true)
        data class Hashtag(val hashtag: String) : Kind

        @TypeLabel("link")
        @JsonClass(generateAdapter = true)
        data class Link(val url: String) : Kind

        @TypeLabel("list")
        @JsonClass(generateAdapter = true)
        data class List(val listId: String) : Kind

        @TypeLabel("direct")
        data object Direct : Kind
    }
}
