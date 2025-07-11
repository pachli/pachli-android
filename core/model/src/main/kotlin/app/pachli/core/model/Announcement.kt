/* Copyright 2020 Tusky Contributors
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

package app.pachli.core.model

import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class Announcement(
    val id: String,
    val content: String,
    val startsAt: Date? = null,
    val endsAt: Date? = null,
    val allDay: Boolean,
    val publishedAt: Date,
    val updatedAt: Date,
    val read: Boolean,
    val mentions: List<Status.Mention>,
    val statuses: List<AnnouncementStatus>,
    val tags: List<HashTag>,
    val emojis: List<Emoji>,
    val reactions: List<Reaction>,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val announcement = other as Announcement?
        return id == announcement?.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    @JsonClass(generateAdapter = true)
    data class Reaction(
        val name: String,
        val count: Int,
        val me: Boolean,
        val url: String?,
        val staticUrl: String?,
    )

    @JsonClass(generateAdapter = true)
    data class AnnouncementStatus(
        val id: String,
        val url: String,
    )
}
