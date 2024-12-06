/*
 * Copyright 2024 Pachli Association
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

/** Reasons an account may be filtered. */
enum class AccountFilterReason {
    /** Not following this account. */
    NOT_FOLLOWING,

    /** Account is younger than 30d. */
    YOUNGER_30D,

    /** Account is limited by the server. */
    LIMITED_BY_SERVER,
}

/**
 * Records an account filtering decision.
 *
 * @param action The [FilterAction] to perform.
 * @param reason The [AccountFilterReason] for the decision.
 */
@JsonClass(generateAdapter = true)
data class AccountFilterDecision(
    val action: FilterAction,
    val reason: AccountFilterReason,
)
