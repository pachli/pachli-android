/*
 * Copyright 2017 Andrew Dawson
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

/**
 * Same as [Account], but only with the attributes required in timelines.
 * Prefer this class over [Account] because it uses way less memory & deserializes faster from json.
 */
@JsonClass(generateAdapter = true)
data class TimelineAccount(
    val id: String,
    @Json(name = "username") val localUsername: String,
    @Json(name = "acct") val username: String,
    // should never be null per Api definition, but some servers break the contract
    @Deprecated("prefer the `name` property, which is not-null and not-empty")
    @Json(name = "display_name") val displayName: String?,
    val url: String,
    val avatar: String,
    val note: String,
    val bot: Boolean = false,
    // nullable for backward compatibility
    val emojis: List<Emoji>? = emptyList(),
) {

    @Suppress("DEPRECATION")
    val name: String
        get() = if (displayName.isNullOrEmpty()) {
            localUsername
        } else {
            displayName
        }
}
