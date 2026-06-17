/*
 * Copyright 2025 Pachli Association
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

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.URLSpan
import android.util.TypedValue
import android.widget.TextView
import androidx.core.text.getSpans
import androidx.core.text.method.LinkMovementMethodCompat
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.designsystem.R
import app.pachli.core.markwon.MarkwonFactory
import app.pachli.core.model.Emoji
import app.pachli.core.model.Hashtag
import app.pachli.core.model.Status
import app.pachli.core.network.PachliTagHandler
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.network.removeQuoteInline
import app.pachli.core.preferences.LinksToUnderline
import app.pachli.core.ui.taghandler.LeadingMarginWithTextSpan
import app.pachli.core.ui.taghandler.MastodonTagHandler
import com.bumptech.glide.RequestManager

/**
 * Interface for setting the content of a [TextView] to Mastodon HTML that
 * has been processed for display.
 *
 * @see [invoke]
 */
interface SetContent {
    /**
     * Processes [content] (assumed to be Mastodon HTML) and sets it as the text
     * for [textView].
     *
     * The content is parsed by [parseToSpanned].
     *
     * Emojis in [content] that are also present in [emojis] are loaded and
     * embedded using [glide], optionally animated depending on [animateEmojis].
     *
     * Any [mentions], and [hashtags] are made clickable and sent to [linkListener].
     *
     * @param glide [RequestManager] to use to load images.
     * @param textView [TextView] to load the final content in to.
     * @param content Content to parse and load.
     * @param emojis
     * @param animateEmojis True if emojis should be animated.
     * @param removeQuoteInline If true, remove `p` elements with a `quote-inline` class.
     * @param linksToUnderline
     * @param mentions
     * @param hashtags
     * @param tagHandler
     * @param linkListener
     */
    operator fun invoke(
        glide: RequestManager,
        textView: TextView,
        content: CharSequence,
        emojis: List<Emoji>,
        animateEmojis: Boolean,
        removeQuoteInline: Boolean,
        linksToUnderline: Set<LinksToUnderline>,
        mentions: List<Status.Mention>? = null,
        hashtags: List<Hashtag>? = null,
        tagHandler: PachliTagHandler? = null,
        linkListener: LinkListener,
    ) {
        val spannableStringBuilder = SpannableStringBuilder().apply {
            append(parseToSpanned(textView, content, removeQuoteInline, tagHandler))

            getSpans(0, length, URLSpan::class.java).forEach {
                convertUrlSpanToMoreSpecificType(it, linksToUnderline, this, mentions, hashtags, linkListener)
            }

            val hashtagsInContent = getSpans(0, length, HashtagSpan::class.java).map {
                it.hashtag
            }.toSet()
            val oobHashtags = hashtags.orEmpty().filterNot { hashtagsInContent.contains(it.name) }

            val underlineHashtags = linksToUnderline.contains(LinksToUnderline.HASHTAGS)
            val oobSpans = oobHashtags.map { tag ->
                HashtagSpan(tag.name, underlineHashtags, tag.url) { linkListener.onViewTag(tag.name) }
            }

            if (oobSpans.isNotEmpty()) {
                append("\n\n")

                oobSpans.forEachIndexed { index, span ->
                    val start = length
                    append("#${span.hashtag}".unicodeWrap())
                    val end = length
                    if (index < oobSpans.size) append(" ")
                    setSpan(span, start, end, 0)
                }
            }

            emojify(glide, emojis, textView, animateEmojis)

            markupHiddenUrls(textView, this)
        }

        setText(textView, spannableStringBuilder)
        textView.movementMethod = LinkMovementMethodCompat.getInstance()
    }

    /**
     * Parse [content] to [Spanned].
     *
     * Implementations of [SetContent] should override this to perform the
     * actual parsing. Post-processing is handled in [invoke].
     *
     * @param textView [TextView] to load the final content in to.
     * @param content The content to parse, expected to be HTML.
     * @param removeQuoteInline If true, remove any `p` elements with a `quote-inline`
     * class as part of parsing.
     * @param tagHandler Optional [PachliTagHandler] to use when parsing HTML.
     *
     * @return [content] converted to a [Spanned] string.
     */
    fun parseToSpanned(textView: TextView, content: CharSequence, removeQuoteInline: Boolean, tagHandler: PachliTagHandler? = null): Spanned

    /** Sets the content of [textView] to [text]. */
    fun setText(textView: TextView, text: Spannable)
}

/**
 * Sets content by parsing it as Mastodon HTML.
 */
object SetContentAsMastodonHtml : SetContent {
    override fun parseToSpanned(
        textView: TextView,
        content: CharSequence,
        removeQuoteInline: Boolean,
        tagHandler: PachliTagHandler?,
    ): Spanned {
        return if (removeQuoteInline) {
            content.removeQuoteInline().parseAsMastodonHtml(tagHandler = tagHandler ?: MastodonTagHandler(textView))
        } else {
            content.parseAsMastodonHtml(tagHandler = tagHandler ?: MastodonTagHandler(textView))
        }
    }

    override fun setText(textView: TextView, text: Spannable) {
        // HTML like this:
        //
        // <ul>
        //   <li>one</li>
        //   <li>two</li>
        //   <li><ul><li>a</li><li>b</li></ul></li>
        // </ul>
        //
        // will have created nested LeadingMarginWithTextSpans for the third item, the
        // nested list.
        //
        // This is a problem, because the nested LeadingMarginWithTextSpans will each
        // draw their own marker. So the output for the above looks like:
        //
        // ```
        //  * one
        //  * two
        //  *  * a
        //  *  * b
        // ```
        //
        // To fix this (hack) update `computeMarginText` in the outermost spans to return
        // the empty string.

        // First, find all spans that share a start index.
        //
        // key: Index of the start of the span in `text`.
        // value: List<Span>, a list of all spans starting at `key`, sorted, shortest span
        // first.
        val spansSharingStartIndex = text.getSpans<LeadingMarginWithTextSpan>(0, text.length)
            .groupBy { text.getSpanStart(it) }
            .filter { it.value.size != 1 }
            .mapValues { it.value.sortedBy { text.getSpanEnd(it) } }

        // Then update the margin text of all spans except the first (which is the shortest).
        spansSharingStartIndex.values.forEach { spans ->
            spans.drop(1).forEach { span -> span.computeMarginText = { "" } }
        }

        textView.text = text
    }
}

/**
 * Sets content by parsing it as Markdown.
 */
class SetContentAsMarkdown(context: Context) : SetContent {
    val textSize: Float

    init {
        val typedValue = TypedValue()
        val displayMetrics = context.resources.displayMetrics
        context.theme.resolveAttribute(R.attr.status_text_medium, typedValue, true)
        textSize = typedValue.getDimension(displayMetrics)
    }

    private val markwon = MarkwonFactory.makeMarkwon(context, textSize)

    override fun parseToSpanned(textView: TextView, content: CharSequence, removeQuoteInline: Boolean, tagHandler: PachliTagHandler?): Spanned {
        return markwon.toMarkdown(if (removeQuoteInline) content.removeQuoteInline() else content.toString())
    }

    override fun setText(textView: TextView, text: Spannable) {
        markwon.setParsedMarkdown(textView, text)
    }
}
