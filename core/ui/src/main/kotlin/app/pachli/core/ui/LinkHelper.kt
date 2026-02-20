/* Copyright 2017 Andrew Dawson
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

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.core.text.method.LinkMovementMethodCompat
import app.pachli.core.model.HashTag
import app.pachli.core.model.Status.Mention
import com.mikepenz.iconics.IconicsColor
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.IconicsSize
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.color
import com.mikepenz.iconics.utils.size

interface LinkListener {
    fun onViewTag(tag: String)
    fun onViewAccount(id: String)
    fun onViewUrl(url: String)
}

fun getDomain(urlString: String?): String {
    val host = urlString?.toUri()?.host ?: return ""
    return when {
        host.startsWith("www.") -> host.substring(4)
        else -> host
    }
}

/**
 * Show the destination URL for any [URLSpan] in [content] where the link text
 * does not show the destination.
 *
 * Mastodon doesn't allow people to post `a` elements with a custom destination,
 * so links always show the actual destination.
 *
 * That is not the case for statuses that may have originated from
 * non-Mastodon servers.
 *
 * Find those links and insert a "🔗" marker and the link's domain if the link's
 * content doesn't match the domain of the target.
 *
 * [URLSpan]s that start with `#` or `@` are ignored, these are links to hashtags
 * or mentions.
 *
 * @param textView The [TextView] the content will be displayed in, so the icon can
 * be adjusted to match the view's text size and color
 * @param content The [SpannableStringBuilder] that contains the content. The
 * content is modified in-place (rather than returning a new [SpannableStringBuilder]).
 */
internal fun markupHiddenUrls(textView: TextView, content: SpannableStringBuilder): SpannableStringBuilder {
    val obscuredLinkSpans = content
        .getSpans(0, content.length, URLSpan::class.java)
        .filterNot { it is HashtagSpan || it is MentionSpan }
        .filter {
            val start = content.getSpanStart(it)
            if (content[start] == '#' || content[start] == '@') return@filter false

            val text = content.subSequence(start, content.getSpanEnd(it)).toString()
                .split(' ').lastOrNull().orEmpty()

            var textDomain = getDomain(text)
            if (textDomain.isBlank()) {
                textDomain = getDomain("https://$text")
            }
            return@filter getDomain(it.url) != textDomain
        }

    if (obscuredLinkSpans.isEmpty()) return content

    val context = textView.context

    // Drawable to use to mark links. R.string.url_domain_notifier contains a Unicode emoji
    // ("🔗") that can render oddly depending on the user's choice of emoji set, so the emoji
    // is replaced with the drawable
    val iconLinkDrawable = IconicsDrawable(context, GoogleMaterial.Icon.gmd_open_in_new).apply {
        size = IconicsSize.px(textView.textSize)
        color = IconicsColor.colorInt(textView.currentTextColor)
    }
    val iconLength = "🔗".length

    for (span in obscuredLinkSpans) {
        val end = content.getSpanEnd(span)
        val additionalText = context.getString(R.string.url_domain_notifier, getDomain(span.url))
        content.insert(end, additionalText)

        // ImageSpan has bugs when trying to align the drawable with text, so use
        // EmojiSpan which centre-aligns it correctly. EmojiSpan default is to scale
        // the drawable to fill the text height, set scaleFactor to get a more
        // reasonable size.
        val linkSpan = EmojiSpan(textView).apply {
            imageDrawable = iconLinkDrawable
            scaleFactor = 0.7f
        }
        val iconIndex = end + 2
        content.setSpan(linkSpan, iconIndex, iconIndex + iconLength, 0)
    }

    return content
}

/**
 * Replaces [span] with a more appropriate span type based on the text contents
 * of [span].
 */
internal fun convertUrlSpanToMoreSpecificType(
    span: URLSpan,
    builder: SpannableStringBuilder,
    mentions: List<Mention>?,
    tags: List<HashTag>?,
    listener: LinkListener,
) = builder.apply {
    val start = getSpanStart(span)
    val end = getSpanEnd(span)
    val flags = getSpanFlags(span)

    // Determine the new span from the text content.
    val text = subSequence(start, end)
    val newSpan = when (text[0]) {
        '#' -> getCustomSpanForHashtag(text, tags, span, listener)
        '@' -> getCustomSpanForMention(mentions, span, listener)
        else -> NoUnderlineURLSpan(span.url, listener::onViewUrl)
    }

    // Replace the previous span with the more appropriate span.
    removeSpan(span)
    setSpan(newSpan, start, end, flags)

    // Insert Unicode directional isolates at the end and start of the span so that
    // "@foo" or "#foo" is rendered that way in RTL text, and not "foo@" or "foo#".
    //
    // Can't use CharSequence.unicodeWrap() here as that returns a String, which will
    // remove any spans (e.g., those inserted when viewing edits to a status) that
    // indicate where the added/removed text is, meaning that added/removed hashtags,
    // mentions, etc, wouldn't be highlighted in the diff view.
    insert(end, "\u2069")
    insert(start, "\u2068")
}

@VisibleForTesting
fun getTagName(text: CharSequence, tags: List<HashTag>?): String? {
    val scrapedName = text.subSequence(1, text.length).replaceAccents()
    val normalisedName = scrapedName.normaliseHashtag()
    return when (tags) {
        null -> scrapedName
        else -> tags.firstOrNull { it.name.normaliseHashtag() == normalisedName }?.name
    }
}

internal fun getCustomSpanForHashtag(text: CharSequence, tags: List<HashTag>?, span: URLSpan, listener: LinkListener): ClickableSpan? {
    return getTagName(text, tags)?.let { tagName ->
        HashtagSpan(tagName, span.url) { listener.onViewTag(tagName) }
    }
}

private fun getCustomSpanForMention(mentions: List<Mention>?, span: URLSpan, listener: LinkListener): ClickableSpan? {
    // https://github.com/tuskyapp/Tusky/pull/2339
    return mentions?.firstOrNull { it.url == span.url }?.let { mention ->
        MentionSpan(mention.url) { listener.onViewAccount(mention.id) }
    }
}

/**
 * Put mentions in a piece of text and makes them clickable, associating them with callbacks to
 * notify when they're clicked.
 *
 * @param view the returned text will be put in
 * @param mentions any '@' mentions which are known to be in the content
 * @param listener to notify about particular spans that are clicked
 */
fun setClickableMentions(view: TextView, mentions: List<Mention>?, listener: LinkListener) {
    if (mentions?.isEmpty() != false) {
        view.text = null
        return
    }

    view.text = SpannableStringBuilder().apply {
        var start = 0
        var end = 0
        var flags: Int
        var firstMention = true

        for (mention in mentions) {
            val customSpan = MentionSpan(mention.url) { listener.onViewAccount(mention.id) }
            end += 1 + mention.localUsername.length // length of @ + username
            flags = getSpanFlags(customSpan)
            if (firstMention) {
                firstMention = false
            } else {
                append(" ")
                start += 1
                end += 1
            }

            append("@")
            append(mention.localUsername)
            setSpan(customSpan, start, end, flags)
            start = end
        }
    }
    view.movementMethod = LinkMovementMethodCompat.getInstance()
}

fun createClickableText(text: String, link: String, onClickListener: OnClickListener): CharSequence {
    return SpannableStringBuilder(text).apply {
        setSpan(
            NoUnderlineURLSpan(link, onClickListener),
            0,
            text.length,
            Spanned.SPAN_INCLUSIVE_EXCLUSIVE,
        )
    }
}
