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

package app.pachli.util

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_UP
import android.view.View
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import app.pachli.R
import app.pachli.core.activity.EmojiSpan
import app.pachli.core.network.model.HashTag
import app.pachli.core.network.model.Status.Mention
import app.pachli.interfaces.LinkListener
import com.mikepenz.iconics.IconicsColor
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.IconicsSize
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.color
import com.mikepenz.iconics.utils.size
import java.lang.ref.WeakReference

fun getDomain(urlString: String?): String {
    val host = urlString?.toUri()?.host ?: return ""
    return when {
        host.startsWith("www.") -> host.substring(4)
        else -> host
    }
}

/**
 * Finds links, mentions, and hashtags in a piece of text and makes them clickable, associating
 * them with callbacks to notify when they're clicked.
 *
 * @param view the returned text will be put in
 * @param content containing text with mentions, links, or hashtags
 * @param mentions any '@' mentions which are known to be in the content
 * @param listener to notify about particular spans that are clicked
 */
fun setClickableText(view: TextView, content: CharSequence, mentions: List<Mention>, tags: List<HashTag>?, listener: LinkListener) {
    val spannableContent = markupHiddenUrls(view, content)

    view.text = spannableContent.apply {
        getSpans(0, spannableContent.length, URLSpan::class.java).forEach {
            setClickableText(it, this, mentions, tags, listener)
        }
    }
    view.movementMethod = NoTrailingSpaceLinkMovementMethod.getInstance()
}

@VisibleForTesting
fun markupHiddenUrls(textView: TextView, content: CharSequence): SpannableStringBuilder {
    val spannableContent = SpannableStringBuilder(content)
    val originalSpans = spannableContent.getSpans(0, content.length, URLSpan::class.java)
    val obscuredLinkSpans = originalSpans.filter {
        val start = spannableContent.getSpanStart(it)
        val firstCharacter = content[start]
        return@filter if (firstCharacter == '#' || firstCharacter == '@') {
            false
        } else {
            val text = spannableContent.subSequence(start, spannableContent.getSpanEnd(it)).toString()
                .split(' ').lastOrNull().orEmpty()
            var textDomain = getDomain(text)
            if (textDomain.isBlank()) {
                textDomain = getDomain("https://$text")
            }
            getDomain(it.url) != textDomain
        }
    }

    if (obscuredLinkSpans.isNotEmpty()) {
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
            val end = spannableContent.getSpanEnd(span)
            val replacementText = context.getString(R.string.url_domain_notifier, getDomain(span.url))
            spannableContent.insert(end, replacementText)
        }

        var iconIndex = -1
        while (true) {
            iconIndex = spannableContent.indexOf("🔗", iconIndex + 1)
            if (iconIndex == -1) break

            // ImageSpan has bugs when trying to align the drawable with text, so use
            // EmojiSpan which centre-aligns it correctly. EmojiSpan default is to scale
            // the drawable to fill the text height, set scaleFactor to get a more
            // reasonable size.
            val linkSpan = EmojiSpan(WeakReference(textView)).apply {
                imageDrawable = iconLinkDrawable
                scaleFactor = 0.7f
            }
            spannableContent.setSpan(linkSpan, iconIndex, iconIndex + iconLength, 0)
        }
    }

    return spannableContent
}

@VisibleForTesting
fun setClickableText(
    span: URLSpan,
    builder: SpannableStringBuilder,
    mentions: List<Mention>,
    tags: List<HashTag>?,
    listener: LinkListener,
) = builder.apply {
    val start = getSpanStart(span)
    val end = getSpanEnd(span)
    val flags = getSpanFlags(span)
    val text = subSequence(start, end)

    val customSpan = when (text[0]) {
        '#' -> getCustomSpanForTag(text, tags, span, listener)
        '@' -> getCustomSpanForMention(mentions, span, listener)
        else -> null
    } ?: object : NoUnderlineURLSpan(span.url) {
        override fun onClick(view: View) = listener.onViewUrl(url)
    }

    removeSpan(span)
    setSpan(customSpan, start, end, flags)
}

@VisibleForTesting
fun getTagName(text: CharSequence, tags: List<HashTag>?): String? {
    val scrapedName = normalizeToASCII(text.subSequence(1, text.length)).toString()
    return when (tags) {
        null -> scrapedName
        else -> tags.firstOrNull { it.name.equals(scrapedName, true) }?.name
    }
}

private fun getCustomSpanForTag(text: CharSequence, tags: List<HashTag>?, span: URLSpan, listener: LinkListener): ClickableSpan? {
    return getTagName(text, tags)?.let {
        object : NoUnderlineURLSpan(span.url) {
            override fun onClick(view: View) = listener.onViewTag(it)
        }
    }
}

private fun getCustomSpanForMention(mentions: List<Mention>, span: URLSpan, listener: LinkListener): ClickableSpan? {
    // https://github.com/tuskyapp/Tusky/pull/2339
    return mentions.firstOrNull { it.url == span.url }?.let {
        getCustomSpanForMentionUrl(span.url, it.id, listener)
    }
}

private fun getCustomSpanForMentionUrl(url: String, mentionId: String, listener: LinkListener): ClickableSpan {
    return object : MentionSpan(url) {
        override fun onClick(view: View) = listener.onViewAccount(mentionId)
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
            val customSpan = getCustomSpanForMentionUrl(mention.url, mention.id, listener)
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
    view.movementMethod = NoTrailingSpaceLinkMovementMethod.getInstance()
}

fun createClickableText(text: String, link: String): CharSequence {
    return SpannableStringBuilder(text).apply {
        setSpan(NoUnderlineURLSpan(link), 0, text.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
    }
}

/**
 * [LinkMovementMethod] that doesn't add a leading/trailing clickable area.
 *
 * [LinkMovementMethod] has a bug in its calculation of the clickable width of a span on a line. If
 * the span is the last thing on the line the clickable area extends to the end of the view. So the
 * user can tap what appears to be whitespace and open a link.
 *
 * Fix this by overriding ACTION_UP touch events and calculating the true start and end of the
 * content on the line that was tapped. Then ignore clicks that are outside this area.
 *
 * See https://github.com/tuskyapp/Tusky/issues/1567.
 */
object NoTrailingSpaceLinkMovementMethod : LinkMovementMethod() {
    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        val action = event.action
        if (action != ACTION_UP) return super.onTouchEvent(widget, buffer, event)

        val x = event.x.toInt()
        val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
        val line = widget.layout.getLineForVertical(y)
        val lineLeft = widget.layout.getLineLeft(line)
        val lineRight = widget.layout.getLineRight(line)
        if (x > lineRight || x >= 0 && x < lineLeft) {
            return true
        }

        return super.onTouchEvent(widget, buffer, event)
    }

    fun getInstance() = NoTrailingSpaceLinkMovementMethod
}
