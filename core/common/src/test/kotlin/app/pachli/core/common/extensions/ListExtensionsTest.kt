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

package app.pachli.core.common.extensions

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

data class TestData(
    val got: Iterable<*>,
    val want: Iterable<*>,
)

@RunWith(Parameterized::class)
class ListExtensionsTest(private val testData: TestData) {
    companion object {
        @Parameterized.Parameters()
        @JvmStatic
        fun data(): Iterable<Any> {
            return listOf(
                TestData(emptyList<Unit>(), emptyList<Unit>()),
                TestData(listOf(1), listOf(1)),
                TestData(listOf(1, 2, 3), listOf(1, 2, 3)),
                TestData(listOf(1, null, 2), listOf(1, null, 2)),
                // 1-level flattening
                TestData(listOf(listOf(1), listOf(2)), listOf(1, 2)),
                // 1-level flattening with null
                TestData(listOf(listOf(1), null, listOf(2)), listOf(1, null, 2)),
                // 1-level flattening, different types
                TestData(listOf(listOf(1), listOf("hello")), listOf(1, "hello")),
                // 1-level flattening, different types, with null
                TestData(listOf(listOf(1), listOf(null), listOf("hello")), listOf(1, null, "hello")),
                // 2-level flattening, different types, with null
                TestData(
                    got = listOf(
                        listOf(
                            1,
                            listOf(2, 3, null),
                        ),
                        listOf(null),
                        "hello",
                        listOf("world"),
                    ),
                    want = listOf(1, 2, 3, null, null, "hello", "world"),
                ),
                // Works on Sets
                TestData(setOf(1, 2, 3), listOf(1, 2, 3)),
                TestData(
                    got = setOf(
                        setOf(
                            1,
                            listOf(2, 3, null),
                        ),
                        setOf(null),
                        "hello",
                        listOf("world"),
                    ),
                    want = listOf(1, 2, 3, null, null, "hello", "world"),
                ),
            )
        }
    }

    @Test
    fun test() {
        assertThat(testData.got.flatten()).isEqualTo(testData.want)
    }
}
