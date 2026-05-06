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

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.style.LeadingMarginSpan
import androidx.annotation.ColorInt
import androidx.annotation.Px

/**
 * A [LeadingMarginSpan] that draws a vertical line in the leading margin of
 * the text, suitable for displaying an HTML `blockquote`.
 *
 * [android.text.style.QuoteSpan] is not suitable for this as the basic
 * implementation does not allow you to set the colour or stripe width,
 * that's only possible in API 28+.
 *
 * @param marginWidth Width of the margin, in pixels.
 * @param stripeWidth Width of the stripe, in pixels.
 * @param stripeColour Colour to use for the stripe.
 * @param indentation Count of the number of open `blockquote` elements
 * before this one.
 */
internal class BlockQuoteSpan(
    @Px private val marginWidth: Int,
    @Px private val stripeWidth: Int,
    @ColorInt private val stripeColour: Int,
    private val indentation: Int,
) : LeadingMarginSpan {
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
        val style = p.style
        val color = p.color
        p.style = Paint.Style.FILL
        p.color = stripeColour

        // Draw N stripes in the margin, one for this quote, and one for
        // every other open blockquote to here.
        for (level in 0..indentation) {
            val trueX = if (dir > 0) {
                marginWidth * level
            } else {
                c.width - (marginWidth * level)
            }
            c.drawRect(trueX.toFloat(), top.toFloat(), (trueX + dir * stripeWidth).toFloat(), bottom.toFloat(), p)
        }

        p.style = style
        p.color = color
    }

    override fun getLeadingMargin(first: Boolean) = marginWidth
}
