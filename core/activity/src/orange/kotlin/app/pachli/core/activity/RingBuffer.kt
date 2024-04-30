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

package app.pachli.core.activity

import java.util.concurrent.atomic.AtomicInteger

/**
 * A ring buffer with space for [capacity] items. Adding new items to the buffer will
 * drop older items.
 */
class RingBuffer<T>(private val capacity: Int) : Iterable<T> {
    private val data: Array<Any?> = arrayOfNulls(capacity)
    private var nItems: Int = 0
    private var tail: Int = -1

    private val head: Int
        get() = if (nItems == data.size) (tail + 1) % nItems else 0

    /** Number of items in the buffer */
    val size: Int
        get() = nItems

    /** Add an item to the buffer, overwriting the oldest item in the buffer */
    fun add(item: T) {
        tail = (tail + 1) % data.size
        data[tail] = item
        if (nItems < data.size) nItems++
    }

    /** Get an item from the buffer */
    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): T = when {
        nItems == 0 || index > nItems || index < 0 -> throw IndexOutOfBoundsException("$index")
        nItems == data.size -> data[(head + index) % data.size]
        else -> data[index]
    } as T

    /** Buffer as a list. */
    fun toList(): List<T> = iterator().asSequence().toList()

    override fun iterator(): Iterator<T> = object : Iterator<T> {
        private val index: AtomicInteger = AtomicInteger(0)
        override fun hasNext(): Boolean = index.get() < size
        override fun next(): T = get(index.getAndIncrement())
    }
}
