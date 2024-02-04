
import app.pachli.core.activity.RingBuffer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

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

class RingBufferTest {
    @Test
    fun initialSize() {
        val buffer = RingBuffer<Int>(10)
        assertThat(buffer.size).isEqualTo(0)
    }

    @Test
    fun `size increases when there is capacity`() {
        val buffer = RingBuffer<Int>(10)
        buffer.add(1)
        assertThat(buffer.size).isEqualTo(1)
    }

    @Test
    fun `size is bounded by capacity`() {
        val buffer = RingBuffer<Int>(10)
        repeat(20) { buffer.add(it) }
        assertThat(buffer.size).isEqualTo(10)
    }

    @Test
    fun `inserting elements cycles around`() {
        val buffer = RingBuffer<Int>(3)
        buffer.add(1)
        assertThat(buffer[0]).isEqualTo(1)

        buffer.add(2)
        assertThat(buffer[0]).isEqualTo(1)
        assertThat(buffer[1]).isEqualTo(2)

        buffer.add(3)
        assertThat(buffer[0]).isEqualTo(1)
        assertThat(buffer[1]).isEqualTo(2)
        assertThat(buffer[2]).isEqualTo(3)

        // Exceed capacity, first item added is dropped
        buffer.add(4)
        assertThat(buffer[0]).isEqualTo(2)
        assertThat(buffer[1]).isEqualTo(3)
        assertThat(buffer[2]).isEqualTo(4)
    }

    @Test
    fun `toList() returns expected list`() {
        val buffer = RingBuffer<Int>(3)
        repeat(3) { buffer.add(it) }

        assertThat(buffer.toList()).isEqualTo(listOf(0, 1, 2))

        buffer.add(3)
        assertThat(buffer.toList()).isEqualTo(listOf(1, 2, 3))
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun getZeroSizeException() {
        RingBuffer<Int>(1)[0]
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun getIndexOutOfBounds() {
        val buffer = RingBuffer<Int>(1)
        buffer.add(1)
        buffer[10]
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun getIndexNegative() {
        val buffer = RingBuffer<Int>(1)
        buffer.add(1)
        buffer[-1]
    }
}
