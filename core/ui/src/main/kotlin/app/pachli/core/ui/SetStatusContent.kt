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
import android.graphics.Color
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import android.util.TypedValue
import android.widget.TextView
import androidx.core.text.method.LinkMovementMethodCompat
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.designsystem.R
import app.pachli.core.model.Emoji
import app.pachli.core.model.HashTag
import app.pachli.core.model.Status
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.network.removeQuoteInline
import app.pachli.core.ui.PreProcessMastodonHtml.processMarkdown
import com.bumptech.glide.RequestManager
import com.google.android.material.color.MaterialColors
import com.mohamedrejeb.ksoup.entities.KsoupEntities
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.movement.MovementMethodPlugin
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import io.noties.prism4j.annotations.PrismBundle

/**
 * Interface for setting the content of a status.
 *
 * @see [SetStatusContent.invoke]
 */
interface SetStatusContent {
    /**
     * Processes [content] according to [statusDisplayOptions], embedding any [emojis],
     * [mentions], and [hashtags]. Clicks on these are sent to [listener]. Sets the
     * processed content as the [text][TextView.setText] on [textView].
     *
     * @param glide RequestManager used to load images.
     * @param textView
     * @param content
     * @param emojis
     * @param animateEmojis True if emojis should be animated.
     * @param mentions
     * @param hashtags
     * @param removeQuoteInline If true, remove `p` elements with a `quote-inline`
     * class.
     * @param listener
     */
    operator fun invoke(
        glide: RequestManager,
        textView: TextView,
        content: CharSequence,
        emojis: List<Emoji>,
        animateEmojis: Boolean,
        mentions: List<Status.Mention>,
        hashtags: List<HashTag>?,
        removeQuoteInline: Boolean,
        listener: LinkListener,
    )
}

/**
 * Sets status content by parsing it as Mastodon HTML.
 */
object SetMastodonHtmlContent : SetStatusContent {
    private val movementMethod = LinkMovementMethodCompat.getInstance()

    override operator fun invoke(
        glide: RequestManager,
        textView: TextView,
        content: CharSequence,
        emojis: List<Emoji>,
        animateEmojis: Boolean,
        mentions: List<Status.Mention>,
        hashtags: List<HashTag>?,
        removeQuoteInline: Boolean,
        listener: LinkListener,
    ) {
        invoke(
            glide,
            tagHandler = null,
            textView,
            content,
            emojis,
            animateEmojis,
            mentions,
            hashtags,
            removeQuoteInline,
            listener,
        )
    }

    operator fun invoke(
        glide: RequestManager,
        tagHandler: Html.TagHandler? = null,
        textView: TextView,
        content: CharSequence,
        emojis: List<Emoji>,
        animateEmojis: Boolean,
        mentions: List<Status.Mention>,
        hashtags: List<HashTag>?,
        removeQuoteInline: Boolean,
        listener: LinkListener,
    ) {
        val spannableStringBuilder = SpannableStringBuilder().apply {
            append(
                if (removeQuoteInline) {
                    content.removeQuoteInline().parseAsMastodonHtml(tagHandler = tagHandler)
                } else {
                    content.parseAsMastodonHtml(tagHandler = tagHandler)
                },
            )

            getSpans(0, length, URLSpan::class.java).forEach {
                convertUrlSpanToMoreSpecificType(it, this, mentions, hashtags, listener)
            }

            val contentTags = getSpans(0, length, HashtagSpan::class.java).map { it.hashtag }.toSet()
            val missingTags = hashtags.orEmpty().filterNot { contentTags.contains(it.name) }

            val spans = missingTags.map { tag ->
                HashtagSpan(tag.name, tag.url) { listener.onViewTag(tag.name) }
            }

            if (spans.isNotEmpty()) {
                append("\n\n")

                spans.forEachIndexed { index, span ->
                    val start = length
                    append("#${span.hashtag}".unicodeWrap())
                    val end = length
                    if (index < spans.size) append(" ")
                    setSpan(span, start, end, 0)
                }
            }

            emojify(glide, emojis, textView, animateEmojis)

            markupHiddenUrls(textView, this)
        }

        textView.text = spannableStringBuilder
        textView.movementMethod = movementMethod
    }
}

/**
 * Sets status content by parsing it as Markdown.
 */
@PrismBundle(includeAll = true, grammarLocatorClassName = ".MySuperGrammerLocator")
class SetMarkdownContent(context: Context) : SetStatusContent {
    val textSize: Float

    init {
        val typedValue = TypedValue()
        val displayMetrics = context.resources.displayMetrics
        context.theme.resolveAttribute(R.attr.status_text_medium, typedValue, true)
        textSize = typedValue.getDimension(displayMetrics)
    }

    private val markwon = Markwon.builder(context)
        .usePlugin(MovementMethodPlugin.create(LinkMovementMethodCompat.getInstance()))
        .usePlugin(HtmlPlugin.create())
        .usePlugin(SoftBreakAddsNewLinePlugin.create())
        .usePlugin(MarkwonInlineParserPlugin.create())
        .usePlugin(
            JLatexMathPlugin.create(textSize) {
                it.inlinesEnabled(true)
            },
        )
        .usePlugin(
            SyntaxHighlightPlugin.create(
                Prism4j(MySuperGrammerLocator()),
                Prism4jThemeDefault.create(),
            ),
        )
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(PreProcessMastodonHtml)
        // Has to be at the end of the list, see https://github.com/noties/Markwon/issues/494
        .usePlugin(PachliMarkwonTheme(context))
        .build()

