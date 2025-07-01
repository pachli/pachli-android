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

import java.util.Date

/**
 * A snapshot of a status from its edit history
 *
 * See [StatusEdit](https://docs.joinmastodon.org/entities/StatusEdit/)
 */
data class StatusEdit(
    val content: String,
    val spoilerText: String,
    val sensitive: Boolean,
    val createdAt: Date,
    val account: TimelineAccount,
    val poll: PollEdit?,
    val mediaAttachments: List<Attachment>,
    val emojis: List<Emoji>,
)

/**
 * A snapshot of a poll from a status' edit history.
 */
data class PollEdit(
    val options: List<PollOptionEdit>,
)

/**
 * A snapshot of a poll option from a status' edit history.
 */
data class PollOptionEdit(
    val title: String,
)
