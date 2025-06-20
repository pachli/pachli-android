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

import app.pachli.core.network.json.Default
import app.pachli.core.network.json.DefaultIfNull
import app.pachli.core.network.json.HasDefault
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Attachment(
    val id: String,
    val url: String,
    // can be null for e.g. audio attachments
    @Json(name = "preview_url") val previewUrl: String?,
    val meta: MetaData?,
    val type: Type,
    val description: String?,
    val blurhash: String?,
) {

    @HasDefault
    enum class Type {
        @Json(name = "image")
        IMAGE,

        @Json(name = "gifv")
        GIFV,

        @Json(name = "video")
        VIDEO,

        @Json(name = "audio")
        AUDIO,

        @Json(name = "unknown")
        @Default
        UNKNOWN,

        ;

        fun asModel() = when (this) {
            IMAGE -> app.pachli.core.model.Attachment.Type.IMAGE
            GIFV -> app.pachli.core.model.Attachment.Type.GIFV
            VIDEO -> app.pachli.core.model.Attachment.Type.VIDEO
            AUDIO -> app.pachli.core.model.Attachment.Type.AUDIO
            UNKNOWN -> app.pachli.core.model.Attachment.Type.UNKNOWN
        }
    }

    fun asModel() = app.pachli.core.model.Attachment(
        id = id,
        url = url,
        previewUrl = previewUrl,
        meta = meta?.asModel(),
        type = type.asModel(),
        description = description,
        blurhash = blurhash,
    )

    /**
     * The meta data of an [Attachment].
     */
    @JsonClass(generateAdapter = true)
    data class MetaData(
        // Fields in Focus may be null, see https://github.com/mastodon/mastodon/issues/29222
        @DefaultIfNull
        val focus: Focus?,
        val duration: Float?,
        val original: Size?,
        val small: Size?,
    ) {
        fun asModel() = app.pachli.core.model.Attachment.MetaData(
            focus = focus?.asModel(),
            duration = duration,
            original = original?.asModel(),
            small = small?.asModel(),
        )
    }

    /**
     * The Focus entity, used to specify the focal point of an image.
     *
     * See here for more details what the x and y mean:
     *   https://github.com/jonom/jquery-focuspoint#1-calculate-your-images-focus-point
     */
    @JsonClass(generateAdapter = true)
    data class Focus(
        val x: Float = 0f,
        val y: Float = 0f,
    ) {
        fun asModel() = app.pachli.core.model.Attachment.Focus(
            x = x,
            y = y,
        )
    }

    /**
     * The size of an image, used to specify the width/height.
     */
    @JsonClass(generateAdapter = true)
    data class Size(
        val width: Int?,
        val height: Int?,
        // Not always present, see https://github.com/mastodon/mastodon/issues/29125
        val aspect: Double?,
    ) {
        fun asModel() = app.pachli.core.model.Attachment.Size(
            width = width,
            height = height,
            aspect = run {
                if (aspect != null) return@run aspect
                width ?: return@run 1.778
                height ?: return@run 1.778

                return@run (width / height).toDouble()
            },
        )
    }
}

fun Iterable<Attachment>.asModel() = map { it.asModel() }
