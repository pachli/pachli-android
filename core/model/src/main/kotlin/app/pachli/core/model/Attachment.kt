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

package app.pachli.core.model

import android.os.Parcelable
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonClass(generateAdapter = true)
data class Attachment(
    val id: String,
    val url: String,
    // can be null for e.g. audio attachments
    val previewUrl: String?,
    val meta: MetaData?,
    val type: Type,
    val description: String?,
    val blurhash: String?,
) : Parcelable {

    enum class Type {
        IMAGE,
        GIFV,
        VIDEO,
        AUDIO,
        UNKNOWN,
    }

    /**
     * The meta data of an [Attachment].
     */
    @Parcelize
    @JsonClass(generateAdapter = true)
    data class MetaData(
        // Fields in Focus may be null, see https://github.com/mastodon/mastodon/issues/29222
        val focus: Focus?,
        val duration: Float?,
        val original: Size?,
        val small: Size?,
    ) : Parcelable

    /**
     * The Focus entity, used to specify the focal point of an image.
     *
     * See here for more details what the x and y mean:
     *   https://github.com/jonom/jquery-focuspoint#1-calculate-your-images-focus-point
     */
    @Parcelize
    @JsonClass(generateAdapter = true)
    data class Focus(
        val x: Float = 0f,
        val y: Float = 0f,
    ) : Parcelable {
        fun toMastodonApiString(): String = "$x,$y"
    }

    /**
     * The size of an image, used to specify the width/height.
     */
    @Parcelize
    @JsonClass(generateAdapter = true)
    data class Size(
        val width: Int?,
        val height: Int?,
        val aspect: Double?,
    ) : Parcelable

    /**
     * @return True if this attachment can be previewed. A previewable attachment
     *     must be a known type and have a non-null width for the preview image.
     */
    fun isPreviewable(): Boolean {
        if (type == Type.UNKNOWN) return false
        return !(meta?.original?.width == null && meta?.small?.width == null)
    }
}
