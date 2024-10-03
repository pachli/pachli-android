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

package app.pachli.core.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

class OperationCounter {
    private val _count: MutableStateFlow<Int> = MutableStateFlow(0)

    /** Count of outstanding operations. */
    val count = _count.asStateFlow()

    /**
     * Runs [block] incrementing the operation count before [block]
     * starts, decrementing it when [block] ends.
     *
     * ```kotlin
     * private val operationCounter: OperationCounter()
     * val operationCount = operationCounter.count
     *
     * suspend fun foo(): SomeType = operationCounter {
     *     some_network_operation()
     * }
     * ```
     *
     * @return Whatever [block] returned
     */
    suspend operator fun <R> invoke(block: suspend () -> R): R {
        _count.getAndUpdate { it + 1 }
        val result = block.invoke()
        _count.getAndUpdate { it - 1 }
        return result
    }
}
