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
import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import androidx.core.text.getSpans
import androidx.core.text.parseAsHtml
import androidx.test.core.app.ApplicationProvider
import app.pachli.core.common.string.trimTrailingWhitespace
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@HiltAndroidTest
@RunWith(Parameterized::class)
class MastodonTagHandlerTest(private val testData: TestData) {
    @get:Rule(order = 0)
    var hilt = HiltAndroidRule(this)

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val tagHandler = MastodonTagHandler(context)

    data class TestData(val src: String, val want: String)

    /**
     * Asserts that HTML in [TestData.src] is converted to spans that look
     * like [TestData.want].
     */
    @Test
    fun parsesCorrectly() {
        val got = tagHandler.rewriteHtml(testData.src)
            .parseAsHtml(tagHandler = tagHandler)
            .trimTrailingWhitespace()
            .toXmlLike()

        assertThat(got).isEqualTo(testData.want)
    }

    companion object {
        @Parameterized.Parameters()
        @JvmStatic
        fun data(): Iterable<TestData> {
            return listOf(
                TestData(
                    "<p>hello, world</p>",
                    "hello, world",
                ),
                TestData(
                    "<p><code>println()</code></p><p>That was code.</p>",
                    "<s:CodeSpan family=\"monospace\">println()</s:CodeSpan>\n\nThat was code.",
                ),
                // `pre` should be in monospace.
                TestData(
                    "<pre>hello, world</pre>",
                    """<s:TypefaceSpan family="monospace">hello, world</s:TypefaceSpan>""",
                ),
                // `pre` should preserve internal whitespace using non-breaking spaces.
                TestData(
                    "<pre>hello\n world</pre>",
                    "<s:TypefaceSpan family=\"monospace\">hello\n world</s:TypefaceSpan>",
                ),
                // `blockquote` at the start of the content should end at the
                // correct place.
                //
                // This checks that the workaround for a specific Android bug is working
                // correctly. The Android HTML parser does not parse custom elements at the
                // start of the content correctly, and acts as though their closing element
                // is at the end of the content, instead of wherever the closing element
                // actually is. MastodonTagHandler.rewriteHtml() contains a fix for this,
                // this confirms the fix is working.
                TestData(
                    "<blockquote>A quote</blockquote><p>hello, world</p>",
                    "<s:BlockQuoteSpan>A quote\n</s:BlockQuoteSpan>\nhello, world",
                ),
                // Check everything.
                TestData(
                    """
<p>Test1.</p><p><em>italic</em></p><p><strong>bold</strong></p><p><em><strong>bolditalic</strong></em></p><p><code>preformatted</code></p><pre><code>preformatted_code block
this pipe symbol here | should
line up with this one |</code></pre><ul><li>unordered</li><li>list</li></ul><ol><li>ordered</li><li>list</li></ol><blockquote><p>this is a<br>quote<br>block</p></blockquote>
                    """.trimIndent(),
                    """
Test1.

<s:StyleSpan style="italic">italic</s:StyleSpan>

<s:StyleSpan style="bold">bold</s:StyleSpan>

<s:StyleSpan style="bold"><s:StyleSpan style="italic">bolditalic</s:StyleSpan></s:StyleSpan>

<s:CodeSpan family="monospace">preformatted</s:CodeSpan>

<s:CodeSpan family="monospace"><s:TypefaceSpan family="monospace">preformatted_code block
this pipe symbol here | should
line up with this one |</s:CodeSpan></s:TypefaceSpan>

<s:LeadingMarginWithTextSpan text="•">unordered
</s:LeadingMarginWithTextSpan><s:LeadingMarginWithTextSpan text="•">list
</s:LeadingMarginWithTextSpan>
<s:LeadingMarginWithTextSpan text="1. ">ordered
</s:LeadingMarginWithTextSpan><s:LeadingMarginWithTextSpan text="2. ">list
</s:LeadingMarginWithTextSpan>
<s:BlockQuoteSpan>this is a
quote
block</s:BlockQuoteSpan>
                    """.trimIndent(),
                ),
            )
        }
    }
}

/**
 * Converts [Spanned] to an XML-like string, with `<s:...>` and `</s:...>`
 * tags to mark where spans start and end.
 */
private fun Spanned.toXmlLike(): String {
    val spanned = this
    var pos = 0

    return buildString {
        while (true) {
            val spans = spanned.getSpans<Any>(pos, pos)

            // Close open spans before opening new spans.
            //
            // Paragraph spans must cover the whole paragraph, including
            // the final `\n`. This makes the output here a bit different
            // from HTML. Here, the output for a paragraph span will be
            // `...\n</s:...>`, not `...</s:...>\n`
            spans.forEach { if (spanned.getSpanEnd(it) == pos) append(it.endTag()) }
            spans.forEach { if (spanned.getSpanStart(it) == pos) append(it.startTag()) }

            if (pos == spanned.length) return@buildString

            val nextPos = spanned.nextSpanTransition(pos, spanned.length, Any::class.java)

            if (pos != nextPos) {
                append(spanned.subSequence(pos, nextPos))
            }

            pos = nextPos
        }
    }
}

/**
 * @return A start tag for [Any]. The default is `<s:{this.javaClass.simpleName}>`,
 * but specific classes have overrides to include additional information.
 */
private fun Any.startTag(): String {
    val tag = this.javaClass.simpleName
    return when (this) {
        is LeadingMarginWithTextSpan -> "<s:$tag text=\"${computeMarginText(1)}\">"
        is StyleSpan -> {
            val styleAttribute = when (style) {
                Typeface.NORMAL -> null
                Typeface.BOLD -> "bold"
                Typeface.ITALIC -> "italic"
                Typeface.BOLD_ITALIC -> "bolditalic"
                else -> "unknown"
            }?.let { " style=\"$it\"" } ?: ""
            "<s:$tag$styleAttribute>"
        }

        is TypefaceSpan -> "<s:$tag family=\"$family\">"
        else -> "<s:$tag>"
    }
}

/**
 * @return An end tag for [Any], `</s:{this.javaClass.simpleName}>`.
 */
private fun Any.endTag() = "</s:${this.javaClass.simpleName}>"
