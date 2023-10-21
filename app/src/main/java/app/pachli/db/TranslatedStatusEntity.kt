/*
 * Copyright 2023 Pachli Association
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

package app.pachli.db

import androidx.room.Entity
import androidx.room.TypeConverters
import app.pachli.entity.Status

/**
 * Translated version of a status.
 *
 * There is *no* foreignkey relationship between this and [TimelineStatusEntity], as the
 * translation data is kept even if the status is deleted from the local cache (e.g., during
 * a refresh operation).
 */
@Entity(
    primaryKeys = ["serverId", "timelineUserId"],
)
@TypeConverters(Converters::class)
data class TranslatedStatusEntity(
    /** ID of the status as it appeared on the original server */
    val serverId: String,

    /** Pachli ID for the logged in user, in case there are multiple accounts per instance */
    val timelineUserId: Long,

    /** The translated text of the status (HTML), equivalent to [Status.content] */
    val content: String,

    /** The service that provided the machine translation */
    val provider: String,
)
