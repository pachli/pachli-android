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
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PRIVATE
import app.pachli.core.common.PachliError
import app.pachli.core.network.Server.Error.UnparseableVersion
import app.pachli.core.network.ServerKind.AKKOMA
import app.pachli.core.network.ServerKind.FEDIBIRD
import app.pachli.core.network.ServerKind.FIREFISH
import app.pachli.core.network.ServerKind.FRIENDICA
import app.pachli.core.network.ServerKind.GLITCH
import app.pachli.core.network.ServerKind.GOTOSOCIAL
import app.pachli.core.network.ServerKind.HOMETOWN
import app.pachli.core.network.ServerKind.ICESHRIMP
import app.pachli.core.network.ServerKind.MASTODON
import app.pachli.core.network.ServerKind.PIXELFED
import app.pachli.core.network.ServerKind.PLEROMA
import app.pachli.core.network.ServerKind.SHARKEY
import app.pachli.core.network.ServerKind.UNKNOWN
import app.pachli.core.network.ServerOperation.ORG_JOINMASTODON_FILTERS_CLIENT
import app.pachli.core.network.ServerOperation.ORG_JOINMASTODON_FILTERS_SERVER
import app.pachli.core.network.ServerOperation.ORG_JOINMASTODON_STATUSES_TRANSLATE
import app.pachli.core.network.model.InstanceV1
import app.pachli.core.network.model.InstanceV2
import app.pachli.core.network.model.nodeinfo.NodeInfo
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.recover
import com.github.michaelbull.result.toResultOr
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.constraints.Constraint
import io.github.z4kn4fein.semver.satisfies
import io.github.z4kn4fein.semver.toVersion
import java.text.ParseException
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
            val serverKind = ServerKind.from(software)
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
            val serverKind = ServerKind.from(software)
            val version = parseVersionString(serverKind, software.version).bind()
            val capabilities = capabilitiesFromServerVersion(serverKind, version)

            Server(serverKind, version, capabilities)
        }

        /**
         * Parse a [version] string from the given [serverKind] in to a [Version].
         */
        @VisibleForTesting(otherwise = PRIVATE)
        fun parseVersionString(serverKind: ServerKind, version: String): Result<Version, Error> {
            val result = runSuspendCatching {
                Version.parse(version, strict = false)
            }.mapError { UnparseableVersion(version, it) }

            if (result is Ok) return result

            return when (serverKind) {
                // These servers should have semver compatible versions, but perhaps
                // the server operator has changed them. Try looking for a matching
                // <major>.<minor>.<patch> somewhere in the version string and hope
                // it's correct
                AKKOMA, FEDIBIRD, FIREFISH, GLITCH, HOMETOWN, MASTODON, PIXELFED, UNKNOWN -> {
                    val rx = """(?<major>\d+)\.(?<minor>\d+).(?<patch>\d+)""".toRegex()
                    rx.find(version)
                        .toResultOr { UnparseableVersion(version, ParseException("unexpected null", 0)) }
                        .andThen {
                            // Fetching groups by name instead of index requires API >= 26
                            val adjusted = "${it.groups[1]?.value}.${it.groups[2]?.value}.${it.groups[3]?.value}"
                            runSuspendCatching { Version.parse(adjusted, strict = false) }
                                .mapError { UnparseableVersion(version, it) }
                        }
                }

                // Friendica does not report a semver compatible version, expect something
                // where the version looks like "yyyy.mm", with an optional suffix that
                // starts with a "-". The date-like parts of the string may have leading
                // zeros.
                //
                // Try to extract the "yyyy.mm", without any leading zeros, append ".0".
                // https://github.com/friendica/friendica/issues/11264
                FRIENDICA -> {
                    val rx = """^0*(?<major>\d+)\.0*(?<minor>\d+)""".toRegex()
                    rx.find(version)
                        .toResultOr { UnparseableVersion(version, ParseException("unexpected null", 0)) }
                        .andThen {
                            // Fetching groups by name instead of index requires API >= 26
                            val adjusted = "${it.groups[1]?.value}.${it.groups[2]?.value}.0"
                            runSuspendCatching { Version.parse(adjusted, strict = false) }
                                .mapError { UnparseableVersion(version, it) }
                        }
                }

                // GoToSocial does not always report a semver compatible version, and is all
                // over the place, including:
                //
                // - "" (empty)
                // - "git-8ab30d0"
                // - "kalaclista git-212fecf"
                // - "f4fcffc8b56ef73c184ae17892b69181961c15c7"
                //
                // as well as instances where the version number is semver compatible, but is
                // separated by whitespace or a "_".
                //
                // https://github.com/superseriousbusiness/gotosocial/issues/1953
                //
                // Since GoToSocial has comparatively few features at the moment just fall
                // back to "0.0.0" if there are problems.
                GOTOSOCIAL -> {
                    // Failed, split on spaces and parse the first component
                    val components = version.split(" ", "_")
                    runSuspendCatching { Version.parse(components[0], strict = false) }
                        .recover { "0.0.0".toVersion() }
                }

                // IceShrimp uses "yyyy.mm.dd" with leading zeros in the month and day
                // components, similar to Friendica.
                // https://iceshrimp.dev/iceshrimp/iceshrimp/issues/502 and
                // https://iceshrimp.dev/iceshrimp/iceshrimp-rewrite/issues/1
                ICESHRIMP -> {
                    val rx = """^0*(?<major>\d+)\.0*(?<minor>\d+)\.0*(?<patch>\d+)""".toRegex()
                    rx.find(version).toResultOr { UnparseableVersion(version, ParseException("unexpected null", 0)) }
                        .andThen {
                            // Fetching groups by name instead of index requires API >= 26
                            val adjusted = "${it.groups[1]?.value}.${it.groups[2]?.value ?: 0}.${it.groups[3]?.value ?: 0}"
                            runSuspendCatching { Version.parse(adjusted, strict = false) }
                                .mapError { UnparseableVersion(adjusted, it) }
                        }
                }

                // Seen "Pleroma 0.9.0 d93789dfde3c44c76a56732088a897ddddfe9716" in
                // the wild
                PLEROMA -> {
                    val rx = """Pleroma (?<major>\d+)\.(?<minor>\d+)\.(?<patch>\d+)""".toRegex()
                    rx.find(version).toResultOr { UnparseableVersion(version, ParseException("unexpected null", 0)) }
                        .andThen {
                            // Fetching groups by name instead of index requires API >= 26
                            val adjusted = "${it.groups[1]?.value}.${it.groups[2]?.value}.${it.groups[3]?.value}"
                            runSuspendCatching { Version.parse(adjusted, strict = false) }
                                .mapError { UnparseableVersion(adjusted, it) }
                        }
                }

                // Uses format "yyyy.mm.dd" with an optional ".beta..." suffix.
                // https://git.joinsharkey.org/Sharkey/Sharkey/issues/371
                SHARKEY -> {
                    val rx = """^(?<major>\d+)\.(?<minor>\d+)\.(?<patch>\d+)""".toRegex()
                    rx.find(version).toResultOr { UnparseableVersion(version, ParseException("unexpected null", 0)) }
                        .andThen {
                            // Fetching groups by name instead of index requires API >= 26
                            val adjusted = "${it.groups[1]?.value}.${it.groups[2]?.value}.${it.groups[3]?.value}"
                            runSuspendCatching { Version.parse(adjusted, strict = false) }
                                .mapError { UnparseableVersion(adjusted, it) }
                        }
                }
            }
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

                // GoToSocial has client-side filtering, not server-side
                GOTOSOCIAL -> {
                    when {
                        // Implemented in https://github.com/superseriousbusiness/gotosocial/pull/2594
                        v >= "0.15.0".toVersion() -> c[ORG_JOINMASTODON_FILTERS_CLIENT] = "1.1.0".toVersion()
                    }
                }

                // FireFish can't filter (conversation in the Firefish dev. chat )
                FIREFISH -> { }

                // Everything else. Assume server side filtering and no translation. This may be an
                // incorrect assumption.
                AKKOMA, FEDIBIRD, FRIENDICA, GLITCH, HOMETOWN, ICESHRIMP, PIXELFED, PLEROMA, SHARKEY, UNKNOWN -> {
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

/**
 * Servers that are known to implement the Mastodon client API
 */
enum class ServerKind {
    AKKOMA,
    FEDIBIRD,
    FIREFISH,
    FRIENDICA,
    GLITCH,
    GOTOSOCIAL,
    HOMETOWN,
    ICESHRIMP,
    MASTODON,
    PLEROMA,
    PIXELFED,
    SHARKEY,

    /**
     * Catch-all for servers we don't recognise but that responded to either
     * /api/v1/instance or /api/v2/instance
     */
    UNKNOWN,
    ;

    companion object {
        fun from(s: NodeInfo.Software) = when (s.name.lowercase()) {
            "akkoma" -> AKKOMA
            "fedibird" -> FEDIBIRD
            "firefish" -> FIREFISH
            "friendica" -> FRIENDICA
            "gotosocial" -> GOTOSOCIAL
            "hometown" -> HOMETOWN
            "iceshrimp" -> ICESHRIMP
            "mastodon" -> {
                // Glitch doesn't report a different software name it stuffs it
                // in the version (https://github.com/glitch-soc/mastodon/issues/2582).
                if (s.version.contains("+glitch")) GLITCH else MASTODON
            }
            "pixelfed" -> PIXELFED
            "pleroma" -> PLEROMA
            "sharkey" -> SHARKEY
            else -> UNKNOWN
        }
    }
}
