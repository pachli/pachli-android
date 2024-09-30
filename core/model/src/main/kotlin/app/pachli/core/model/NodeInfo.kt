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

/**
 * A validated NodeInfo.
 *
 * See https://nodeinfo.diaspora.software/protocol.html and
 * https://nodeinfo.diaspora.software/schema.html.
 */
data class NodeInfo(val software: Software) {
    data class Software(
        /** Software name, won't be null, empty, or blank */
        val name: String,
        /** Software version, won't be null, empty, or blank */
        val version: String,
    )
}
