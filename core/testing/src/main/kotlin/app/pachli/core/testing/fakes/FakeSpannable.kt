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

package app.pachli.core.testing.fakes

import android.text.Spannable

class FakeSpannable(private val text: String) : Spannable {
    val spans = mutableListOf<BoundedSpan>()

    override fun setSpan(what: Any?, start: Int, end: Int, flags: Int) {
        spans.add(BoundedSpan(what, start, end))
    }

    override fun <T : Any> getSpans(start: Int, end: Int, type: Class<T>): Array<T> {
        return spans.filter { it.start >= start && it.end <= end && type.isInstance(it.span) }
            .map { it.span }
            .toTypedArray() as Array<T>
    }

    override fun removeSpan(what: Any?) {
        spans.removeIf { span -> span.span == what }
    }

    override fun toString(): String {
        return text
    }

    override val length: Int
        get() = text.length

    class BoundedSpan(val span: Any?, val start: Int, val end: Int)

    override fun nextSpanTransition(start: Int, limit: Int, type: Class<*>?): Int {
        throw NotImplementedError()
    }

    override fun getSpanEnd(tag: Any?): Int {
        throw NotImplementedError()
    }

    override fun getSpanFlags(tag: Any?): Int {
        throw NotImplementedError()
    }

    override fun get(index: Int): Char {
        throw NotImplementedError()
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        return text.subSequence(startIndex, endIndex)
    }

    override fun getSpanStart(tag: Any?): Int {
        throw NotImplementedError()
    }
}
