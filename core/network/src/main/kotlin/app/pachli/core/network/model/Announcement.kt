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

package app.pachli.core.network.model

import app.pachli.core.network.model.Announcement.AnnouncementStatus
import app.pachli.core.network.model.Announcement.Reaction
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class Announcement(
    val id: String,
    val content: String,
    @Json(name = "starts_at") val startsAt: Date? = null,
    @Json(name = "ends_at") val endsAt: Date? = null,
    @Json(name = "all_day") val allDay: Boolean,
    @Json(name = "published_at") val publishedAt: Date,
    @Json(name = "updated_at") val updatedAt: Date,
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

    fun asModel() = app.pachli.core.model.Announcement(
        id = id,
        content = content,
        startsAt = startsAt,
        endsAt = endsAt,
        allDay = allDay,
        publishedAt = publishedAt,
        updatedAt = updatedAt,
        read = read,
        mentions = mentions.asModel(),
        statuses = statuses.asModel(),
        tags = tags.asModel(),
        emojis = emojis.asModel(),
        reactions = reactions.asModel(),

    )

    @JsonClass(generateAdapter = true)
    data class Reaction(
        val name: String,
        val count: Int,
        val me: Boolean,
        val url: String?,
        @Json(name = "static_url") val staticUrl: String?,
    ) {
        fun asModel() = app.pachli.core.model.Announcement.Reaction(
            name = name,
            count = count,
            me = me,
            url = url,
            staticUrl = staticUrl,
        )
    }

    @JsonClass(generateAdapter = true)
    data class AnnouncementStatus(
        val id: String,
        val url: String,
    ) {
        fun asModel() = app.pachli.core.model.Announcement.AnnouncementStatus(
            id = id,
            url = url,
        )
    }
}

@JvmName("iterableAnnouncementAsModel")
fun Iterable<Announcement>.asModel() = map { it.asModel() }

@JvmName("iterableReactionAsModel")
fun Iterable<Reaction>.asModel() = map { it.asModel() }

@JvmName("iterableAnnouncementStatusAsModel")
fun Iterable<AnnouncementStatus>.asModel() = map { it.asModel() }
