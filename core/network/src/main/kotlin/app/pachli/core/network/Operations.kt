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

package app.pachli.core.network

import app.pachli.core.network.ServerKind.MASTODON
import app.pachli.core.network.model.InstanceV1
import app.pachli.core.network.model.InstanceV2
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.mapError
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.constraints.Constraint
import io.github.z4kn4fein.semver.constraints.satisfiedByAny
import kotlin.collections.set
import kotlin.coroutines.cancellation.CancellationException

/**
 * Identifiers for operations that the server may or may not support.
 */
enum class ServerOperation(id: String) {
    // Translate a status, introduced in Mastodon 4.0.0
    ORG_JOINMASTODON_STATUSES_TRANSLATE("org.joinmastodon.statuses.translate"),
}

enum class ServerKind {
    MASTODON,
    AKKOMA,
    PLEROMA,
    UNKNOWN,

    ;

    companion object {
        private val rxVersion = """\(compatible; ([^ ]+) ([^)]+)\)""".toRegex()

        fun parse(vs: String): Result<Pair<ServerKind, Version>, ServerCapabilitiesError> = binding {
            // Parse instance version, which looks like "4.2.1 (compatible; Iceshrimp 2023.11)"
            // or it's a regular version number.
            val matchResult = rxVersion.find(vs)
            if (matchResult == null) {
                val version = resultOf {
                    Version.parse(vs, strict = false)
                }.mapError { ServerCapabilitiesError.VersionParse(it) }.bind()
                return@binding Pair(MASTODON, version)
            }

            val (software, unparsedVersion) = matchResult.destructured
            val version = resultOf {
                Version.parse(unparsedVersion, strict = false)
            }.mapError { ServerCapabilitiesError.VersionParse(it) }.bind()

            val s = when (software.lowercase()) {
                "akkoma" -> AKKOMA
                "mastodon" -> MASTODON
                "pleroma" -> PLEROMA
                else -> UNKNOWN
            }

            return@binding Pair(s, version)
        }
    }
}

/** Errors that can occur when processing server capabilities */
sealed interface ServerCapabilitiesError {
    val throwable: Throwable

    /** Could not parse the server's version string */
    data class VersionParse(override val throwable: Throwable) : ServerCapabilitiesError
}

/** Represents operations that can be performed on the given server. */
class ServerCapabilities(
    val serverKind: ServerKind = MASTODON,
    private val capabilities: Map<ServerOperation, List<Version>> = emptyMap(),
) {
    /**
     * Returns true if the server supports the given operation at the given minimum version
     * level, false otherwise.
     */
    fun can(operation: ServerOperation, constraint: Constraint) = capabilities[operation]?.let {
            versions ->
        constraint satisfiedByAny versions
    } ?: false

    companion object {
        /**
         * Generates [ServerCapabilities] from the instance's configuration report.
         */
        fun from(instance: InstanceV1): Result<ServerCapabilities, ServerCapabilitiesError> = binding {
            val (serverKind, _) = ServerKind.parse(instance.version).bind()
            val capabilities = mutableMapOf<ServerOperation, List<Version>>()

            // Create a default set of capabilities (empty). Mastodon servers support InstanceV2 from
            // v4.0.0 onwards, and there's no information about capabilities for other server kinds.

            ServerCapabilities(serverKind, capabilities)
        }

        /**
         * Generates [ServerCapabilities] from the instance's configuration report.
         */
        fun from(instance: InstanceV2): Result<ServerCapabilities, ServerCapabilitiesError> = binding {
            val (serverKind, _) = ServerKind.parse(instance.version).bind()
            val capabilities = mutableMapOf<ServerOperation, List<Version>>()

            when (serverKind) {
                MASTODON -> {
                    if (instance.configuration.translation.enabled) {
                        capabilities[ServerOperation.ORG_JOINMASTODON_STATUSES_TRANSLATE] = listOf(Version(major = 1))
                    }
                }
                else -> { /* nothing to do yet */ }
            }

            ServerCapabilities(serverKind, capabilities)
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
    val successResult = getOr { null } // getOrNull()
    return when {
        successResult != null -> resultOf { transform(successResult) }
        else -> Err(getError() ?: error("Unreachable state"))
    }
}
