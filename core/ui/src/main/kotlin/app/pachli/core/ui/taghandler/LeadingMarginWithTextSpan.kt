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
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import kotlin.math.roundToInt

fun interface ComputeLeadingMarginWithTextSpanText {
    /**
     * Returns the text to display.
     *
     * @param dir Positive if the text will be displayed LTR, negative
     * if the text will be displayed RTL.
     */
    operator fun invoke(dir: Int): String
}

/**
 * A [LeadingMarginSpan][android.text.style.LeadingMarginSpan] that shows text
 * inside the margin.
 *
 * The leading margin is sized to provide enough room to show the text `99. `,
 * so this can be used for lists of up to 99 items.
 *
 * The text to display in the margin should be provided by [computeMarginText].
 *
 * Within the margin the text is aligned according to [alignment].
 *
 * @param indentation 0-based indentation level of this item.
 * @param alignment See [Alignment].
 * @param computeMarginText See [ComputeLeadingMarginWithTextSpanText.invoke]
 */
class LeadingMarginWithTextSpan(
    private val indentation: Int,
    private val alignment: Alignment = Alignment.END,
    private val computeMarginText: ComputeLeadingMarginWithTextSpanText,
) : LeadingMarginSpan {
    private var marginWidth: Int = 0

    private var marginText: String? = null

    /** How text in the leading margin should be aligned. */
    enum class Alignment {
        /** Text aligned to the start of the margin. */
        START,

        /** Text centered between the start and end of the margin. */
        CENTER,

        /** Text aligned to the end of the margin. */
        END,
    }

    override fun drawLeadingMargin(
        c: Canvas,
        p: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        l: Layout,
    ) {
        // Margin is sized to allow enough space for "99. " in the active font.
        marginWidth = p.measureText("99. ").roundToInt()

        val startCharOfSpan = (text as Spanned).getSpanStart(this)
        val isFirstChar = startCharOfSpan == start

        if (!isFirstChar) return

        val computedMarginText = if (marginText == null) {
            marginText = computeMarginText(dir)
            marginText!!
        } else {
            marginText!!
        }
        val marginTextWidth = p.measureText(computedMarginText)

        val xOffset = when (alignment) {
            Alignment.START -> 0f
            Alignment.CENTER -> (marginWidth - marginTextWidth) / 2
            Alignment.END -> if (dir > 0) marginWidth - marginTextWidth else -(marginWidth - marginTextWidth)
        }

        // Some Android versions have a bug where `x` is always 0 (e.g., API 28).
        // Calculate the correct value.
        val trueX: Int = if (dir > 0) {
            marginWidth * indentation
        } else {
            c.width - if (marginWidth * indentation == 0) {
                marginTextWidth.roundToInt()
            } else {
                marginWidth * indentation
            }
        }
        c.drawText(computedMarginText, trueX + xOffset, baseline.toFloat(), p)
    }

    override fun getLeadingMargin(first: Boolean): Int = marginWidth
}
