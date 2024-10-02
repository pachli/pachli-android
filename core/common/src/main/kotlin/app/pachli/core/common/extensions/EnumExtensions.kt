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

package app.pachli.core.common.extensions

import kotlin.enums.enumEntries

// Note: Technically these don't have to be extension methods on
// Enum.Companion. You could remove that and write:
//
//   get<SomeEnum>(0)
//
// But the bare "get" (getOrElse, getOrNull) in calling code strikes
// me as a little weird. Installing these on Enum.Companion makes
// the calling code:
//
//   Enum.get<SomeEnum>(0)
//
// which is a little more legible.

/**
 * Returns the enum constant with the given [ordinal] value.
 *
 * If the ordinal is out of bounds of this enum, throws
 * an IndexOutOfBoundsException except in Kotlin/JS where the behavior is unspecified.
 *
 * @see [kotlin.Array.get]
 */
inline fun <reified E : Enum<E>> Enum.Companion.get(ordinal: Int) = enumEntries<E>()[ordinal]

/**
 * Returns the enum constant with the given [ordinal] value or the result
 * of calling the [defaultValue] function if the [ordinal] is out of bounds of
 * this enum.
 */
inline fun <reified E : Enum<E>> Enum.Companion.getOrElse(ordinal: Int, defaultValue: (Int) -> E) = enumEntries<E>().getOrElse(ordinal, defaultValue)

/**
 * Returns the enum constant with the given [ordinal] value or `null` if the
 * [ordinal] is out of bounds of this enum
 */
inline fun <reified E : Enum<E>> Enum.Companion.getOrNull(ordinal: Int) = enumEntries<E>().getOrNull(ordinal)
