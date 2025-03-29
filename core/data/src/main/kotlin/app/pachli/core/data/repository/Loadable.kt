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

package app.pachli.core.data.repository

import app.pachli.core.data.repository.Loadable.Loaded
import app.pachli.core.data.repository.Loadable.Loading
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Generic interface for loadable content.
 *
 * Does not represent a failure state, use [Result<Loadable, SomeError>][Result]
 * for that.
 */
// Note: Experimental for the moment.
sealed interface Loadable<out T> {
    /** Data is loading. */
    data object Loading : Loadable<Nothing>

    /** Data is loaded. */
    data class Loaded<T>(val data: T) : Loadable<T>
}

@OptIn(ExperimentalContracts::class)
fun <T> Loadable<T>.get(): T? {
    contract {
        returnsNotNull() implies (this@get is Loaded<T>)
        returns(null) implies (this@get is Loading)
    }

    return when (this) {
        is Loaded<T> -> this.data
        is Loading -> null
    }
}
