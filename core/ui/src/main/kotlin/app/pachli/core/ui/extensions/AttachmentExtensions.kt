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

package app.pachli.core.ui.extensions

import android.content.Context
import androidx.annotation.DrawableRes
import app.pachli.core.model.Attachment
import app.pachli.core.ui.R
import kotlin.math.roundToInt

/** @return a drawable resource for an icon to indicate the attachment type */
@DrawableRes
fun Attachment.iconResource() = when (this.type) {
    Attachment.Type.IMAGE -> R.drawable.ic_photo_24dp
    Attachment.Type.GIFV, Attachment.Type.VIDEO -> R.drawable.ic_videocam_24dp
    Attachment.Type.AUDIO -> R.drawable.ic_music_box_24dp
    Attachment.Type.UNKNOWN -> R.drawable.ic_attach_file_24dp
}

/**
 * Returns a formatted version of [Attachment.description] for use in a
 * UX label.
 *
 * If the media has no description then the content of
 * R.string.description_post_media_no_description_placeholder is returned.
 *
 * If the media has a duration (i.e., it's audio or video) the duration is
 * prepended to the description, and returned (again, the string resource
 * is used if the media has no description).
 *
 * Use [Attachment.getContentDescription] for text to use as the attachment's
 * content description.
 */
fun Attachment.getFormattedDescription(context: Context): CharSequence {
    var duration = ""
    if (meta?.duration != null && meta!!.duration!! > 0) {
        duration = meta!!.duration?.let { formatDuration(it.toDouble()) } + " "
    }
    return if (description.isNullOrEmpty()) {
        duration + context.getString(R.string.description_post_media_no_description_placeholder)
    } else {
        duration + description
    }
}

/**
 * Returns text suitable for use in an attachment's content description.
 *
 * Like [Attachment.getFormattedDescription], but prepends the text with
 * information about the media's type (image, video, etc).
 */
fun Attachment.getContentDescription(context: Context): CharSequence {
    val description = getFormattedDescription(context)
    val label = when (type) {
        Attachment.Type.IMAGE -> context.getString(R.string.post_media_images)
        Attachment.Type.GIFV, Attachment.Type.VIDEO -> context.getString(R.string.post_media_video)
        Attachment.Type.AUDIO -> context.getString(R.string.post_media_audio)
        else -> context.getString(R.string.post_media_attachments)
    }

    return "$label, $description"
}

private fun formatDuration(durationInSeconds: Double): String {
    val seconds = durationInSeconds.roundToInt() % 60
    val minutes = durationInSeconds.toInt() % 3600 / 60
    val hours = durationInSeconds.toInt() / 3600
    return "%d:%02d:%02d".format(hours, minutes, seconds)
}

fun Attachment.aspectRatio(): Double {
    val (width, height, aspect) = (meta?.small ?: meta?.original) ?: return 1.7778
    width ?: return 1.778
    height ?: return 1.778
    aspect ?: return 1.778
    val adjustedAspect = if (aspect > 0) aspect else width.toDouble() / height
    return adjustedAspect.coerceIn(0.5, 2.0)
}

fun Iterable<Attachment>.aspectRatios() = map { it.aspectRatio() }

/**
 * @return True if this attachment type is playable and should show the playable indicator,
 *     otherwise false.
 */
fun Attachment.Type.isPlayable() = when (this) {
    Attachment.Type.AUDIO, Attachment.Type.GIFV, Attachment.Type.VIDEO -> true
    Attachment.Type.IMAGE, Attachment.Type.UNKNOWN -> false
}
