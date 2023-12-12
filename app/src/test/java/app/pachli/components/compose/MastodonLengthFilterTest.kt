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

import app.pachli.components.compose.ComposeActivity.Companion.MastodonLengthFilter
import app.pachli.components.compose.ComposeActivity.Companion.MastodonLengthFilter.Companion.ALLOW
import app.pachli.components.compose.ComposeActivity.Companion.MastodonLengthFilter.Companion.REJECT
import app.pachli.core.testing.fakes.FakeSpannable
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/**
 * Parameters to pass to [MastodonLengthFilter.filter] and the expected result.
 */
data class TestData(
    val source: CharSequence?,
    val start: Int,
    val end: Int,
    val dest: String,
    val dstart: Int,
    val dend: Int,
    /** The expected result */
    val want: String?,
)

@RunWith(Parameterized::class)
class MastodonLengthFilterTest(private val testData: TestData) {
    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun data(): Iterable<TestData> {
            return listOf(
                // Inserting 9 characters to empty string
                TestData(
                    "some text",
                    0,
                    9,
                    "",
                    0,
                    0,
                    ALLOW,
                ),
                // Appending 1 character to length 5 string
                TestData(
                    "6",
                    0,
                    1,
                    "12345",
                    0,
                    5,
                    ALLOW,
                ),
                // Replacing 1 character in middle of length 5 string
                TestData(
                    "x",
                    0,
                    1,
                    "12345",
                    2,
                    3,
                    ALLOW,
                ),
                // Replacing all characters in length 5 string
                TestData(
                    "xxxxx",
                    0,
                    4,
                    "12345",
                    0,
                    4,
                    ALLOW,
                ),
                // Deleting characters with a zero-length replacement
                TestData(
                    "",
                    0,
                    0,
                    "12345",
                    0,
                    3,
                    ALLOW,
                ),
                // Appending a 2 character emoji to a 9 character string
                TestData(
                    "😜",
                    0,
                    2,
                    "123456789",
                    9,
                    9,
                    ALLOW,
                ),
                // Extending a 10 character string by 1
                TestData(
                    "x",
                    0,
                    1,
                    "1234567890",
                    10,
                    10,
                    REJECT,
                ),
                // Extending a 9 character string by 2
                TestData(
                    "xx",
                    0,
                    2,
                    "123456789",
                    9,
                    9,
                    REJECT,
                ),
                // Replacing 2 characters in a 10 character string with 3
                TestData(
                    "xxx",
                    0,
                    3,
                    "1234567890",
                    5,
                    7,
                    REJECT,
                ),
                // Appending 2 x 2 character emojis to a 9 character string
                TestData(
                    "😜😜",
                    0,
                    3,
                    "123456789",
                    9,
                    9,
                    REJECT,
                ),
            )
        }
    }

    @Test
    fun filter_matchesExpectations() {
        val filter = MastodonLengthFilter(10)
        assertThat(
            filter.filter(
                testData.source,
                testData.start,
                testData.end,
                FakeSpannable(testData.dest),
                testData.dstart,
                testData.dend,
            ),
        ).isEqualTo(testData.want)
    }
}
