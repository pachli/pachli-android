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

package app.pachli.core.network.json

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import java.time.Instant
import java.util.Date
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests [InstantJsonAdapter] and [LenientRfc3339DateJsonAdapter]. They should both
 * parse the same JSON date strings the same way.
 */
@RunWith(Parameterized::class)
@OptIn(ExperimentalStdlibApi::class)
class Rfc3339DateTest(private val testData: TestData) {
    /**
     * Data for each test.
     *
     * @param name Test name.
     * @param json JSON string as input for the test.
     */
    data class TestData(val name: String, val json: String)

    private val moshi = Moshi.Builder()
        .add(InstantJsonAdapter())
        .add(LenientRfc3339DateJsonAdapter())
        .build()

    companion object {
        /** Expected [Instant] value after parsing [TestData.json]. */
        private val wantInstant = Instant.parse("1994-11-05T13:15:30.000Z")

        /** Expected [Date] value after parsing [TestData.json]. */
        private val wantDate = Date.from(wantInstant)

        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun data(): Iterable<TestData> {
            return listOf(
                TestData(
                    "RFC3339 date",
                    "\"1994-11-05T13:15:30.000Z\"",
                ),
                TestData(
                    "RFC3339 without subsecond precision",
                    "\"1994-11-05T13:15:30Z\"",
                ),
                TestData(
                    "RFC3339 with timezone as offset",
                    "\"1994-11-05T16:15:30+03:00\"",
                ),
                TestData(
                    "RFC3339 with missing timezone",
                    "\"1994-11-05T13:15:30\"",
                ),
            )
        }
    }

    @Test
    fun dateParses() {
        assertThat(moshi.adapter<Date>().fromJson(testData.json)).isEqualTo(wantDate)
    }

    @Test
    fun instantParses() {
        assertThat(moshi.adapter<Instant>().fromJson(testData.json)).isEqualTo(wantInstant)
    }
}
