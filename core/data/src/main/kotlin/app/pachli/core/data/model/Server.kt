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

package app.pachli.core.data.model

import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PRIVATE
import app.pachli.core.common.PachliError
import app.pachli.core.data.model.Server.Error.UnparseableVersion
import app.pachli.core.database.model.InstanceInfoEntity
import app.pachli.core.database.model.ServerEntity
import app.pachli.core.model.NodeInfo
import app.pachli.core.model.ServerCapabilities
import app.pachli.core.model.ServerKind
import app.pachli.core.model.ServerKind.AKKOMA
import app.pachli.core.model.ServerKind.FEDIBIRD
import app.pachli.core.model.ServerKind.FIREFISH
import app.pachli.core.model.ServerKind.FRIENDICA
import app.pachli.core.model.ServerKind.GLITCH
import app.pachli.core.model.ServerKind.GOTOSOCIAL
import app.pachli.core.model.ServerKind.HOMETOWN
import app.pachli.core.model.ServerKind.ICESHRIMP
import app.pachli.core.model.ServerKind.MASTODON
import app.pachli.core.model.ServerKind.PIXELFED
import app.pachli.core.model.ServerKind.PLEROMA
import app.pachli.core.model.ServerKind.SHARKEY
import app.pachli.core.model.ServerKind.UNKNOWN
import app.pachli.core.model.ServerOperation
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_FILTERS_CLIENT
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_FILTERS_SERVER
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_BY_DATE
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_FROM
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_AUDIO
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_EMBED
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_IMAGE
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_LINK
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_MEDIA
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_POLL
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_VIDEO
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_IN_LIBRARY
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_IN_PUBLIC
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_IS_REPLY
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_IS_SENSITIVE
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_LANGUAGE
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_STATUSES_GET
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_STATUSES_SCHEDULED
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_STATUSES_TRANSLATE
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_TIMELINES_LINK
import app.pachli.core.network.R
import app.pachli.core.network.model.InstanceV1
import app.pachli.core.network.model.InstanceV2
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

