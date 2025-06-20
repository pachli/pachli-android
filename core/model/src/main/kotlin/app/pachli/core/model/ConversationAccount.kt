/*
 * Copyright 2025 Pachli Association
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
import java.time.Instant

/**
 * Participants in a [app.pachli.core.database.model.ConversationData].
 */
@JsonClass(generateAdapter = true)
data class ConversationAccount(
    val id: String,
    val localUsername: String,
    val username: String,
    val displayName: String,
    val avatar: String,
    val emojis: List<Emoji>,
    val createdAt: Instant?,
)

fun TimelineAccount.asConversationAccount() = ConversationAccount(
    id = id,
    localUsername = localUsername,
    username = username,
    displayName = name,
    avatar = avatar,
    emojis = emojis.orEmpty(),
    createdAt = createdAt,
)

fun Iterable<TimelineAccount>.asConversationAccount() = map { it.asConversationAccount() }
