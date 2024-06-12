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

package app.pachli.core.common

import android.content.Context
import androidx.annotation.StringRes
import app.pachli.core.common.string.unicodeWrap

/**
 * Interface for errors throughout the app.
 *
 * Derive new error class hierarchies for different components using a sealed
 * class hierarchy like so:
 *
 * ```kotlin
 * sealed class Error(
 *     @StringRes override val resourceId: Int,
 *     override val formatArgs: Array<out String>,
 *     cause: PachliError? = null,
 * ) : PachliError {
 *     data object SomeProblem : Error(R.string.error_some_problem)
 *     data class OutOfRange(val input: Int) : Error(
 *         R.string.error_out_of_range, // "Value %1$d is out of range"
 *         input,
 *     )
 *     data class Fetch(val url: String, val cause: PachliError) : Error(
 *         R.string.error_fetch, // "Could not fetch %1$s: %2$s"
 *         url,
 *         cause = cause,
 *     )
 * }
 * ```
 *
 * In this example `SomeProblem` represents an error with no additional context.
 *
 * `OutOfRange` is an error relating to a single value with no underlying cause.
 * The value (`input`) will be inserted in the string at `%1$s`.
 *
 * `Fetch` is an error relating to a URL with an underlying cause. The URL will be
 * included in the error message at `%1$s`, and the string representation of the
 * cause will be included at `%2$s`.
 *
 * Possible string resources for those errors would be:
 *
 * ```xml
 * <string name="error_some_problem">Operation failed</string>
 * <string name="error_out_of_range">Value %1$d is out of range</string>
 * <string name="error_fetch">Could not fetch %1$s: %2$s</string>
 * ```
 */
interface PachliError {
    /** String resource ID for the error message. */
    @get:StringRes
    val resourceId: Int

    /** Arguments to be interpolated in to the string from [resourceId]. */
    val formatArgs: Array<out String>?

    /**
     * The cause of this error. If present the string representation of `cause`
     * will be set as the last format argument when formatting [resourceId].
     */
    val cause: PachliError?

    /**
     * @return A localised, unicode-wrapped error message for this error.
     */
    fun fmt(context: Context): String {
        val args = buildList {
            formatArgs?.let { addAll(it) }
            cause?.let { add(it.fmt(context)) }
        }
        return context.getString(resourceId, *args.toTypedArray()).unicodeWrap()
    }
}
