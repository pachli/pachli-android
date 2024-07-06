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

package app.pachli.util

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import app.pachli.R
import app.pachli.core.network.model.Status

// TODO: Not part of the [Status] implementation because that module doesn't
// store resources (yet).

/**
 * @return A description for this visibility, or null if it's null or [Status.Visibility.UNKNOWN].
 */
fun Status.Visibility?.description(context: Context): CharSequence? {
    this ?: return null

    val resource: Int = when (this) {
        Status.Visibility.PUBLIC -> R.string.description_visibility_public
        Status.Visibility.UNLISTED -> R.string.description_visibility_unlisted
        Status.Visibility.PRIVATE -> R.string.description_visibility_private
        Status.Visibility.DIRECT -> R.string.description_visibility_direct
        Status.Visibility.UNKNOWN -> return null
    }
    return context.getString(resource)
}

@DrawableRes
fun Status.Visibility?.iconRes(): Int? {
    this ?: return null

    return when (this) {
        Status.Visibility.PUBLIC -> R.drawable.ic_public_24dp
        Status.Visibility.UNLISTED -> R.drawable.ic_lock_open_24dp
        Status.Visibility.PRIVATE -> R.drawable.ic_lock_24dp
        Status.Visibility.DIRECT -> R.drawable.ic_email_24dp
        Status.Visibility.UNKNOWN -> return null
    }
}

fun Status.Visibility?.iconDrawable(view: View): Drawable? {
    val resource = iconRes() ?: return null
    return AppCompatResources.getDrawable(view.context, resource)
}

/**
 * @return An icon for this visibility scaled and coloured to match the text on [textView].
 *     Returns null if visibility is [Status.Visibility.UNKNOWN].
 */
fun Status.Visibility?.icon(textView: TextView): Drawable? {
    this ?: return null

    val drawable = iconDrawable(textView) ?: return null
    val size = textView.textSize.toInt()
    drawable.setBounds(0, 0, size, size)
    drawable.setTint(textView.currentTextColor)
    return drawable
}
