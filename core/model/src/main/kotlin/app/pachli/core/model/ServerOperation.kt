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

package app.pachli.core.model

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.constraints.Constraint
import io.github.z4kn4fein.semver.satisfies

typealias ServerCapabilities = Map<ServerOperation, Version>

/**
 * @return true if the server supports the given operation at the given minimum version
 * level, false otherwise.
 */
fun ServerCapabilities.can(operation: ServerOperation, constraint: Constraint) = this[operation]?.let { version ->
    version satisfies constraint
} ?: false

/**
 * Serializes [Version] to/from JSON using its String form.
 */
class VersionAdapter {
    @ToJson
    fun toJson(version: Version) = version.toString()

    @FromJson
    fun fromJson(s: String) = Version.parse(s)
}

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

    /** Search for posts from a particular account */
    ORG_JOINMASTODON_SEARCH_QUERY_FROM(
        "org.joinmastodon.search.query:from",
        listOf(
            // Initial introduction in Mastodon 3.5.0
            Version(major = 1),
            // Support for `from:me` in Mastodon 4.2.0
            Version(major = 1, minor = 1),
        ),
    ),
    ORG_JOINMASTODON_SEARCH_QUERY_LANGUAGE("org.joinmastodon.search.query:language", listOf(Version(major = 1))),
    ORG_JOINMASTODON_SEARCH_QUERY_HAS_MEDIA("org.joinmastodon.search.query:has:media", listOf(Version(major = 1))),
    ORG_JOINMASTODON_SEARCH_QUERY_HAS_IMAGE("org.joinmastodon.search.query:has:image", listOf(Version(major = 1))),
    ORG_JOINMASTODON_SEARCH_QUERY_HAS_VIDEO("org.joinmastodon.search.query:has:video", listOf(Version(major = 1))),
    ORG_JOINMASTODON_SEARCH_QUERY_HAS_AUDIO("org.joinmastodon.search.query:has:audio", listOf(Version(major = 1))),
    ORG_JOINMASTODON_SEARCH_QUERY_HAS_POLL("org.joinmastodon.search.query:has:poll", listOf(Version(major = 1))),
    ORG_JOINMASTODON_SEARCH_QUERY_HAS_LINK("org.joinmastodon.search.query:has:link", listOf(Version(major = 1))),
    ORG_JOINMASTODON_SEARCH_QUERY_HAS_EMBED("org.joinmastodon.search.query:has:embed", listOf(Version(major = 1))),
    ORG_JOINMASTODON_SEARCH_QUERY_IS_REPLY("org.joinmastodon.search.query:is:reply", listOf(Version(major = 1))),
    ORG_JOINMASTODON_SEARCH_QUERY_IS_SENSITIVE("org.joinmastodon.search.query:is:sensitive", listOf(Version(major = 1))),
    ORG_JOINMASTODON_SEARCH_QUERY_IN_LIBRARY("org.joinmastodon.search.query:in:library", listOf(Version(major = 1))),
    ORG_JOINMASTODON_SEARCH_QUERY_IN_PUBLIC("org.joinmastodon.search.query:in:public", listOf(Version(major = 1))),
    ORG_JOINMASTODON_SEARCH_QUERY_BY_DATE("org.joinmastodon.search.query:in:public", listOf(Version(major = 1))),

    /** Post a status with a `scheduled_at` property, and edit scheduled statuses. */
    ORG_JOINMASTODON_STATUSES_SCHEDULED(
        "org.joinmastodon.statuses.scheduled",
        listOf(
            // Initial introduction in Mastodon 2.7.0.
            Version(major = 1),
        ),
    ),

    /** Fetch statuses that mention a specific URL. */
    ORG_JOINMASTODON_TIMELINES_LINK(
        "org.joinmastodon.timelines.link",
        listOf(
            // Initial introduction in Mastodon 4.3.0
            Version(major = 1),
        ),
    ),
}
