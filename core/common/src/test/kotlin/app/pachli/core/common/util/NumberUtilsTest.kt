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

import java.util.Locale
import kotlin.math.pow
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class NumberUtilsTest(private val input: Long, private val want: String) {
    companion object {
        /** Default locale before this test started */
        private lateinit var locale: Locale

        /**
         * Ensure the Locale is ENGLISH so that tests against literal strings like
         * "1.0M" later, even if the test host's locale is e.g. GERMAN which would
         * normally report "1,0M".
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

        @Parameterized.Parameters(name = "formatNumber_{0}")
        @JvmStatic
        fun data(): Iterable<Any> {
            return listOf(
                arrayOf(0, "0"),
                arrayOf(1, "1"),
                arrayOf(-1, "-1"),
                arrayOf(999, "999"),
                arrayOf(1000, "1.0K"),
                arrayOf(1500, "1.5K"),
                arrayOf(-1500, "-1.5K"),
                arrayOf(1000.0.pow(2).toLong(), "1.0M"),
                arrayOf(1000.0.pow(3).toLong(), "1.0G"),
                arrayOf(1000.0.pow(4).toLong(), "1.0T"),
                arrayOf(1000.0.pow(5).toLong(), "1.0P"),
                arrayOf(1000.0.pow(6).toLong(), "1.0E"),
                arrayOf(3, "3"),
                arrayOf(35, "35"),
                arrayOf(350, "350"),
                arrayOf(3500, "3.5K"),
                arrayOf(-3500, "-3.5K"),
                arrayOf(3500 * 1000, "3.5M"),
                arrayOf(3500 * 1000.0.pow(2).toLong(), "3.5G"),
                arrayOf(3500 * 1000.0.pow(3).toLong(), "3.5T"),
                arrayOf(3500 * 1000.0.pow(4).toLong(), "3.5P"),
                arrayOf(3500 * 1000.0.pow(5).toLong(), "3.5E"),
            )
        }
    }

    @Test
    fun test() {
        Assert.assertEquals(want, formatNumber(input, 1000))
    }
}
