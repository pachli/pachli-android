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

package app.pachli.core.ui

import com.twitter.twittertext.Regex.HASHTAG_SEPARATORS

/** Characters that are invalid in a hashtag. */
private val HASHTAG_INVALID_CHARS = Regex("[^\\p{L}\\p{N}\\u0E47-\\u0E4E$HASHTAG_SEPARATORS]")

/** Map of accented characters to their non-accented counterpart. */
private val accentToNonAccentMap = "ÀÁÂÃÄÅàáâãäåĀāĂăĄąÇçĆćĈĉĊċČčÐðĎďĐđÈÉÊËèéêëĒēĔĕĖėĘęĚěĜĝĞğĠġĢģĤĥĦħÌÍÎÏìíîïĨĩĪīĬĭĮįİıĴĵĶķĸĹĺĻļĽľĿŀŁłÑñŃńŅņŇňŉŊŋÒÓÔÕÖØòóôõöøŌōŎŏŐőŔŕŖŗŘřŚśŜŝŞşŠšſŢţŤťŦŧÙÚÛÜùúûüŨũŪūŬŭŮůŰűŲųŴŵÝýÿŶŷŸŹźŻżŽž".toList().zip(
    "AAAAAAaaaaaaAaAaAaCcCcCcCcCcDdDdDdEEEEeeeeEeEeEeEeEeGgGgGgGgHhHhIIIIiiiiIiIiIiIiIiJjKkkLlLlLlLlLlNnNnNnNnnNnOOOOOOooooooOoOoOoRrRrRrSsSsSsSssTtTtTtUUUUuuuuUuUuUuUuUuUuWwYyyYyYZzZzZz".toList(),
).toMap()

/**
 * Treat [this] as a hashtag name (with/without the leading `#`) and normalise it for
 * consistency with how Mastodon treats hashtags.
 *
 * - Convert half-width non-Latin characters to full-width
 * - Convert full-width Latin characters to basic Latin
 * - Lowercase
 * - Convert accented characters to non-accented counterparts
 */
// Based on the behaviour implemented in
// https://github.com/mastodon/mastodon/blob/main/app/lib/hashtag_normalizer.rb.
fun CharSequence.normaliseHashtag(): String {
    return java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFKC)
        .lowercase()
        .replaceAccents()
        .removeInvalidHashtagCharacters()
}

private fun CharSequence.removeInvalidHashtagCharacters() = HASHTAG_INVALID_CHARS.replace(this, "")

fun CharSequence.replaceAccents() = String(map { accentToNonAccentMap[it] ?: it }.toCharArray())
