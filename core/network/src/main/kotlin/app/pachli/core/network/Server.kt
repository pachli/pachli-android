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

import androidx.annotation.StringRes
import app.pachli.core.common.PachliError
import app.pachli.core.common.resultOf
import app.pachli.core.network.Server.Error.UnparseableVersion
import app.pachli.core.network.ServerKind.AKKOMA
import app.pachli.core.network.ServerKind.GOTOSOCIAL
import app.pachli.core.network.ServerKind.MASTODON
import app.pachli.core.network.ServerKind.PLEROMA
import app.pachli.core.network.ServerKind.UNKNOWN
import app.pachli.core.network.ServerOperation.ORG_JOINMASTODON_FILTERS_CLIENT
import app.pachli.core.network.ServerOperation.ORG_JOINMASTODON_FILTERS_SERVER
import app.pachli.core.network.ServerOperation.ORG_JOINMASTODON_STATUSES_TRANSLATE
import app.pachli.core.network.model.InstanceV1
import app.pachli.core.network.model.InstanceV2
import app.pachli.core.network.model.nodeinfo.NodeInfo
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.mapError
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.constraints.Constraint
import io.github.z4kn4fein.semver.satisfies
import io.github.z4kn4fein.semver.toVersion
import kotlin.collections.set

/**
 * Identifiers for operations that the server may or may not support.
 */
enum class ServerOperation(id: String, versions: List<Version>) {
    /** Client-side filters */
    ORG_JOINMASTODON_FILTERS_CLIENT(
        "org.joinmastodon.filters.client",
        listOf(
            // Initial introduction in Mastodon 2.4.3
            Version(major = 1),
            // "account" context available in filter views in Mastodon 3.1.0
            Version(major = 1, minor = 1),
        ),
    ),

    /** Server-side filters */
    ORG_JOINMASTODON_FILTERS_SERVER(
        "org.joinmastodon.filters.server",
        listOf(
            // Intitial introduction in Mastodon 4.0.0
            Version(major = 1),
        ),
    ),

    /** Translate a status */
    ORG_JOINMASTODON_STATUSES_TRANSLATE(
        "org.joinmastodon.statuses.translate",
        listOf(
            // Initial introduction in Mastodon 4.0.0
            Version(major = 1),
            // Spoiler warnings, polls, and media descriptions are also translated in Mastodon 4.2.0
            Version(major = 1, minor = 1),
        ),
    ),
}

