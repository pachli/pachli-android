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
