/* Copyright 2017 Andrew Dawson
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

/**
 * @return true if list is null or else return list.isEmpty()
 */
fun isEmpty(list: List<*>?): Boolean {
    return list == null || list.isEmpty()
}

/**
 * @return a new ArrayList containing the elements without duplicates in the same order
 */
fun <T> removeDuplicates(list: List<T>): ArrayList<T> {
    val set = LinkedHashSet(list)
    return ArrayList(set)
}
