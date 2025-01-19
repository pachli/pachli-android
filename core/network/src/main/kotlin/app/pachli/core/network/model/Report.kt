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

package app.pachli.core.network.model

import app.pachli.core.network.json.Default
import app.pachli.core.network.json.HasDefault
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class Report(
    val id: String,
    val category: Category,
    val actionTaken: Boolean,
    val actionTakenAt: Instant,
    val comment: String,
    val forwarded: Boolean,
    @Json(name = "status_ids") val statusIds: List<String>?,
    @Json(name = "created_at") val createdAt: Instant,
    @Json(name = "rule_ids") val ruleIds: List<String>?,
    @Json(name = "target_account") val targetAccount: TimelineAccount,
) {
    @HasDefault
    enum class Category {
        /** Unwanted or repetitive content. */
        @Json(name = "spam")
        SPAM,

        /** A specific rule was violated. */
        @Json(name = "violation")
        VIOLATION,

        /** Some other reason. */
        @Json(name = "other")
        @Default
        OTHER,
    }
}
