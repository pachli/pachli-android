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

package app.pachli.entity

import com.google.gson.annotations.SerializedName

/** https://docs.joinmastodon.org/entities/Translation/ */
data class Translation(
    /** The translated text of the status (HTML), equivalent to [Status.content] */
    val content: String,

    /**
     * The language of the source text, as auto-detected by the machine translation
     * (ISO 639 language code)
     */
    @SerializedName("detected_source_language") val detectedSourceLanguage: String,

    /** The service that provided the machine translation */
    val provider: String,
)
