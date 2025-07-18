/* Copyright 2018 Conny Duck
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
data class Emoji(
    val shortcode: String,
    val url: String,
    @Json(name = "static_url") val staticUrl: String,
    @Json(name = "visible_in_picker") val visibleInPicker: Boolean?,
    val category: String? = null,
) {
    fun asModel() = app.pachli.core.model.Emoji(
        shortcode = shortcode,
        url = url,
        staticUrl = staticUrl,
        visibleInPicker = visibleInPicker,
        category = category,
    )
}

fun Iterable<Emoji>.asModel() = map { it.asModel() }
