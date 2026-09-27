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

package app.pachli.util

import java.util.Calendar
import java.util.Date
import org.junit.Assert
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Enclosed::class)
class DateExtensionsTest {
    @RunWith(Parameterized::class)
    class EqualByMinuteTest(private val data: Triple<Date, Date, Boolean>) {
        companion object {
            @Parameterized.Parameters
            @JvmStatic
            fun data(): Iterable<Any> {
                val c = Calendar.getInstance()
                c.set(Calendar.YEAR, 2026)
                c.set(Calendar.MONTH, Calendar.SEPTEMBER)
                c.set(Calendar.DAY_OF_MONTH, 26)
                c.set(Calendar.HOUR_OF_DAY, 1)
                c.set(Calendar.MINUTE, 0)
                c.set(Calendar.SECOND, 30)
                c.set(Calendar.MILLISECOND, 0)

                val start = c.time

                return listOf(
                    // Different hour is not equal.
                    Triple(
                        start,
                        (c.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 2) }.time,
                        false,
                    ),
                    // Different clock minute, but within 60 seconds of the original
                    // is not equal.
                    Triple(
                        start,
                        (c.clone() as Calendar).apply {
                            set(Calendar.MINUTE, 1)
                            set(Calendar.SECOND, 0)
                        }.time,
                        false,
                    ),
                    // Within the same clock minute is equal.
                    Triple(
                        start,
                        (c.clone() as Calendar).apply { set(Calendar.SECOND, 12) }.time,
                        true,
                    ),
                )
            }
        }

        @Test
        fun equalByMinute() {
            Assert.assertEquals(data.first.equalByMinute(data.second), data.third)
        }
    }
}
