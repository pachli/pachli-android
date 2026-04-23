/*
 * Copyright (c) 2026 Pachli Association
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

package app.pachli.core.ui.taghandler

import android.content.Context
import android.graphics.Color
import android.text.TextPaint
import android.text.style.TypefaceSpan
import com.google.android.material.R
import com.google.android.material.color.MaterialColors

/**
 * Displays the text in monospace, in `colorOnSurfaceVariant`, on
 * `colorSurfaceContainerLow`.
 */
internal class CodeSpan(context: Context) : TypefaceSpan("monospace") {
    private val bgColor = MaterialColors.getColor(
        context,
        R.attr.colorSurfaceContainerLow,
        Color.WHITE,
    )
    private val fgColor = MaterialColors.getColor(
        context,
        R.attr.colorOnSurfaceVariant,
        Color.BLACK,
    )

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        ds.setColor(fgColor)
        ds.bgColor = bgColor
    }
}
