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
import android.graphics.Color
import android.text.Editable
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import app.pachli.core.network.PachliTagHandler
import app.pachli.core.ui.taghandler.LeadingMarginWithTextSpan.Alignment
import app.pachli.core.ui.taghandler.Mark.Code
import app.pachli.core.ui.taghandler.Mark.OrderedListItem
import app.pachli.core.ui.taghandler.Mark.UnorderedListItem
import com.google.android.material.color.MaterialColors
import java.util.Stack
import org.xml.sax.XMLReader

/**
 * Matches `<br> ` and variants with an internal space or self-closing
 * `/` character.
 *
 * - `<br> `
 * - `<br > `
 * - `<br/> `
 * - `<br /> `
 *
 * etc.
 */
private val rxBR = """(?i)<br(?<inner>\s*/)?> """.toRegex()

/**
 * Matches opening and closing `ul`, `ol`, and `li` tags.
 *
 * The `/` in a (possible) closing tag is saved in `$bl`. The tag name is
 * saved in `$tag`.
 */
private val rxListTags = """(?i)<(?<bl>/)?(?<tag>li|ol|ul)[^>]*>""".toRegex()

/**
 * Handles HTML elements that may appear in Mastodon content and are not handled
 * by [HtmlCompat.fromHtml][androidx.core.text.HtmlCompat.fromHtml].
 *
 * The elements Mastodon may use are described at
 * https://docs.joinmastodon.org/spec/activitypub/#sanitization
 *
 * Of those, the elements that require custom handling are:
 *
 * - `code` (not supported by HtmlCompat)
 * - `pre` (not supported by HtmlCompat)
 * - `ol / li` (not supported by HtmlCompat, rewritten to pachli-ol)
 * - `ul / li` (supported by HtmlCompat, but badly, rewritten to pachli-ul)
 */
class MastodonTagHandler(context: Context) : PachliTagHandler {
    /** Stack of currently open `ol` and `ul` lists. */
    private val lists = Stack<ListElementHandler>()

    private val codeHandler = CodeHandler(context)

    override fun rewriteHtml(html: CharSequence): String {
        // Can't use named match groups here (e.g., code like
        // `.replace(rxListTags, $$"<${bl}pachli-${tag}>")` because named
        // match groups aren't supported on Android < API 26.
        //
        // Can't use numbered match groups either (e.g., code like
        // `.replace(rxListTags, $$"<$1pachli-$2>")` because if the numbered
        // match group didn't match anything it interpolates into the string
        // as the literal text `null`.
        //
        // So use a transform function that can handle both of these cases.
        return html
            .replace(rxBR, { "<br${it.groups[1]?.value ?: ""}>&nbsp;" })
            .replace("  ", "&nbsp;&nbsp;")
            .replace(
                rxListTags,
                { "<${it.groups[1]?.value ?: ""}pachli-${it.groups[2]?.value}>" },
            )
    }

    override fun handleTag(opening: Boolean, tag: String, output: Editable, xmlReader: XMLReader) {
        // `ul`, `ol`, and `li` were re-written to have a `pachli-` prefix in
        // `rewriteHtml`.
        //
        // `code` and `pre` aren't handled by the default tag handler, so can
        // be used as is.
        when (tag.lowercase()) {
            "pachli-ul" -> if (opening) {
                lists.push(UnorderedListElementHandler())
            } else {
                if (lists.isNotEmpty()) lists.pop()
            }

            "pachli-ol" -> if (opening) {
                lists.push(OrderedListElementHandler())
            } else {
                if (lists.isNotEmpty()) lists.pop()
            }

            "pachli-li" -> if (opening) {
                if (lists.isNotEmpty()) lists.peek().startListItem(output)
            } else {
                if (lists.isNotEmpty()) lists.peek().endListItem(output, indentation = lists.size - 1)
            }

            "code", "pre" -> if (opening) {
                codeHandler.startElement(output)
            } else {
                codeHandler.endElement(output)
            }
        }
    }
}

private interface ElementHandler {
    /**
     * Called when the element's start tag is encountered.
     *
     * @param text The current content, up to, but not including, the start
     * tag.
     */
    fun startElement(text: Editable) = Unit

    /**
     * Called when the element's end tag is encountered.
     *
     * @param text The current content, up to, but not including, the end
     * tag.
     */
    fun endElement(text: Editable) = Unit
}

private interface ListElementHandler : ElementHandler {
    /** Called when an opening <li> tag is encountered. */
    fun startListItem(text: Editable) = Unit

    /** Called when a closing </li> tag is encountered. */
    fun endListItem(text: Editable, indentation: Int) = Unit
}

/**
 * Processes inline `code` elements.
 *
 * Marks the `<code>` tag. `</code>` inserts spans between the start
 * and end tags setting the font family and foreground/background colours.
 */
private class CodeHandler(context: Context) : ElementHandler {
    private val bgColor = MaterialColors.getColor(
        context,
        com.google.android.material.R.attr.colorSurfaceContainerLow,
        Color.WHITE,
    )
    private val fgColor = MaterialColors.getColor(
        context,
        com.google.android.material.R.attr.colorOnSurfaceVariant,
        Color.BLACK,
    )

    private val typefaceSpan = TypefaceSpan("monospace")
    private val fgColorSpan = ForegroundColorSpan(fgColor)
    private val bgColorSpan = BackgroundColorSpan(bgColor)

    override fun startElement(text: Editable) {
        text.appendMark(Code)
    }

    override fun endElement(text: Editable) {
        text.getLastSpanOrNull<Code>()?.let { mark ->
            text.setSpansFromMark(mark, typefaceSpan, fgColorSpan, bgColorSpan)
        }
    }
}

/**
 * Processes unordered lists and their `li` contents.
 *
 * Marks the `<li>` tag. `</li>` tags insert a [LeadingMarginWithTextSpan]
 * between the start and end tag with a bullet character in the margin.
 */
private class UnorderedListElementHandler : ListElementHandler {
    override fun startListItem(text: Editable) {
        text.ensureEndsWithNewline()
        text.appendMark(UnorderedListItem)
    }

    override fun endListItem(text: Editable, indentation: Int) {
        text.ensureEndsWithNewline()

        text.getLastSpanOrNull<UnorderedListItem>()?.let { mark ->
            text.setSpansFromMark(
                mark,
                LeadingMarginWithTextSpan(indentation, Alignment.CENTER) { "•" },
            )
        }
    }
}

/**
 * Processes ordered lists and their `li` contents.
 *
 * Marks the `<li>` tag. `</li>` tags insert a [LeadingMarginWithTextSpan]
 * between the start and end tag, with the 1-based numbered index of the
 * `li` element in the list.
 */
private class OrderedListElementHandler : ListElementHandler {
    /** Count of total `li` tags seen in this ordered list. */
    private var count = 1

    override fun startListItem(text: Editable) {
        text.ensureEndsWithNewline()
        text.appendMark(OrderedListItem(count++))
    }

    override fun endListItem(text: Editable, indentation: Int) {
        text.ensureEndsWithNewline()

        text.getLastSpanOrNull<OrderedListItem>()?.let { mark ->
            text.setSpansFromMark(
                mark,
                LeadingMarginWithTextSpan(indentation) {
                    if (it > 0) {
                        "${mark.number}. "
                    } else {
                        " .${mark.number}"
                    }
                },
            )
        }
    }
}