    override operator fun invoke(
        glide: RequestManager,
        textView: TextView,
        content: CharSequence,
        emojis: List<Emoji>,
        animateEmojis: Boolean,
        mentions: List<Status.Mention>,
        hashtags: List<HashTag>?,
        removeQuoteInline: Boolean,
        listener: LinkListener,
    ) {
        val spannableStringBuilder = SpannableStringBuilder().apply {
            append(markwon.toMarkdown(if (removeQuoteInline) content.removeQuoteInline() else content.toString()))

            getSpans(0, length, URLSpan::class.java).forEach {
                convertUrlSpanToMoreSpecificType(it, this, mentions, hashtags, listener)
            }

            val contentTags = getSpans(0, length, HashtagSpan::class.java).map { it.hashtag }.toSet()
            val missingTags = hashtags.orEmpty().filterNot { contentTags.contains(it.name) }

            val spans = missingTags.map { tag ->
                HashtagSpan(tag.name, tag.url) { listener.onViewTag(tag.name) }
            }

            if (spans.isNotEmpty()) {
                append("\n\n")

                spans.forEachIndexed { index, span ->
                    val start = length
                    append("#${span.hashtag}".unicodeWrap())
                    val end = length
                    if (index < spans.size) append(" ")
                    setSpan(span, start, end, 0)
                }
            }

            emojify(glide, emojis, textView, animateEmojis)

            markupHiddenUrls(textView, this)
        }

        markwon.setParsedMarkdown(textView, spannableStringBuilder)
        textView.movementMethod = LinkMovementMethodCompat.getInstance()
    }
}

/**
 * Markwon theme plugin to set foreground and background colours for code blocks
 * (inline and fenced) from the app theme, to ensure code blocks are still legible
 * in light and dark themes.
 */
class PachliMarkwonTheme(private val context: Context) : AbstractMarkwonPlugin() {
    override fun configureTheme(builder: MarkwonTheme.Builder) {
        val codeBackgroundColor = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorSurfaceContainerLow,
            Color.WHITE,
        )
        val codeTextColor = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            Color.BLACK,
        )

        builder
            .codeBackgroundColor(codeBackgroundColor)
            .codeTextColor(codeTextColor)
            .codeBlockBackgroundColor(codeBackgroundColor)
            .codeBlockTextColor(codeTextColor)
    }
}

/**
 * Markwon plugin that pre-processes HTML from Mastodon before processing as Markdown.
 *
 * @see [processMarkdown]
 */
object PreProcessMastodonHtml : AbstractMarkwonPlugin() {
    /** Match the contents of a fenced code block. */
    private val rxCodeBlock =
        """^```(.*?)^```""".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))

    /** Match any start or end tag. */
    private val rxTag = """</?[^>]+?>""".toRegex(RegexOption.DOT_MATCHES_ALL)

    /** Match any inline code element (i.e., between two literal backquotes. */
    private val rxLiteral = """(?<!`)`(.+?)`""".toRegex(setOf(RegexOption.MULTILINE))

    /**
     * Match any opening `p` element, with optional whitespace before it, and optional
     * whitespace immediately after the opening tag.
     *
     * The optional whitespace after the tag is to catch `<p>   ...`, which would
     * appear as a code block because of the indentation.
     */
    private val rxPEl = """\s*<p\s*>\s*""".toRegex()

    /** Match `<br>`, `<br/>` (with optional spaces). */
    private val rxBrEl = """<br\s*/?>""".toRegex()

    /**
     * Match `~~~` at the start of a line. These are an alternative mechanism for
     * starting a fenced code block. They seem to be much less frequent than
     * using backticks.
     */
    private val rxThreeTilde = """^~~~""".toRegex(RegexOption.MULTILINE)

    /**
     * Processes [input] and removes/replaces some HTML content.
     *
     * Any Markdown in [input] will be wrapped in HTML block elements. Undo those,
     * so the content becomes "Markdown that possibly contains embedded HTML" and not
     * "HTML that might contain some Markdown directives".
     */
    override fun processMarkdown(input: String): String {
        // Known differences from Mastodon web rendering
        //
        // - Links in fenced code blocks are not clickable
        //   Eg., a hashtag in a fenced code block is not clickable
        val processed = input
            // Remove <p> with any preceeding whitespace (just in case a paragraph was
            // indented -- not removing the whitespace could cause it to be treated as
            // a code block).
            .replace(rxPEl, "")
            // </p> ends a paragraph, Markdown needs two blank lines as separators.
            .replace("</p>", "\n\n")
            // <br> and variations become "\n".
            .replace(rxBrEl, "\n")
            // Convert leading quote markers from entities to ">".
            .replace("&gt; ", "> ")
            // Three ~ at the start of a line are unlikely to be a fenced code block
            // and are more likely to be decoration. Escape them so they are not parsed
            // as code.
            //
            // https://dair-community.social/@emilymbender/114172441506624981
            .replace(rxThreeTilde, Regex.escapeReplacement("""\~\~\~"""))
            // Hack for Mathstodon. Mathstodon uses `\[...\]` for block latex content
            // and `\(...\)` for inline latex content (those are literal backslash and
            // bracket/brace characters). Rewrite those to block or inline level `$$`
            // strings so the JLatexMath plugin will parse them.
            .replace("""\[""", "\n\n$$\n")
            .replace("""\]""", "\n$$\n\n")
            .replace("""\(""", "$$")
            .replace("""\)""", "$$")
            // HTML in fenced code blocks is treated literally by Markwon.
            // So remove all HTML tags inside fenced blocks (keep the content).
            //
            // There may be HTML entities that need to be converted.
            .replace(rxCodeBlock) { KsoupEntities.decodeHtml(it.value.replace(rxTag, "")) }
            // Convert any HTML entities in inline code.
            .replace(rxLiteral) { KsoupEntities.decodeHtml(it.value) }

        return processed
    }
}
