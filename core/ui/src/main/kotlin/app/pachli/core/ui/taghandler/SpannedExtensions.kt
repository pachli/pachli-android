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

import android.text.Editable
import android.text.Spannable
import android.text.Spanned
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.Spanned.SPAN_MARK_MARK

/**
 * @return The last span of type [T] in [Spanned], null if no such
 * span exists.
 */
inline fun <reified T : Mark> Spanned.getLastSpanOrNull() =
    getSpans(0, length, T::class.java).lastOrNull()

/** Inserts a [mark] at the end of [Spannable]. */
fun Spannable.appendMark(mark: Mark) {
    val len = length
    setSpan(mark, len, len, SPAN_MARK_MARK)
}

/**
 * Finds [mark], and sets [spans] over [Spannable] from [mark] to the
 * end of [Spannable].
 */
fun Spannable.setSpansFromMark(mark: Mark, vararg spans: Any) {
    val start = getSpanStart(mark)
    removeSpan(mark)

    val end = length
    if (start != end) {
        spans.forEach {
            setSpan(it, start, end, SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}

/** Ensures [Editable] ends with a new line. */
fun Editable.ensureEndsWithNewline() {
    if (isNotEmpty() && last() != '\n') append("\n")
}
