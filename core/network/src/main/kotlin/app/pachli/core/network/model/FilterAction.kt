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

package app.pachli.core.network.model

import app.pachli.core.network.json.Default
import app.pachli.core.network.json.HasDefault
import com.squareup.moshi.Json

/** A filter choice, either content filter or account filter. */
@HasDefault
enum class FilterAction {
    /** No filtering, show item as normal. */
    @Json(name = "none")
    NONE,

    /** Replace the item with a warning, allowing the user to click through. */
    @Default
    @Json(name = "warn")
    WARN,

    /** Remove the item, with no indication to the user it was present. */
    @Json(name = "hide")
    HIDE,
}
