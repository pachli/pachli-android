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

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Maps this [Result<V, E>][Result] to [Result<V, E>][Result] by either applying the [transform]
 * function to the [value][Ok.value] if this [Result] is [Ok&lt;T>][Ok], or returning the result
 * unchanged.
 */
@OptIn(ExperimentalContracts::class)
inline infix fun <V, E, reified T : V> Result<V, E>.mapIfInstance(transform: (T) -> V): Result<V, E> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when (this) {
        is Ok -> (value as? T)?.let { Ok(transform(it)) } ?: this
        is Err -> this
    }
}

/**
 * Maps this [Result<V?, E>][Result] to [Result<V?, E>][Result] by either applying the [transform]
 * function to the [value][Ok.value] if this [Result] is [Ok] and non-null, or returning the
 * result unchanged.
 */
@OptIn(ExperimentalContracts::class)
inline infix fun <V, E> Result<V?, E>.mapIfNotNull(transform: (V) -> V): Result<V?, E> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when (this) {
        is Ok -> value?.let { Ok(transform(it)) } ?: this
        is Err -> this
    }
}
