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

package app.pachli.core.common.util

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AbsoluteTimeFormatterTest {
    companion object {
        /** Default locale before this test started */
        private lateinit var locale: Locale

        /**
         * Ensure the Locale is ENGLISH so that tests against literal strings like
         * "Apr" later, even if the test host's locale is e.g. FRENCH which would
         * normally report "avr.".
         */
        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            locale = Locale.getDefault()
            Locale.setDefault(Locale.ENGLISH)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            Locale.setDefault(locale)
        }
    }

    private val formatter = AbsoluteTimeFormatter(TimeZone.getTimeZone("UTC"))
    private val now = Date.from(Instant.parse("2022-04-11T00:00:00.00Z"))

    @Test
    fun `null handling`() {
        assertEquals("??", formatter.format(null, true, now))
        assertEquals("??", formatter.format(null, false, now))
    }

    @Test
    fun `same day formatting`() {
        val tenTen = Date.from(Instant.parse("2022-04-11T10:10:00.00Z"))
        assertEquals("10:10", formatter.format(tenTen, true, now))
        assertEquals("10:10", formatter.format(tenTen, false, now))
    }

    @Test
    fun `same year formatting`() {
        val nextDay = Date.from(Instant.parse("2022-04-12T00:10:00.00Z"))
        assertEquals("12 Apr, 00:10", formatter.format(nextDay, true, now))
        assertEquals("12 Apr, 00:10", formatter.format(nextDay, false, now))
        val endOfYear = Date.from(Instant.parse("2022-12-31T23:59:00.00Z"))
        assertEquals("31 Dec, 23:59", formatter.format(endOfYear, true, now))
        assertEquals("31 Dec, 23:59", formatter.format(endOfYear, false, now))
    }

    @Test
    fun `other year formatting`() {
        val firstDayNextYear = Date.from(Instant.parse("2023-01-01T00:00:00.00Z"))
        assertEquals("2023-01-01", formatter.format(firstDayNextYear, true, now))
        assertEquals("2023-01-01 00:00", formatter.format(firstDayNextYear, false, now))
        val inTenYears = Date.from(Instant.parse("2032-04-11T10:10:00.00Z"))
        assertEquals("2032-04-11", formatter.format(inTenYears, true, now))
        assertEquals("2032-04-11 10:10", formatter.format(inTenYears, false, now))
    }
}
