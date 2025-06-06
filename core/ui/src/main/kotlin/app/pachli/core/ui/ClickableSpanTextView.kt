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

package app.pachli.core.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.text.Selection
import android.text.Spannable
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_CANCEL
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_UP
import android.view.ViewConfiguration
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat.startActivity
import androidx.core.graphics.withSave
import androidx.core.view.doOnLayout
import app.pachli.core.designsystem.R as DR
import java.lang.Float.max
import java.lang.Float.min
import kotlin.math.abs
import timber.log.Timber

/**
 * Represents the action to take when the user long-presses on a link.
 *
 * @param action The action to take.
 */
private class LongPressRunnable(val action: Action) : Runnable {
    fun interface Action {
        operator fun invoke()
    }

    override fun run() = action()
}

/**
 * Displays text to the user with optional [ClickableSpan]s. Extends the touchable area of the spans
 * to ensure they meet the minimum size of 48dp x 48dp for accessibility requirements.
 *
 * If the touchable area of multiple spans overlap the touch is dispatched to the closest span.
 */
class ClickableSpanTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {
    /**
     * Map of [RectF] that enclose the [ClickableSpan] without any additional touchable area. A span
     * may extend over more than one line, so multiple entries in this map may point to the same
     * span.
     */
    private val spanRects = mutableMapOf<RectF, ClickableSpan>()

    /**
     * Map of [RectF] that enclose the [ClickableSpan] with the additional touchable area. A span
     * may extend over more than one line, so multiple entries in this map may point to the same
     * span.
     */
    private val delegateRects = mutableMapOf<RectF, ClickableSpan>()

    /**
     * The [ClickableSpan] that is used for the point the user has touched. Null if the user is
     * not tapping, or the point they have touched is not associated with a span.
     */
    private var clickedSpan: ClickableSpan? = null

    /** The minimum size, in pixels, of a touchable area for accessibility purposes */
    private val minDimenPx = resources.getDimensionPixelSize(DR.dimen.minimum_touch_target)

    /**
     * Debugging helper. Normally false, set this to true to show a border around spans, and
     * shade their touchable area.
     */
    private val showSpanBoundaries = false

    /**
     * Debugging helper. The paint to use to draw a span.
     */
    private lateinit var spanDebugPaint: Paint

    /**
     * Debugging helper. The paint to use to shade a span's touchable area.
     */
    private lateinit var paddingDebugPaint: Paint

    /** Runnable to trigger when the user long-presses on a link. */
    private var longPressRunnable: LongPressRunnable? = null

    /** True if [longPressRunnable] has triggered, false otherwise. */
    private var longPressTriggered = false

    init {
        // Initialise debugging paints, if appropriate. Only ever present in debug builds, and
        // is optimised out if showSpanBoundaries is false.
        if (BuildConfig.DEBUG && showSpanBoundaries) {
            spanDebugPaint = Paint()
            spanDebugPaint.color = Color.BLACK
            spanDebugPaint.style = Paint.Style.STROKE

            paddingDebugPaint = Paint()
            paddingDebugPaint.color = Color.MAGENTA
            paddingDebugPaint.alpha = 50
        }
    }

    override fun onTextChanged(
        text: CharSequence?,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int,
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)

        // TextView tries to optimise the layout process, and will not perform a layout if the
        // new text takes the same area as the old text (see TextView.checkForRelayout()). This
        // can result in statuses using the wrong clickable areas as they are never remeasured.
        // (https://github.com/tuskyapp/Tusky/issues/3596). Force a layout pass to ensure that
        // the spans are measured correctly.
        if (!isInLayout) requestLayout()

