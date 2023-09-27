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

package app.pachli.network

import app.pachli.entity.InstanceV1
import app.pachli.network.model.InstanceV2
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.getOrElse
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersion
import kotlin.coroutines.cancellation.CancellationException

enum class ServerOperation(id: String) {
    // Translate a status, introduced in Mastodon 4.0.0
    ORG_JOINMASTODON_STATUSES_TRANSLATE("org.joinmastodon.statuses.translate")
}

enum class ServerKind {
    MASTODON,
    PLEROMA,
    // PIXELFED,  // Needs to report as missing notification support
    UNKNOWN;

    companion object {
        fun from(instance: InstanceV1) = if (instance.pleroma == null) MASTODON else PLEROMA

        fun from(instance: InstanceV2) = MASTODON
    }
}



sealed interface ServerCapabilitiesError {
    val throwable: Throwable

    data class VersionParse(override val throwable: Throwable): ServerCapabilitiesError
}

/** Represents operations that can be performed on the given server. */
class ServerCapabilities(
    val serverKind: ServerKind,
    val capabilities: Map<ServerOperation, List<Version>>
) {
    companion object {
        fun from(instance: InstanceV1): Result<ServerCapabilities, ServerCapabilitiesError> {
            val serverKind = ServerKind.from(instance)
            val capabilities = mutableMapOf<ServerOperation, List<Version>>()

            when (serverKind) {
                ServerKind.MASTODON -> {
                    val version = resultOf {
                        Version.parse(instance.version, strict = false)
                    }.getOrElse { return Err(ServerCapabilitiesError.VersionParse(it)) }

                    // Can translate?
                    if (version >= "4.0.0".toVersion()) {

                    }
                }
                ServerKind.PLEROMA -> TODO()
                ServerKind.UNKNOWN -> TODO()
            }

            return Ok(ServerCapabilities(serverKind, capabilities))
        }

        fun from(instance: InstanceV2): Result<ServerCapabilities, ServerCapabilitiesError> {
            val serverKind = ServerKind.from(instance)
            val capabilities = mutableMapOf<ServerOperation, List<Version>>()

            when (serverKind) {
                ServerKind.MASTODON -> {

                }
                else -> { /* TODO: Have a default set of capabilities */ }
            }

            return Ok(ServerCapabilities(serverKind, capabilities))
        }
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
    val successResult = getOr { null }// getOrNull()
    return when {
        successResult != null -> resultOf { transform(successResult) }
        else -> Err(getError() ?: error("Unreachable state"))
    }
}
