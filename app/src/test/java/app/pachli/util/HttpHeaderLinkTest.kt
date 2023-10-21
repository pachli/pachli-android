/*
 * Copyright 2023 Pachli Association
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pachli.core.network.model.HttpHeaderLink
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HttpHeaderLinkTest {
    data class TestData(val name: String, val input: String, val want: List<HttpHeaderLink>)

    @Test
    fun shouldParseValidLinks() {
        val testData = arrayOf(
            // Examples from https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Link
            TestData(
                "Single URL",
                "<https://example.com>",
                listOf(HttpHeaderLink("https://example.com")),
            ),
            TestData(
                "Single URL with parameters",
                "<https://example.com>; rel=\"preconnect\"",
                listOf(HttpHeaderLink("https://example.com")),
            ),
            TestData(
                "Single encoded URL with parameters",
                "<https://example.com/%E8%8B%97%E6%9D%A1>; rel=\"preconnect\"",
                listOf(HttpHeaderLink("https://example.com/%E8%8B%97%E6%9D%A1")),
            ),
            TestData(
                "Multiple URLs, separated by commas",
                "<https://one.example.com>; rel=\"preconnect\", <https://two.example.com>; rel=\"preconnect\", <https://three.example.com>; rel=\"preconnect\"",
                listOf(
                    HttpHeaderLink("https://one.example.com"),
                    HttpHeaderLink("https://two.example.com"),
                    HttpHeaderLink("https://three.example.com"),
                ),
            ),
            // Examples from https://httpwg.org/specs/rfc8288.html#rfc.section.3.5
            TestData(
                "Single URL, multiple parameters",
                "<http://example.com/TheBook/chapter2>; rel=\"previous\"; title=\"previous chapter\"",
                listOf(HttpHeaderLink("http://example.com/TheBook/chapter2")),
            ),
            TestData(
                "Root resource",
                "</>; rel=\"http://example.net/foo\"",
                listOf(HttpHeaderLink("/")),
            ),
            TestData(
                "Terms and anchor",
                "</terms>; rel=\"copyright\"; anchor=\"#foo\"",
                listOf(HttpHeaderLink("/terms")),
            ),
            TestData(
                "Multiple URLs with parameter encoding",
                "</TheBook/chapter2>; rel=\"previous\"; title*=UTF-8'de'letztes%20Kapitel, </TheBook/chapter4>; rel=\"next\"; title*=UTF-8'de'n%c3%a4chstes%20Kapitel",
                listOf(
                    HttpHeaderLink("/TheBook/chapter2"),
                    HttpHeaderLink("/TheBook/chapter4"),
                ),
            ),
        )

        // Verify that the URLs are parsed correctly
        for (test in testData) {
            val links = HttpHeaderLink.parse(test.input)
            assertEquals("${test.name}: Same size", links.size, test.want.size)
            for (i in links.indices) {
                assertEquals(test.name, test.want[i].uri, links[i].uri)
            }
        }
    }
}
