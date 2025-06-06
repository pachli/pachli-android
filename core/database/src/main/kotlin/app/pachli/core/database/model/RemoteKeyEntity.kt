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

package app.pachli.core.database.model

import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * The remote keys for the given timeline, see [RemoteKeyKind].
 */
@Entity(
    primaryKeys = ["accountId", "timelineId", "kind"],
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
data class RemoteKeyEntity(
    /** User account these keys relate to. */
    val accountId: Long,
    /**
     * Identifier for the timeline these keys relate to.
     *
     * See [Timeline.refreshKeyPrimaryKey][app.pachli.core.model.Timeline.remoteKeyTimelineId].
     */
    val timelineId: String,
    val kind: RemoteKeyKind,
    val key: String? = null,
) {
    /** Kinds of remote keys that can be stored for a timeline. */
    enum class RemoteKeyKind {
        /** Key to load the next (chronologically oldest) page of data for this timeline */
        NEXT,

        /** Key to load the previous (chronologically newer) page of data for this timeline */
        PREV,

        /**
         * ID of the last notification the user read on the Notification list, and should
         * be restored to view when the user returns to the list.
         *
         * May not be the ID of the most recent notification if the user has scrolled down
         * the list.
         */
        REFRESH,
    }
}
