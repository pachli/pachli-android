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

import android.os.Parcelable
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

/**
 * A custom emoji. Either available on the user's server, or associated with
 * data returned from the server.
 *
 * @property shortcode The emoji's shortcode -- this is the name of the
 * emoji enclosed in `:`.
 * @property url URL of the image to show as the emoji.
 * @property staticUrl As [url], but does not animate.
 * @param visibleInPicker True if the emoji should be visible to user when
 * picking emojis.
 * @param category Arbitrary category the emoji is a member of. Not localised.
 */
@Parcelize
@JsonClass(generateAdapter = true)
data class Emoji(
    val shortcode: String,
    val url: String,
    val staticUrl: String,
    val visibleInPicker: Boolean?,
    val category: String? = null,
) : Parcelable
