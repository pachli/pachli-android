/* Copyright 2019 kyori19
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

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class ScheduledStatus(
    val id: String,
    @Json(name = "scheduled_at") val scheduledAt: Date,
    val params: StatusParams,
    @Json(name = "media_attachments") val mediaAttachments: List<Attachment>,
) {
    fun asModel() = app.pachli.core.model.ScheduledStatus(
        id = id,
        scheduledAt = scheduledAt,
        params = params.asModel(),
        mediaAttachments = mediaAttachments.asModel(),
    )
}

fun Iterable<ScheduledStatus>.asModel() = map { it.asModel() }
