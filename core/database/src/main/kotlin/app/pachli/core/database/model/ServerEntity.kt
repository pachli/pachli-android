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

package app.pachli.core.database.model

import androidx.room3.ColumnInfo
import androidx.room3.ColumnTypeConverters
import androidx.room3.Entity
import androidx.room3.ForeignKey
import app.pachli.core.database.Converters
import app.pachli.core.model.Emoji
import app.pachli.core.model.Server
import app.pachli.core.model.ServerCapabilities
import app.pachli.core.model.ServerKind
import app.pachli.core.model.ServerLimits
import io.github.z4kn4fein.semver.Version

/**
 * Represents a Mastodon server's capabilities.
 *
 * Each server is associated with exactly one [PachliAccountEntity] through the [pachliAccountId]
 * property.
 *
 * @property pachliAccountId
 * @property serverKind Server's [ServerKind].
 * @property version Server's version, parsed to a [Version].
 * @property rawVersion Raw server version string, as reported by the server.
 * @property capabilities Server's [ServerCapabilities].
 * @property limits Server's [ServerLimits].
 * @property emojis Server's [Emoji].
 */
@Entity(
    primaryKeys = ["pachliAccountId"],
    foreignKeys = [
        ForeignKey(
            entity = PachliAccountEntity::class,
            parentColumns = ["pachliAccountId"],
            childColumns = ["pachliAccountId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
)
@ColumnTypeConverters(Converters::class)
data class ServerEntity(
    val pachliAccountId: Long,
    val serverKind: ServerKind,
    val version: Version,
    @ColumnInfo(defaultValue = "")
    val rawVersion: String,
    val capabilities: ServerCapabilities,

    @ColumnInfo(defaultValue = "{}")
    val limits: ServerLimits,

    @ColumnInfo(defaultValue = "[]")
    val emojis: List<Emoji>,
) {
    fun asModel() = Server(
        kind = serverKind,
        version = version,
        rawVersion = rawVersion,
        capabilities = capabilities,
        limits = limits,
        emojis = emojis,
    )
}

fun Server.asEntity(pachliAccountId: Long) = ServerEntity(
    pachliAccountId = pachliAccountId,
    serverKind = kind,
    version = version,
    rawVersion = rawVersion,
    capabilities = capabilities,
    limits = limits,
    emojis = emojis,
)