        doOnLayout { measureSpans() }
    }

    /**
     * Compute [Rect]s for each [ClickableSpan].
     *
     * Each span is associated with at least two Rects. One for the span itself, and one for the
     * touchable area around the span.
     *
     * If the span runs over multiple lines there will be two Rects per line.
     */
    private fun measureSpans() {
        spanRects.clear()
        delegateRects.clear()

        val spannedText = text as? Spanned ?: return

        // The goal is to record all the [Rect]s associated with a span with the same fidelity
        // that the user sees when they highlight text in the view to select it.
        //
        // There's no method in [TextView] or [Layout] that does exactly that. [Layout.getSelection]
        // would be perfect, but it's not accessible. However, [Layout.getSelectionPath] is. That
        // records the Rects between two characters in the string, and handles text that spans
        // multiple lines, is bidirectional, etc.
        //
        // However, it records them in to a [Path], and a Path has no mechanism to extract the
        // Rects saved in to it.
        //
        // So subclass Path with [RectRecordingPath], which records the data from calls to
        // [addRect]. Pass that to `getSelectionPath` to extract all the Rects between start and
        // end.
        val rects = mutableListOf<RectF>()
        val rectRecorder = RectRecordingPath(rects)

        for (span in spannedText.getSpans(0, text.length - 1, ClickableSpan::class.java)) {
            rects.clear()
            val spanStart = spannedText.getSpanStart(span)
            val spanEnd = spannedText.getSpanEnd(span)

            // Collect all the Rects for this span
            layout.getSelectionPath(spanStart, spanEnd, rectRecorder)

            // Save them
            for (rect in rects) {
                // Adjust to account for the view's padding and gravity
                rect.offset(totalPaddingLeft.toFloat(), totalPaddingTop.toFloat())
                rect.bottom += extendedPaddingBottom

                // The rect wraps just the span, with no additional touchable area. Save a copy.
                spanRects[RectF(rect)] = span

                // Adjust the rect to meet the minimum dimensions
                if (rect.height() < minDimenPx) {
                    val yOffset = (minDimenPx - rect.height()) / 2
                    rect.top = max(0f, rect.top - yOffset)
                    rect.bottom = min(rect.bottom + yOffset, bottom.toFloat())
                }

                if (rect.width() < minDimenPx) {
                    val xOffset = (minDimenPx - rect.width()) / 2
                    rect.left = max(0f, rect.left - xOffset)
                    rect.right = min(rect.right + xOffset, right.toFloat())
                }

                // Save it
                delegateRects[rect] = span
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) return super.dispatchTouchEvent(event)

        // Prevent crash on Android <= O_MR1. The selection state can be lost
        // later, causing `selectionEnd` to return -1. Fix this by explicitly
        // resetting the selection (if present) or resetting the text.
        //
        // See https://issuetracker.google.com/issues/37068143.
        val start = selectionStart
        val end = selectionEnd
        val content = text as? Spannable

        if (content != null && (start < 0 || end < 0)) {
            Selection.setSelection(content, content.length)
        } else if (start != end && event.actionMasked == ACTION_DOWN) {
            text = null
            text = content
        }

        return super.dispatchTouchEvent(event)
    }

    /**
     * Handle some touch events.
     *
     * - [ACTION_DOWN]: Determine which, if any span, has been clicked, and save in clickedSpan.
     * Launch the runnable that will either be cancelled, or act as if this was a long press.
     * - [ACTION_UP]: If a span was saved then dispatch the click to that span.
     * - [ACTION_CANCEL]: Clear the saved span.
     * - [ACTION_MOVE]: Cancel if the user has moved off the span they original touched.
     *
     * Defer to the parent class for other touches.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: return super.onTouchEvent(null)
        if (delegateRects.isEmpty()) return super.onTouchEvent(event)

        when (event.action) {
            ACTION_DOWN -> {
                // Clear any existing touch actions
                removeLongPressRunnable()
                longPressTriggered = false

                clickedSpan = null

                // Determine the span the user touched. If no span then nothing to do.
                val span = getTouchedSpan(event, spanRects) ?: return super.onTouchEvent(event)

                clickedSpan = span
                val url = (span as URLSpan).url

                // Get the text of the span, to possibly use as the title. Maybe null if
                // getSpanStart or getSpanEnd return -1.
                val title = let {
                    val spanStart = (text as Spanned).getSpanStart(span)
                    val spanEnd = (text as Spanned).getSpanEnd(span)

                    if (spanStart == -1 || spanEnd == -1) return@let null

                    text.subSequence(spanStart + 1, spanEnd).toString()
                }

                // Configure and launch the runnable that will act if this is a long-press.
                // Opens the chooser with the link the user touched. If the text of the span
                // is not the same as the URL it's sent as the title.
                longPressRunnable = LongPressRunnable {
                    longPressTriggered = true
                    clickedSpan = null
                    this@ClickableSpanTextView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    val shareIntent = Intent.createChooser(
                        Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, url)
                            if (title != null && title != url) putExtra(Intent.EXTRA_TITLE, title)
                            type = "text/plain"
                        },
                        null,
                    )
                    startActivity(context, shareIntent, null)
                }
                this@ClickableSpanTextView.postDelayed(
                    longPressRunnable,
                    ViewConfiguration.getLongPressTimeout().toLong(),
                )

                return super.onTouchEvent(event)
            }

            ACTION_UP -> {
                removeLongPressRunnable()
                if (longPressTriggered) return true

                return clickedSpan?.let {
                    // If the user released under a different span there's nothing to do.
                    if (getTouchedSpan(event, spanRects) != it) {
                        return super.onTouchEvent(event)
                    }

                    it.onClick(this@ClickableSpanTextView)
                    clickedSpan = null
                    return true
                } ?: super.onTouchEvent(event)
            }

            ACTION_CANCEL -> {
                // Clear any existing touch actions
                removeLongPressRunnable()
                clickedSpan = null
                return super.onTouchEvent(event)
            }

            ACTION_MOVE -> {
                if (getTouchedSpan(event, spanRects) != clickedSpan) {
                    removeLongPressRunnable()
                    clickedSpan = null
                }
                return super.onTouchEvent(event)
            }

            else -> return super.onTouchEvent(event)
        }
    }

    /**
     * Returns the span the user clicked on, or null if the click was not over a span.
     */
    private fun getTouchedSpan(event: MotionEvent, spanRects: MutableMap<RectF, ClickableSpan>): ClickableSpan? {
        val x = event.x
        val y = event.y

        // If the user has clicked directly on a span then use it, ignoring any overlap
        for ((rect, span) in spanRects) {
            if (!rect.contains(x, y)) continue
            Timber.v("span click: %s", (span as URLSpan).url)
            return span
        }

        // Otherwise, check to see if it's in a touchable area
        var activeEntry: MutableMap.MutableEntry<RectF, ClickableSpan>? = null

        for (entry in delegateRects) {
            if (entry == activeEntry) continue
            if (!entry.key.contains(x, y)) continue

            if (activeEntry == null) {
                activeEntry = entry
                continue
            }
            Timber.v("Overlap: %s %s", (entry.value as URLSpan).url, (activeEntry.value as URLSpan).url)
            if (isClickOnFirst(entry.key, activeEntry.key, x, y)) {
                activeEntry = entry
            }
        }
        activeEntry?.let { Timber.v("padding click: %s", (activeEntry.value as URLSpan).url) }
        return activeEntry?.value
    }

    /**
     * Determine whether a click on overlapping rectangles should be attributed to the first or the
     * second rectangle.
     *
     * When the user clicks on the overlap it has to be attributed to the "best" rectangle. The
     * rectangles have equivalent z-order, so their "closeness" to the user in the Z-plane is not
     * a consideration.
     *
     * The chosen rectangle depends on whether they overlap top/bottom (the top of one rect is
     * not the same as the top of the other rect), or they overlap left/right (the tops of both
     * rects are the same).
     *
     * In this example the rectangles overlap top/bottom because their top edges are not aligned.
     *
     * ```
     *     +--------------+
     *     |1             |
     *     |      +--------------+
     *     |      |2             |
     *     |      |              |
     *     |      |              |
     *     +------|              |
     *            |              |
     *            +--------------+
     * ```
     *
     * (Rectangle #1 being partially occluded by rectangle #2 is for clarity in the diagram, it
     * does not affect the algorithm)
     *
     * Take the Y coordinate of the centre of each rectangle.
     *
     * ```
     *     +--------------+
     *     |1             |
     *     |      +--------------+
     *     |......|2             |  <-- Rect #1 centre line
     *     |      |              |
     *     |      |..............|  <-- Rect #2 centre line
     *     +------|              |
     *            |              |
     *            +--------------+
     * ```
     *
     * Take the Y position of the click, and determine which Y centre coordinate it is closest too.
     * Whichever one is closest is the clicked rectangle.
     *
     * In these examples the left column of numbers is the Y coordinate, `*` marks the point where
     * the user clicked.
     *
     * ```
     * 0   +--------------+                  +--------------+
     * 1   |1             |                  |1             |
     * 2   |      +--------------+           |      +--------------+
     * 3   |......|2  *          |           |......|2             |
     * 4   |      |              |           |      |              |
     * 5   |      |..............|           |      |*.............|
     * 6   +------|              |           +------|              |
     * 7          |              |                  |              |
     * 8          +--------------+                  +--------------+
     *
     *     Rect #1 centre Y = 3
     *     Rect #2 centre Y = 5
     *     Click (*) Y      = 3              Click (*) Y      = 5
     *     Result: Rect #1 is clicked        Result: Rect #2 is clicked
     * ```
     *
     * The approach is the same if the rectangles overlap left/right, but the X coordinate of the
     * centre of the rectangle is tested against the X coordinate of the click.
     *
     * @param first rectangle to test against
     * @param second rectangle to test against
     * @param x coordinate of user click
     * @param y coordinate of user click
     * @return true if the click was closer to the first rectangle than the second
     */
    private fun isClickOnFirst(first: RectF, second: RectF, x: Float, y: Float): Boolean {
        Timber.v("first: %s second: %s click: %f %f", first, second, x, y)
        val (firstDiff, secondDiff) = if (first.top == second.top) {
            Timber.v("left/right overlap")
            Pair(abs(first.centerX() - x), abs(second.centerX() - x))
        } else {
            Timber.v("top/bottom overlap")
            Pair(abs(first.centerY() - y), abs(second.centerY() - y))
        }
        Timber.d("firstDiff: %f secondDiff: %f", firstDiff, secondDiff)
        return firstDiff < secondDiff
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Paint span boundaries. Optimised out on release builds, or debug builds where
        // showSpanBoundaries is false.
        if (BuildConfig.DEBUG && showSpanBoundaries) {
            canvas.withSave {
                for (rect in delegateRects.keys) {
                    drawRect(rect, paddingDebugPaint)
                }

                for (rect in spanRects.keys) {
                    drawRect(rect, spanDebugPaint)
                }
            }
        }
    }

    /**
     * Removes the long-press runnable that may have been added in [onTouchEvent].
     */
    private fun removeLongPressRunnable() = longPressRunnable?.let {
        this.removeCallbacks(it)
        longPressRunnable = null
    }
}

/**
 * A [Path] that records the contents of all the [addRect] calls it receives.
 *
 * @param rects list to record the received [RectF]
 */
private class RectRecordingPath(private val rects: MutableList<RectF>) : Path() {
    override fun addRect(left: Float, top: Float, right: Float, bottom: Float, dir: Direction) {
        rects.add(RectF(left, top, right, bottom))
    }
}
