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

/**
 * @property quotedStatusId If quoting, the ID of the status being quoted.
 * @property quotePolicy The quote policy that applies to this status.
 */
data class StatusParams(
    val text: String,
    val sensitive: Boolean?,
    val visibility: Status.Visibility,
    val spoilerText: String?,
    val inReplyToId: String?,
    val poll: NewPoll?,
    val language: String? = null,
    val quotedStatusId: String?,
    val quotePolicy: AccountSource.QuotePolicy?,
)
