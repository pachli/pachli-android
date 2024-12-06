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
import dev.zacsweers.moshix.sealed.annotations.DefaultObject
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

/** Reasons an account may be filtered. */
enum class AccountFilterReason {
    /** Not following this account. */
    NOT_FOLLOWING,

    /** Account is younger than 30d. */
    YOUNGER_30D,

    /** Account is limited by the server. */
    LIMITED_BY_SERVER,
}

// The user's filter choice (enum):
// - none, warn, hide

// The active filter decision, per notification (sealed class)
// - none, warn+reason, hide+reason, override+original
//
// Why? So you can't have "none" with a reason, or "warn" without a reason

/**
 * Records an account filtering decision.
 *
 * @param action The [FilterAction] to perform.
 * @param reason The [AccountFilterReason] for the decision.
 */
// @JsonClass(generateAdapter = true)
// data class AccountFilterDecision_org(
//    val action: FilterAction,
//    val reason: AccountFilterReason,
// )

@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed interface AccountFilterDecision {
    /** The item did not match any filters, and should be shown. */
    @DefaultObject
    data object None : AccountFilterDecision

    /** The item matched a [reason] set to "warn". */
    @TypeLabel("warn")
    @JsonClass(generateAdapter = true)
    data class Warn(val reason: AccountFilterReason) : AccountFilterDecision

    /** The item matched a [reason] set to "hide". */
    @TypeLabel("hide")
    @JsonClass(generateAdapter = true)
    data class Hide(val reason: AccountFilterReason) : AccountFilterDecision

    /**
     * The item was originally filtered because of [original], the user has overriden
     * that to view the item anyway.
     */
    @TypeLabel("override")
    @JsonClass(generateAdapter = true)
    data class Override(val original: AccountFilterDecision) : AccountFilterDecision

    companion object {
        fun make(action: FilterAction, reason: AccountFilterReason) = when (action) {
            FilterAction.NONE -> None
            FilterAction.WARN -> Warn(reason)
            FilterAction.HIDE -> Hide(reason)
        }
    }
}