data class Server(
    val kind: ServerKind,
    val version: Version,
    private val capabilities: Map<ServerOperation, Version> = emptyMap(),
) {
    /**
     * @return true if the server supports the given operation at the given minimum version
     * level, false otherwise.
     */
    fun can(operation: ServerOperation, constraint: Constraint) = capabilities[operation]?.let {
            version ->
        version satisfies constraint
    } ?: false

    companion object {
        /**
         * Constructs a server from its [NodeInfo] and [InstanceV2] details.
         */
        fun from(software: NodeInfo.Software, instanceV2: InstanceV2): Result<Server, Error> = binding {
            val serverKind = ServerKind.from(software.name)
            val version = parseVersionString(serverKind, software.version).bind()
            val capabilities = capabilitiesFromServerVersion(serverKind, version)

            when (serverKind) {
                MASTODON -> {
                    if (instanceV2.configuration.translation.enabled) {
                        capabilities[ORG_JOINMASTODON_STATUSES_TRANSLATE] = when {
                            version >= "4.2.0".toVersion() -> "1.1.0".toVersion()
                            else -> "1.0.0".toVersion()
                        }
                    }
                }
                else -> { /* Nothing to do */ }
            }

            Server(serverKind, version, capabilities)
        }

        /**
         * Constructs a server from its [NodeInfo] and [InstanceV1] details.
         */
        fun from(software: NodeInfo.Software, instanceV1: InstanceV1): Result<Server, Error> = binding {
            val serverKind = ServerKind.from(software.name)
            val version = parseVersionString(serverKind, software.version).bind()
            val capabilities = capabilitiesFromServerVersion(serverKind, version)

            Server(serverKind, version, capabilities)
        }

        /**
         * Parse a [version] string from the given [serverKind] in to a [Version].
         */
        private fun parseVersionString(serverKind: ServerKind, version: String): Result<Version, UnparseableVersion> = binding {
            // Real world examples of version strings from nodeinfo
            // pleroma - 2.6.50-875-g2eb5c453.service-origin+soapbox
            // akkoma - 3.9.3-0-gd83f5f66f-blob
            // firefish - 1.1.0-dev29-hf1
            // hometown - 4.0.10+hometown-1.1.1
            // cherrypick - 4.6.0+cs-8f0ba0f
            // gotosocial - 0.13.1-SNAPSHOT git-dfc7656

            val semver = when (serverKind) {
                // These servers have semver compatible versions
                AKKOMA, MASTODON, PLEROMA, UNKNOWN -> {
                    resultOf { Version.parse(version, strict = false) }
                        .mapError { UnparseableVersion(version, it) }.bind()
                }
                // GoToSocial does not report a semver compatible version, expect something
                // where the possible version number is space-separated, like "0.13.1 git-ccecf5a"
                // https://github.com/superseriousbusiness/gotosocial/issues/1953
                GOTOSOCIAL -> {
                    // Try and parse as semver, just in case
                    resultOf { Version.parse(version, strict = false) }
                        .getOrElse {
                            // Didn't parse, use the first component, fall back to 0.0.0
                            val components = version.split(" ")
                            resultOf { Version.parse(components[0], strict = false) }
                                .getOrElse { "0.0.0".toVersion() }
                        }
                }
            }

            semver
        }

        /**
         * Capabilities that can be determined directly from the server's version, without checking
         * the instanceInfo response.
         *
         * Modifies `capabilities` by potentially adding new capabilities to the map.
         */
        private fun capabilitiesFromServerVersion(kind: ServerKind, v: Version): MutableMap<ServerOperation, Version> {
            val c = mutableMapOf<ServerOperation, Version>()
            when (kind) {
                MASTODON -> {
                    when {
                        v >= "3.1.0".toVersion() -> c[ORG_JOINMASTODON_FILTERS_CLIENT] = "1.1.0".toVersion()
                        v >= "2.4.3".toVersion() -> c[ORG_JOINMASTODON_FILTERS_CLIENT] = "1.0.0".toVersion()
                    }

                    when {
                        v >= "4.0.0".toVersion() -> c[ORG_JOINMASTODON_FILTERS_SERVER] = "1.0.0".toVersion()
                    }
                }

                // GoToSocial can't filter, https://github.com/superseriousbusiness/gotosocial/issues/1472
                GOTOSOCIAL -> { }

                // Everything else. Assume server side filtering and no translation. This may be an
                // incorrect assumption.
                AKKOMA, PLEROMA, UNKNOWN -> {
                    c[ORG_JOINMASTODON_FILTERS_SERVER] = "1.0.0".toVersion()
                }
            }
            return c
        }
    }

    /** Errors that can occur when processing server capabilities */
    sealed class Error(
        @StringRes resourceId: Int,
        vararg formatArgs: String,
    ) : PachliError(resourceId, *formatArgs) {
        /** Could not parse the server's version string */
        data class UnparseableVersion(val version: String, val throwable: Throwable) : Error(
            R.string.server_error_unparseable_version,
            version,
            throwable.localizedMessage,
        )
    }
}

enum class ServerKind {
    AKKOMA,
    GOTOSOCIAL,
    MASTODON,
    PLEROMA,
    UNKNOWN,
    ;

    companion object {
        fun from(s: String) = when (s.lowercase()) {
            "akkoma" -> AKKOMA
            "gotosocial" -> GOTOSOCIAL
            "mastodon" -> MASTODON
            "pleroma" -> PLEROMA
            else -> UNKNOWN
        }
    }
}
