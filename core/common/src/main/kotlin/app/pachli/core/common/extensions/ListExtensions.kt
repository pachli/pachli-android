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

/**
 * Returns a single list of all elements from all iterables
 * in the given iterable.
 */
// kotlin.collections.flatten() doesn't work on a List<Any?>
// (e.g., as used in Adapter.onBindViewHolder) as it requires
// all members of the collection to have the same type T.
//
// This implementation flattens everything.
fun Iterable<*>.flatten(): List<*> {
    return buildList {
        fun innerFlatten(element: Any?) {
            when (element) {
                is Iterable<*> -> element.forEach { innerFlatten(it) }
                else -> add(element)
            }
        }

        this@flatten.forEach { innerFlatten(it) }
    }
}
