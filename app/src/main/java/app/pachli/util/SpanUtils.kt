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

package app.pachli.util

import android.text.Spannable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import app.pachli.core.ui.MentionSpan
import app.pachli.core.ui.NoUnderlineURLSpan
import com.twitter.twittertext.Extractor

private val spanClasses = listOf(ForegroundColorSpan::class.java, URLSpan::class.java)

private val extractor = Extractor().apply { isExtractURLWithoutProtocol = false }

/**
 * Takes text containing mentions and hashtags and urls and makes them the given colour.
 */
fun highlightSpans(text: Spannable, colour: Int) {
    // Strip all existing colour spans.
    for (spanClass in spanClasses) {
        clearSpans(text, spanClass)
    }

    // Colour the mentions and hashtags.
    val string = text.toString()

    val entities = extractor.extractEntitiesWithIndices(string)

    for (entity in entities) {
        val span = when (entity.type) {
            Extractor.Entity.Type.URL -> NoUnderlineURLSpan(string.substring(entity.start, entity.end))
            Extractor.Entity.Type.HASHTAG -> ForegroundColorSpan(colour)
            Extractor.Entity.Type.MENTION -> MentionSpan(string.substring(entity.start, entity.end))
        }
        text.setSpan(span, entity.start, entity.end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
    }
}

private fun <T> clearSpans(text: Spannable, spanClass: Class<T>) {
    for (span in text.getSpans(0, text.length, spanClass)) {
        text.removeSpan(span)
    }
}
