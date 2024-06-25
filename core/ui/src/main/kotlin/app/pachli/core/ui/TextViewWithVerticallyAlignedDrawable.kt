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

package app.pachli.core.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.TextView

/**
 * [TextView] that vertically aligns any start/end (or left/right) compound drawables
 * with the first line of text.
 *
 * If the drawable height is <= to the line height it is centred within the line.
 * Otherwise the top of the drawable is aligned with the top of the text.
 *
 * Necessary because the gravity of compound drawables can't be set.
 */
class TextViewWithVerticallyAlignedDrawable @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : androidx.appcompat.widget.AppCompatTextView(context, attrs, defStyleAttr) {
    private var boundsRect: Rect = Rect()

    override fun onDraw(canvas: Canvas) {
        val centreTextView = height / 2

        // Get the height of the first line
        getLineBounds(0, boundsRect)
        val lineHeight = boundsRect.height() + lineSpacingExtra.toInt()

        // If the drawable fits within the height of the first line, centre-align it
        // within that line. Otherwise align it with the top of the first line
        (compoundDrawablesRelative[0] ?: compoundDrawables[0])?.let {
            alignDrawableWithFirstLine(it, centreTextView, lineHeight)
        }

        (compoundDrawablesRelative[2] ?: compoundDrawables[2])?.let {
            alignDrawableWithFirstLine(it, centreTextView, lineHeight)
        }

        super.onDraw(canvas)
    }

    /**
     * Computes and sets a new top bound for [drawable] that vertically aligns it with
     * the first line of text.
     *
     * @param drawable the drawable to align
     * @param centreTextView the Y coordinate of the centre of the text view
     * @param lineHeight the height of the first line of text
     */
    private fun alignDrawableWithFirstLine(drawable: Drawable, centreTextView: Int, lineHeight: Int) {
        // Difference between the height of the drawable and the height of the line.
        // Positive if the line is taller than the drawable, negative otherwise
        val heightDiff = lineHeight - drawable.intrinsicHeight

        // The drawable's "natural" Y position, vertically centred
        val naturalY = centreTextView - (drawable.intrinsicHeight / 2)

        // Offset the drawable to the top of the view plus heightDiff. Coerce heightDiff to at
        // least 0 so if the drawable is taller than the line it is not clipped off the top.
        val offsetY = -naturalY + heightDiff.coerceAtLeast(0)
        drawable.setBounds(0, offsetY, drawable.intrinsicWidth, drawable.intrinsicHeight + offsetY)
    }
}
