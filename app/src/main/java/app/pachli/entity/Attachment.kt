/* Copyright 2017 Andrew Dawson
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
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package app.pachli.entity

import android.os.Parcelable
import androidx.annotation.DrawableRes
import app.pachli.R
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Attachment(
    val id: String,
    val url: String,
    @SerializedName("preview_url") val previewUrl: String?, // can be null for e.g. audio attachments
    val meta: MetaData?,
    val type: Type,
    val description: String?,
    val blurhash: String?,
) : Parcelable {

    @JsonAdapter(MediaTypeDeserializer::class)
    enum class Type {
        @SerializedName("image")
        IMAGE,

        @SerializedName("gifv")
        GIFV,

        @SerializedName("video")
        VIDEO,

        @SerializedName("audio")
        AUDIO,

        @SerializedName("unknown")
        UNKNOWN,
    }

    /** @return a drawable resource for an icon to indicate the attachment type */
    @DrawableRes
    fun iconResource() = when (this.type) {
        Type.IMAGE -> R.drawable.ic_photo_24dp
        Type.GIFV, Type.VIDEO -> R.drawable.ic_videocam_24dp
        Type.AUDIO -> R.drawable.ic_music_box_24dp
        Type.UNKNOWN -> R.drawable.ic_attach_file_24dp
    }

    class MediaTypeDeserializer : JsonDeserializer<Type> {
        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, classOfT: java.lang.reflect.Type, context: JsonDeserializationContext): Type {
            return when (json.toString()) {
                "\"image\"" -> Type.IMAGE
                "\"gifv\"" -> Type.GIFV
                "\"video\"" -> Type.VIDEO
                "\"audio\"" -> Type.AUDIO
                else -> Type.UNKNOWN
            }
        }
    }

    /**
     * The meta data of an [Attachment].
     */
    @Parcelize
    data class MetaData(
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
    data class Focus(
        val x: Float,
        val y: Float,
    ) : Parcelable {
        fun toMastodonApiString(): String = "$x,$y"
    }

    /**
     * The size of an image, used to specify the width/height.
     */
    @Parcelize
    data class Size(
        val width: Int,
        val height: Int,
        val aspect: Double,
    ) : Parcelable
}
