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

/** Returns an [Int] byte count equal to this [Int] multiplied by the suffix */
inline val Int.KiB get() = this * 1024

/** Returns an [Int] byte count equal to this [Int] multiplied by the suffix */
inline val Int.MiB get() = this * 1024 * 1024

/** Returns a [Long] byte count equal to this [Long] multiplied by the suffix */
inline val Long.KiB get() = this * 1024

/** Returns a [Long] byte count equal to this [Long] multiplied by the suffix */
inline val Long.MiB get() = this * 1024 * 1024
