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

package app.pachli.core.model

import java.time.Instant

data class Report(
    val id: String,
    val category: Category,
    val actionTaken: Boolean,
    val actionTakenAt: Instant?,
    val comment: String,
    val forwarded: Boolean = false,
    val statusIds: List<String>?,
    val createdAt: Instant,
    val ruleIds: List<String>?,
    val targetAccount: TimelineAccount,
) {
    enum class Category {
        /** Unwanted or repetitive content. */
        SPAM,

        /** A specific rule was violated. */
        VIOLATION,

        /** Some other reason. */
        OTHER,
    }
}