data class Server(
    val kind: ServerKind,
    val version: Version,
    val capabilities: ServerCapabilities = emptyMap(),
) {
    /**
     * @return true if the server supports the given operation at the given minimum version
     * level, false otherwise.
     */
    fun can(operation: ServerOperation, constraint: Constraint) =
        capabilities[operation]?.let { version ->
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
                GLITCH, HOMETOWN, MASTODON -> {
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
         * Constructs a capabilities map from its [NodeInfo] and [InstanceInfoEntity] details.
         */
        fun from(software: NodeInfo.Software, instanceInfoEntity: InstanceInfoEntity): Result<Server, Error> = binding {
            val serverKind = ServerKind.from(software)
            val version = parseVersionString(serverKind, software.version).bind()
            val capabilities = capabilitiesFromServerVersion(serverKind, version)

            when (serverKind) {
                GLITCH, HOMETOWN, MASTODON -> {
                    if (instanceInfoEntity.enabledTranslation) {
                        capabilities[ORG_JOINMASTODON_STATUSES_TRANSLATE] = when {
                            version >= "4.2.0".toVersion() -> "1.1.0".toVersion()
                            else -> "1.0.0".toVersion()
                        }
                    }
                }

                else -> {
                    /* Nothing to do */
                }
            }

            Server(serverKind, version, capabilities)
        }

        fun from(entity: ServerEntity) = Server(
            kind = entity.serverKind,
            version = entity.version,
            capabilities = entity.capabilities,
        )

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
                // https://activitypub.software/TransFem-org/Sharkey/-/issues/371
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
         */
        private fun capabilitiesFromServerVersion(kind: ServerKind, v: Version): MutableMap<ServerOperation, Version> {
            val c = mutableMapOf<ServerOperation, Version>()
            when (kind) {
                // Glitch has the same version number as upstream Mastodon
                GLITCH, MASTODON -> {
                    // Scheduled statuses
                    when {
                        v >= "2.7.0".toVersion() -> c[ORG_JOINMASTODON_STATUSES_SCHEDULED] = "1.0.0".toVersion()
                    }

                    // Client filtering
                    when {
                        v >= "3.1.0".toVersion() -> c[ORG_JOINMASTODON_FILTERS_CLIENT] = "1.1.0".toVersion()
                        v >= "2.4.3".toVersion() -> c[ORG_JOINMASTODON_FILTERS_CLIENT] = "1.0.0".toVersion()
                    }

                    // Server side filtering
                    when {
                        v >= "4.0.0".toVersion() -> c[ORG_JOINMASTODON_FILTERS_SERVER] = "1.0.0".toVersion()
                    }

                    // Search operators
                    when {
                        v >= "4.3.0".toVersion() -> {
                            c[ORG_JOINMASTODON_SEARCH_QUERY_IN_PUBLIC] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_FROM] = "1.1.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_LANGUAGE] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_HAS_MEDIA] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_HAS_IMAGE] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_HAS_VIDEO] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_HAS_AUDIO] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_HAS_POLL] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_HAS_LINK] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_HAS_EMBED] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_IS_REPLY] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_IS_SENSITIVE] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_IN_LIBRARY] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_BY_DATE] = "1.0.0".toVersion()
                        }

                        v >= "4.2.0".toVersion() -> {
                            c[ORG_JOINMASTODON_SEARCH_QUERY_FROM] = "1.1.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_LANGUAGE] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_HAS_MEDIA] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_HAS_IMAGE] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_HAS_VIDEO] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_HAS_AUDIO] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_HAS_POLL] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_HAS_LINK] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_HAS_EMBED] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_IS_REPLY] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_IS_SENSITIVE] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_IN_LIBRARY] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_BY_DATE] = "1.0.0".toVersion()
                        }

                        v >= "3.5.0".toVersion() -> {
                            c[ORG_JOINMASTODON_SEARCH_QUERY_FROM] = "1.0.0".toVersion()
                        }
                    }

                    // Link timelines
                    when {
                        v >= "4.3.0".toVersion() -> c[ORG_JOINMASTODON_TIMELINES_LINK] = "1.0.0".toVersion()
                    }

                    // Get multiple statuses at once.
                    when {
                        v >= "4.3.0".toVersion() -> c[ORG_JOINMASTODON_STATUSES_GET] = "1.0.0".toVersion()
                    }
                }

                GOTOSOCIAL -> {
                    // Can't do scheduled posts, https://github.com/superseriousbusiness/gotosocial/issues/1006

                    // Filters
                    when {
                        // Implemented in https://github.com/superseriousbusiness/gotosocial/pull/2936
                        v >= "0.16.0".toVersion() -> {
                            c[ORG_JOINMASTODON_FILTERS_CLIENT] = "1.1.0".toVersion()
                            c[ORG_JOINMASTODON_FILTERS_SERVER] = "1.0.0".toVersion()
                        }
                        // Implemented in https://github.com/superseriousbusiness/gotosocial/pull/2594
                        v >= "0.15.0".toVersion() -> c[ORG_JOINMASTODON_FILTERS_CLIENT] = "1.1.0".toVersion()
                    }

                    // Search
                    when {
                        // from: in https://github.com/superseriousbusiness/gotosocial/pull/2943
                        v >= "0.16.0".toVersion() -> {
                            c[ORG_JOINMASTODON_SEARCH_QUERY_FROM] = "1.0.0".toVersion()
                        }
                    }
                }

                // FireFish can't filter (conversation in the Firefish dev. chat )
                FIREFISH -> { }

                // Sharkey can't filter, https://activitypub.software/TransFem-org/Sharkey/-/issues/492
                SHARKEY -> {
                    // Assume scheduled support (may be wrong).
                    c[ORG_JOINMASTODON_STATUSES_SCHEDULED] = "1.0.0".toVersion()
                }

                FRIENDICA -> {
                    // Assume filter support (may be wrong).
                    c[ORG_JOINMASTODON_FILTERS_SERVER] = "1.0.0".toVersion()

                    // Assume scheduled support (may be wrong).
                    c[ORG_JOINMASTODON_STATUSES_SCHEDULED] = "1.0.0".toVersion()

                    // Search
                    when {
                        // Friendica has a number of search operators that are not in Mastodon.
                        // See https://github.com/friendica/friendica/blob/develop/doc/Channels.md
                        // for details.
                        v >= "2024.3.0".toVersion() -> {
                            c[ORG_JOINMASTODON_SEARCH_QUERY_FROM] = "1.0.0".toVersion()
                            c[ORG_JOINMASTODON_SEARCH_QUERY_LANGUAGE] = "1.0.0".toVersion()
                        }
                    }
                }

                PLEROMA -> {
                    // Pleroma only has v1 filters
                    c[ORG_JOINMASTODON_FILTERS_CLIENT] = "1.1.0".toVersion()
                    c[ORG_JOINMASTODON_STATUSES_SCHEDULED] = "1.0.0".toVersion()
                }

                AKKOMA -> {
                    // https://akkoma.dev/AkkomaGang/akkoma/src/branch/develop/lib/pleroma/web/mastodon_api/controllers
                    // Akkoma only has v1 filters.
                    c[ORG_JOINMASTODON_FILTERS_CLIENT] = "1.1.0".toVersion()
                    c[ORG_JOINMASTODON_STATUSES_SCHEDULED] = "1.0.0".toVersion()
                }

                // Everything else. Assume:
                //
                // - server side filtering
                // - scheduled status support
                // - no translation
                //
                // This may be an incorrect assumption.
                FEDIBIRD, HOMETOWN, ICESHRIMP, PIXELFED, UNKNOWN -> {
                    c[ORG_JOINMASTODON_FILTERS_SERVER] = "1.0.0".toVersion()
                    c[ORG_JOINMASTODON_STATUSES_SCHEDULED] = "1.0.0".toVersion()
                }
            }
            return c
        }
    }

    /** Errors that can occur when processing server capabilities */
    sealed interface Error : PachliError {
        /** Could not parse the server's version string */
        data class UnparseableVersion(val version: String, val throwable: Throwable) : Error {
            override val resourceId = R.string.server_error_unparseable_version
            override val formatArgs: Array<String> = arrayOf(version, throwable.localizedMessage ?: "")
            override val cause: PachliError? = null
        }
    }
}
