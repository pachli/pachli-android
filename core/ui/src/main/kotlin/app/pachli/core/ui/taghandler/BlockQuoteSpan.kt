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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.style.LeadingMarginSpan
import androidx.appcompat.R
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt

/**
 * A [LeadingMarginSpan] that draws a vertical line in the leading margin of
 * the text, suitable for displaying an HTML `blockquote`.
 *
 * The margin is sized to the width of an "m" in the text's font.
 *
 * The stripe is set to one-third of the margin width, and drawn in
 * `colorPrimary`.
 *
 * [android.text.style.QuoteSpan] is not suitable for this as the basic
 * implementation does not allow you to set the colour or stripe width.
 */
internal class BlockQuoteSpan(context: Context) : LeadingMarginSpan {
    private var marginWidth = 0

    private val stripeColour = MaterialColors.getColor(
        context,
        R.attr.colorPrimary,
        Color.WHITE,
    )

    override fun drawLeadingMargin(
        c: Canvas,
        p: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence?,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout?,
    ) {
        marginWidth = p.measureText("m").roundToInt()
        val stripeWidth = (marginWidth / 3f)
        val style = p.style
        val color = p.color
        p.style = Paint.Style.FILL
        p.color = stripeColour
        c.drawRect(x.toFloat(), top.toFloat(), x + dir * stripeWidth, bottom.toFloat(), p)
        p.style = style
        p.color = color
    }

    override fun getLeadingMargin(first: Boolean) = marginWidth
}
