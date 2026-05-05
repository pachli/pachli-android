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
import android.os.Build
import android.text.Layout
import android.text.style.LeadingMarginSpan
import androidx.annotation.Px
import androidx.annotation.VisibleForTesting
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
 * The text to display in the margin should be provided by [computeMarginText].
 *
 * Within the margin the text is aligned according to [alignment].
 *
 * @param marginWidth Width of the margin, in pixels
 * @param indentation 0-based indentation level of this item.
 * @param alignment See [Alignment].
 * @param computeMarginText See [ComputeLeadingMarginWithTextSpanText.invoke]
 */
internal class LeadingMarginWithTextSpan(
    @Px private val marginWidth: Int,
    private val indentation: Int,
    private val alignment: Alignment = Alignment.END,
    @get:VisibleForTesting
    val computeMarginText: ComputeLeadingMarginWithTextSpanText,
) : LeadingMarginSpan {
    /** Text to display in the margin. */
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
        if (!first) return

        // On newer Android versions the paint may be flagged to include hyphens
        // at the start or end (this seems to occur if the first line of the
        // paragraph this span is attached to will be hyphenated). This:
        //
        // 1. Miscalculates the text measurements.
        // 2. Causes the text drawn in the leading margin to have a leading or
        // trailing hyphen.
        //
        // Fix this by using a new paint, constructed from the first, with the
        // hyphenation flags disabled.
        val finalPaint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Paint(p).apply {
                startHyphenEdit = Paint.START_HYPHEN_EDIT_NO_EDIT
                endHyphenEdit = Paint.END_HYPHEN_EDIT_NO_EDIT
            }
        } else {
            p
        }

        val computedMarginText = if (marginText == null) {
            marginText = computeMarginText(dir)
            marginText!!
        } else {
            marginText!!
        }
        val marginTextWidth = finalPaint.measureText(computedMarginText)

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

        // Alternative code for fixing the "Hyphenated text draws a hyphen
        // after a bullet" problem, in case changing the paint doesn't work
        // on all devices. Requires the span be passed the target TextView
        // as a constructor parameter.
//        val builder = StaticLayout.Builder.obtain(
//            computedMarginText,
//            0,
//            computedMarginText.length,
//            textView.paint,
//            marginWidth,
//        )
//
//        val staticLayout = builder
//            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
//            .setLineSpacing(textView.lineSpacingExtra, textView.lineSpacingMultiplier)
//            .setIncludePad(false)
//            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
//            .build()
//
//        c.withTranslation(trueX + xOffset, top.toFloat()) {
//            staticLayout.draw(this)
//        }

        c.drawText(computedMarginText, trueX + xOffset, baseline.toFloat(), finalPaint)
    }

    override fun getLeadingMargin(first: Boolean): Int = marginWidth
}
