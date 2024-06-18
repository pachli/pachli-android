/*
 * Copyright 2023 Pachli Association
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

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Returns a flow that mirrors the original flow, but filters out values that occur within
 * [timeout] of the previously emitted value. The first value is always emitted.
 *
 * Example:
 *
 * ```kotlin
 * flow {
 *     emit(1)
 *     delay(90.milliseconds)
 *     emit(2)
 *     delay(90.milliseconds)
 *     emit(3)
 *     delay(1010.milliseconds)
 *     emit(4)
 *     delay(1010.milliseconds)
 *     emit(5)
 * }.throttleFirst(1000.milliseconds)
 * ```
 *
 * produces the following emissions.
 *
 * ```text
 * 1, 4, 5
 * ```
 *
 * @see kotlinx.coroutines.flow.debounce(Duration)
 * @param timeout Emissions within this duration of the last emission are filtered.
 *     Defaults to [DEFAULT_THROTTLE_FIRST_TIMEOUT] if omitted.
 * @param timeSource Used to measure elapsed time. Normally only overridden in tests
 */
fun <T> Flow<T>.throttleFirst(
    timeout: Duration = DEFAULT_THROTTLE_FIRST_TIMEOUT,
    timeSource: TimeSource = TimeSource.Monotonic,
) = flow {
    var marker: TimeMark? = null
    collect {
        if (marker == null || marker!!.elapsedNow() >= timeout) {
            emit(it)
            marker = timeSource.markNow()
        }
    }
}

private val DEFAULT_THROTTLE_FIRST_TIMEOUT = 500.milliseconds

/*
 * Copyright 2022 Christophe Beyls
 *
 * This file is copied from
 * https://github.com/cbeyls/fosdem-companion-android/blob/c70a681f1ed7d25890636ecd149dcbd4950b2df1/app/src/main/java/be/digitalia/fosdem/flow/FlowExt.kt#L4
 *
 * and is based on work he describes in
 * https://bladecoder.medium.com/smarter-shared-kotlin-flows-d6b75fc66754.
 *
 * In personal communication Christophe wrote:
 *
 * """
 * [...] the code, for which I claim no ownership. You can use and modify and
 * redistribute it all you want including in commercial projects, without
 * attribution.
 * """
 *
 * The fosdem-companion-android repository is under the Apache 2.0 license.
 */

@JvmInline
value class SharedFlowContext(private val subscriptionCount: StateFlow<Int>) {
    /**
     * A shared flow that does not cancel collecting the upstream flow after
     * a state (lifecycle) change.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> Flow<T>.flowWhileShared(started: SharingStarted): Flow<T> {
        return started.command(subscriptionCount)
            .distinctUntilChanged()
            .flatMapLatest {
                when (it) {
                    SharingCommand.START -> this
                    SharingCommand.STOP,
                    SharingCommand.STOP_AND_RESET_REPLAY_CACHE,
                    -> emptyFlow()
                }
            }
    }
}

inline fun <T> stateFlow(
    scope: CoroutineScope,
    initialValue: T,
    producer: SharedFlowContext.() -> Flow<T>,
): StateFlow<T> {
    val state = MutableStateFlow(initialValue)
    producer(SharedFlowContext(state.subscriptionCount)).launchIn(scope, state)
    return state.asStateFlow()
}

fun <T> Flow<T>.launchIn(scope: CoroutineScope, collector: FlowCollector<T>): Job = scope.launch {
    collect(collector)
}

inline fun <T> countSubscriptionsFlow(producer: SharedFlowContext.() -> Flow<T>): Flow<T> {
    val subscriptionCount = MutableStateFlow(0)
    return producer(SharedFlowContext(subscriptionCount.asStateFlow()))
        .countSubscriptionsTo(subscriptionCount)
}

fun <T> Flow<T>.countSubscriptionsTo(subscriptionCount: MutableStateFlow<Int>): Flow<T> {
    return flow {
        subscriptionCount.update { it + 1 }
        try {
            collect(this)
        } finally {
            subscriptionCount.update { it - 1 }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> SharedFlowContext.versionedResourceFlow(
    version: Flow<Int>,
    producer: suspend (version: Int) -> T,
): Flow<T> {
    return version
        .flowWhileShared(SharingStarted.WhileSubscribed())
        .distinctUntilChanged()
        .mapLatest(producer)
}
