/* Copyright 2019 Conny Duck
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

@JsonClass(generateAdapter = true)
data class Conversation(
    val id: String,
    val accounts: List<TimelineAccount>,
    // should never be null, but apparently its possible https://github.com/tuskyapp/Tusky/issues/1038
    @Json(name = "last_status") val lastStatus: Status?,
    val unread: Boolean,
) {
    fun asModel() = app.pachli.core.model.Conversation(
        id = id,
        accounts = accounts.asModel(),
        lastStatus = lastStatus?.asModel(),
        unread = unread,
    )
}

@JvmName("iterableConversationAsModel")
fun Iterable<Conversation>.asModel() = map { it.asModel() }
