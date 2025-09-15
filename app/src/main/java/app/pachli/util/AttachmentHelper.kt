package app.pachli.util

import android.content.Context
import app.pachli.R
import app.pachli.core.model.Attachment
import kotlin.math.roundToInt

/**
 * Returns a formatted version of [Attachment.description].
 *
 * If the media has no description then the content of
 * R.string.description_post_media_no_description_placeholder is returned.
 *
 * If the media has a duration (i.e., it's audio or video) the duration is
 * prepended to the description, and returned (again, the string resource
 * is used if the media has no description).
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

private fun formatDuration(durationInSeconds: Double): String {
    val seconds = durationInSeconds.roundToInt() % 60
    val minutes = durationInSeconds.toInt() % 3600 / 60
    val hours = durationInSeconds.toInt() / 3600
    return "%d:%02d:%02d".format(hours, minutes, seconds)
}

fun Iterable<Attachment>.aspectRatios(): List<Double> {
    return map { attachment ->
        // clamp ratio between 2:1 & 1:2, defaulting to 16:9
        val (width, height, aspect) = (attachment.meta?.small ?: attachment.meta?.original) ?: return@map 1.7778
        width ?: return@map 1.778
        height ?: return@map 1.778
        aspect ?: return@map 1.778
        val adjustedAspect = if (aspect > 0) aspect else width.toDouble() / height
        adjustedAspect.coerceIn(0.5, 2.0)
    }
}
