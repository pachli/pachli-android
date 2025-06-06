/*
 * Copyright 2022 Tusky Contributors
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

package app.pachli.core.network

import android.text.Html.TagHandler
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.core.text.parseAsHtml
import app.pachli.core.common.string.trimTrailingWhitespace

/**
 * parse a String containing html from the Mastodon api to Spanned
 */
@JvmOverloads
fun CharSequence.parseAsMastodonHtml(tagHandler: TagHandler? = null): Spanned {
    return this.replace("<br> ".toRegex(), "<br>&nbsp;")
        .replace("<br /> ", "<br />&nbsp;")
        .replace("<br/> ", "<br/>&nbsp;")
        .replace("  ", "&nbsp;&nbsp;")
        .parseAsHtml(tagHandler = tagHandler)
        /* Html.fromHtml returns trailing whitespace if the html ends in a </p> tag, which
         * most status contents do, so it should be trimmed. */
        .trimTrailingWhitespace()
}

// See https://github.com/tuskyapp/Tusky/issues/563
fun replaceCrashingCharacters(content: CharSequence): CharSequence? {
    var replacing = false
    var builder: SpannableStringBuilder? = null
    val length = content.length
    for (index in 0 until length) {
        val character = content[index]

        // If there are more than one or two, switch to a map
        if (character == SOFT_HYPHEN) {
            if (!replacing) {
                replacing = true
                builder = SpannableStringBuilder(content, 0, index)
            }
            builder!!.append(ASCII_HYPHEN)
        } else if (replacing) {
            builder!!.append(character)
        }
    }
    return if (replacing) builder else content
}

private const val SOFT_HYPHEN = '\u00ad'
private const val ASCII_HYPHEN = '-'
