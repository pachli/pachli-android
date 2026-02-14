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

package app.pachli.components.compose

import app.pachli.core.testing.fakes.FakeSpannable
import app.pachli.util.highlightSpans
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
class StatusLengthTest(
    private val text: String,
    private val expectedLength: Int,
) {
    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        @JvmStatic
        fun data(): Iterable<Any> {
            return listOf(
                arrayOf<Any>("", 0),
                arrayOf<Any>(" ", 1),
                arrayOf<Any>("123", 3),
                // "@user@server" should be treated as "@user"
                arrayOf<Any>("123 @example@example.org", 12),
                // URLs under 23 chars are treated as 23 chars
                arrayOf<Any>("123 http://example.org", 27),
                // URLs over 23 chars are treated as 23 chars
                arrayOf<Any>("123 http://urlthatislongerthan23characters.example.org", 27),
                // URLs end when they should (the ")." should be part of the status
                // length, not considered to be part of the URL)
                arrayOf<Any>("test (https://example.com). test", 36),
                // Short hashtags are treated as is
                arrayOf<Any>("123 #basictag", 13),
                // Long hashtags are *also* treated as is (not treated as 23, like URLs)
                arrayOf<Any>("123 #atagthatislongerthan23characters", 37),
            )
        }
    }

    @Test
    fun statusLength_matchesExpectations() {
        val spannedText = FakeSpannable(text)
        highlightSpans(spannedText, 0)

        assertEquals(
            expectedLength,
            ComposeViewModel.statusLength(spannedText, "", 23),
        )
    }

    @Test
    fun statusLength_withCwText_matchesExpectations() {
        val spannedText = FakeSpannable(text)
        highlightSpans(spannedText, 0)

        val cwText = FakeSpannable(
            "a @example@example.org #hashtagmention and http://example.org URL",
        )
        assertEquals(
            expectedLength + cwText.length,
            ComposeViewModel.statusLength(spannedText, cwText.toString(), 23),
        )
    }
}
