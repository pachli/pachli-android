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
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.getOr
import kotlin.coroutines.cancellation.CancellationException

/**
 * Base class for errors throughout the app.
 *
 * Derive new error class hierarchies for different components using a sealed
 * class hierarchy like so:
 *
 * ```kotlin
 * sealed class Error(
 *     @StringRes resourceId: Int,
 *     vararg formatArgs: String,
 *     source: PachliError? = null,
 * ) : PachliError(resourceId, *formatArgs, source = source) {
 *     data object SomeProblem : Error(R.string.error_some_problem)
 *     data class OutOfRange(val input: Int) : Error(
 *         R.string.error_out_of_range
 *         input,
 *     )
 *     data class Fetch(val url: String, val e: PachliError) : Error(
 *         R.string.error_fetch,
 *         url,
 *         source = e,
 *     )
 * }
 * ```
 *
 * In this example `SomeProblem` represents an error with no additional context,
 * `OtherProblem` is an error relating to a URL and the URL will be included in
 * the error message, and `WrappedError` represents an error that wraps another
 * error that was the actual cause.
 *
 * Possible string resources for those errors would be:
 *
 * ```xml
 * <string name="error_some_problem">Operation failed</string>
 * <string name="error_out_of_range">Value %1$d is out of range</string>
 * <string name="error_fetch">Could not fetch %1$s: %2$s</string>
 * ```
 *
 * In that last example the `url` parameter will be interpolated as the first
 * placeholder and the error message from the error passed as the `source`
 * parameter will be interpolated as the second placeholder.
 *
 * @property resourceId String resource ID for the error message
 * @property formatArgs 0 or more arguments to interpolate in to the string resource
 * @property source (optional) The underlying error that caused this error
 */
open class PachliError(
    @StringRes private val resourceId: Int,
    private vararg val formatArgs: String,
    val source: PachliError? = null,
) {
    fun msg(context: Context): String {
        val args = mutableListOf(*formatArgs)
        source?.let { args.add(it.msg(context)) }
        return context.getString(resourceId, *args.toTypedArray())
    }
}

// See https://www.jacobras.nl/2022/04/resilient-use-cases-with-kotlin-result-coroutines-and-annotations/

/**
 * Like [runCatching], but with proper coroutines cancellation handling. Also only catches [Exception] instead of [Throwable].
 *
 * Cancellation exceptions need to be rethrown. See https://github.com/Kotlin/kotlinx.coroutines/issues/1814.
 */
inline fun <R> resultOf(block: () -> R): Result<R, Exception> {
    return try {
        Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Err(e)
    }
}

/**
 * Like [runCatching], but with proper coroutines cancellation handling. Also only catches [Exception] instead of [Throwable].
 *
 * Cancellation exceptions need to be rethrown. See https://github.com/Kotlin/kotlinx.coroutines/issues/1814.
 */
inline fun <T, R> T.resultOf(block: T.() -> R): Result<R, Exception> {
    return try {
        Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Err(e)
    }
}

/**
 * Like [mapCatching], but uses [resultOf] instead of [runCatching].
 */
inline fun <R, T> Result<T, Exception>.mapResult(transform: (value: T) -> R): Result<R, Exception> {
    val successResult = getOr { null } // getOrNull()
    return when {
        successResult != null -> resultOf { transform(successResult) }
        else -> Err(getError() ?: error("Unreachable state"))
    }
}
