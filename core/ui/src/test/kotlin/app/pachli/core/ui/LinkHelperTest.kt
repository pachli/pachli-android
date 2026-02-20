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

package app.pachli.core.ui

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.pachli.core.model.HashTag
import app.pachli.core.model.Status
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkHelperTest {
    private val listener = object : LinkListener {
        override fun onViewTag(tag: String) { }
        override fun onViewAccount(id: String) { }
        override fun onViewUrl(url: String) { }
    }

    private val mentions = listOf(
        Status.Mention("1", "https://example.com/@user", "user", "user"),
        Status.Mention("2", "https://example.com/@anotherUser", "anotherUser", "anotherUser"),
    )
    private val tags = listOf(
        HashTag("Pachli", "https://example.com/Tags/Pachli"),
        HashTag("mastodev", "https://example.com/Tags/mastodev"),
    )

    private val textView: TextView
        get() = TextView(InstrumentationRegistry.getInstrumentation().targetContext)

    private val context: Context
        get() = textView.context

    @Test
    fun whenSettingClickableText_mentionUrlsArePreserved() {
        val builder = SpannableStringBuilder()
        for (mention in mentions) {
            builder.append("@${mention.username}", URLSpan(mention.url), 0)
            builder.append(" ")
        }

        var urlSpans = builder.getSpans(0, builder.length, URLSpan::class.java)
        for (span in urlSpans) {
            convertUrlSpanToMoreSpecificType(span, builder, mentions, null, listener)
        }

        urlSpans = builder.getSpans(0, builder.length, URLSpan::class.java)
        for (span in urlSpans) {
            Assert.assertNotNull(mentions.firstOrNull { it.url == span.url })
        }
    }

    @Test
    fun whenSettingClickableText_nonMentionsAreNotConvertedToMentions() {
        val builder = SpannableStringBuilder()
        val nonMentionUrl = "http://example.com/"
        for (mention in mentions) {
            builder.append("@${mention.username}", URLSpan(nonMentionUrl), 0)
            builder.append(" ")
            builder.append("@${mention.username} ")
        }

        var urlSpans = builder.getSpans(0, builder.length, URLSpan::class.java)
        for (span in urlSpans) {
            convertUrlSpanToMoreSpecificType(span, builder, mentions, null, listener)
        }

        urlSpans = builder.getSpans(0, builder.length, URLSpan::class.java)
        for (span in urlSpans) {
            Assert.assertEquals(nonMentionUrl, span.url)
        }
    }

    @Test
    fun whenCheckingTags_tagNameIsComparedCaseInsensitively() {
        for (tag in tags) {
            for (mutatedTagName in listOf(tag.name, tag.name.uppercase(), tag.name.lowercase())) {
                val tagName = getTagName("#$mutatedTagName", tags)
                assertThat(tagName).isNotNull()
                assertThat(tags.firstOrNull { it.name == tagName }).isNotNull()
            }
        }
    }

    @Test
    fun whenCheckingTags_tagNameIsNormalized() {
        val mutator = "aeiou".toList().zip("åÉîøÜ".toList()).toMap()
        for (tag in tags) {
            val mutatedTagName = String(tag.name.map { mutator[it] ?: it }.toCharArray())
            val tagName = getTagName("#$mutatedTagName", tags)
            assertThat(tagName).isNotNull()
            assertThat(tags.firstOrNull { it.name == tagName }).isNotNull()
        }
    }

    @Test
    fun hashedUrlSpans_withNoMatchingTag_areNotModified() {
        for (tag in tags) {
            Assert.assertNull(getTagName("#not${tag.name}", tags))
        }
    }

    @Test
    fun whenTagsAreNull_tagNameIsGeneratedFromText() {
        for (tag in tags) {
            assertThat(tag.name).isEqualTo(getTagName("#${tag.name}", null))
        }
    }

    @Test
    fun whenStringIsInvalidUri_emptyStringIsReturnedFromGetDomain() {
        listOf(
            null,
            "foo bar baz",
            "http:/foo.bar",
            "c:/foo/bar",
        ).forEach {
            Assert.assertEquals("", getDomain(it))
        }
    }

    @Test
    fun whenUrlIsValid_correctDomainIsReturned() {
        listOf(
            "example.com",
            "localhost",
            "sub.domain.com",
            "10.45.0.123",
        ).forEach { domain ->
            listOf(
                "https://$domain",
                "https://$domain/",
                "https://$domain/foo/bar",
                "https://$domain/foo/bar.html",
                "https://$domain/foo/bar.html#",
                "https://$domain/foo/bar.html#anchor",
                "https://$domain/foo/bar.html?argument=value",
                "https://$domain/foo/bar.html?argument=value&otherArgument=otherValue",
            ).forEach { url ->
                Assert.assertEquals(domain, getDomain(url))
            }
        }
    }

    @Test
    fun wwwPrefixIsStrippedFromGetDomain() {
        mapOf(
            "https://www.example.com/foo/bar" to "example.com",
            "https://awww.example.com/foo/bar" to "awww.example.com",
            "http://www.localhost" to "localhost",
            "https://wwwexample.com/" to "wwwexample.com",
        ).forEach { (url, domain) ->
            Assert.assertEquals(domain, getDomain(url))
        }
    }

    @Test
    fun hiddenDomainsAreMarkedUp() {
        val displayedContent = "This is a good place to go"
        val maliciousDomain = "malicious.place"
        val maliciousUrl = "https://$maliciousDomain/to/go"
        val content = SpannableStringBuilder()
        content.append(displayedContent, URLSpan(maliciousUrl), 0)
        val oldContent = content.toString()
        Assert.assertEquals(
            "$displayedContent${context.getString(R.string.url_domain_notifier, maliciousDomain)}",
            markupHiddenUrls(textView, content).toString(),
        )
        Assert.assertEquals(oldContent, content.toString())
    }

    @Test
    fun fraudulentDomainsAreMarkedUp() {
        val displayedContent = "https://pachli.app/"
        val maliciousDomain = "malicious.place"
        val maliciousUrl = "https://$maliciousDomain/to/go"
        val content = SpannableStringBuilder()
        content.append(displayedContent, URLSpan(maliciousUrl), 0)
        Assert.assertEquals(
            "$displayedContent${context.getString(R.string.url_domain_notifier, maliciousDomain)}",
            markupHiddenUrls(textView, content).toString(),
        )
    }

    @Test
    fun multipleHiddenDomainsAreMarkedUp() {
        val domains = listOf("one.place", "another.place", "athird.place")
        val displayedContent = "link"
        val content = SpannableStringBuilder()
        for (domain in domains) {
            content.append(displayedContent, URLSpan("https://$domain/foo/bar"), 0)
        }

        val markedUpContent = markupHiddenUrls(textView, content)
        for (domain in domains) {
            Assert.assertTrue(markedUpContent.contains(context.getString(R.string.url_domain_notifier, domain)))
        }
    }

    @Test
    fun nonUriTextExactlyMatchingDomainIsNotMarkedUp() {
        val domain = "some.place"
        val content = SpannableStringBuilder()
            .append(domain, URLSpan("https://$domain/"), 0)
            .append(domain, URLSpan("https://$domain"), 0)
            .append(domain, URLSpan("https://www.$domain"), 0)
            .append("www.$domain", URLSpan("https://$domain"), 0)
            .append("www.$domain", URLSpan("https://$domain/"), 0)
            .append("$domain/", URLSpan("https://$domain/"), 0)
            .append("$domain/", URLSpan("https://$domain"), 0)
            .append("$domain/", URLSpan("https://www.$domain"), 0)

        val markedUpContent = markupHiddenUrls(textView, content)
        Assert.assertFalse(markedUpContent.contains("🔗"))
    }

    @Test
    fun spanEndsWithUrlIsNotMarkedUp() {
        val content = SpannableStringBuilder()
            .append("Some Place: some.place", URLSpan("https://some.place"), 0)
            .append("Some Place: some.place/", URLSpan("https://some.place/"), 0)
            .append("Some Place - https://some.place", URLSpan("https://some.place"), 0)
            .append("Some Place | https://some.place/", URLSpan("https://some.place/"), 0)
            .append("Some Place https://some.place/path", URLSpan("https://some.place/path"), 0)

        val markedUpContent = markupHiddenUrls(textView, content)
        Assert.assertFalse(markedUpContent.contains("🔗"))
    }

    @Test
    fun spanEndsWithFraudulentUrlIsMarkedUp() {
        val content = SpannableStringBuilder()
            .append("Another Place: another.place", URLSpan("https://some.place"), 0)
            .append("Another Place: another.place/", URLSpan("https://some.place/"), 0)
            .append("Another Place - https://another.place", URLSpan("https://some.place"), 0)
            .append("Another Place | https://another.place/", URLSpan("https://some.place/"), 0)
            .append("Another Place https://another.place/path", URLSpan("https://some.place/path"), 0)

        val markedUpContent = markupHiddenUrls(textView, content)
        val asserts = listOf(
            "Another Place: another.place",
            "Another Place: another.place/",
            "Another Place - https://another.place",
            "Another Place | https://another.place/",
            "Another Place https://another.place/path",
        )
        asserts.forEach { _ ->
            Assert.assertTrue(markedUpContent.contains(context.getString(R.string.url_domain_notifier, "some.place")))
        }
    }

    @Test
    fun validMentionsAreNotMarkedUp() {
        val builder = SpannableStringBuilder()
        for (mention in mentions) {
            builder.append("@${mention.username}", URLSpan(mention.url), 0)
            builder.append(" ")
        }

        val markedUpContent = markupHiddenUrls(textView, builder)
        for (mention in mentions) {
            Assert.assertFalse(markedUpContent.contains("${getDomain(mention.url)})"))
        }
    }

    @Test
    fun invalidMentionsAreNotMarkedUp() {
        val builder = SpannableStringBuilder()
        for (mention in mentions) {
            builder.append("@${mention.username}", URLSpan(mention.url), 0)
            builder.append(" ")
        }

        val markedUpContent = markupHiddenUrls(textView, builder)
        for (mention in mentions) {
            Assert.assertFalse(markedUpContent.contains("${getDomain(mention.url)})"))
        }
    }

    @Test
    fun validTagsAreNotMarkedUp() {
        val builder = SpannableStringBuilder()
        for (tag in tags) {
            builder.append("#${tag.name}", URLSpan(tag.url), 0)
            builder.append(" ")
        }

        val markedUpContent = markupHiddenUrls(textView, builder)
        for (tag in tags) {
            Assert.assertFalse(markedUpContent.contains("${getDomain(tag.url)})"))
        }
    }

    @Test
    fun invalidTagsAreNotMarkedUp() {
        val builder = SpannableStringBuilder()
        for (tag in tags) {
            builder.append("#${tag.name}", URLSpan(tag.url), 0)
            builder.append(" ")
        }

        val markedUpContent = markupHiddenUrls(textView, builder)
        for (tag in tags) {
            Assert.assertFalse(markedUpContent.contains("${getDomain(tag.url)})"))
        }
    }
}
