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

package app.pachli.core.preferences

import java.net.IDN

class ProxyConfiguration private constructor(
    val hostname: String,
    val port: Int,
) {
    companion object {
        fun create(hostname: String, port: Int): ProxyConfiguration? {
            if (isValidHostname(IDN.toASCII(hostname)) && isValidProxyPort(port)) {
                return ProxyConfiguration(hostname, port)
            }
            return null
        }
        fun isValidProxyPort(value: Any): Boolean = when (value) {
            is String -> if (value == "") {
                true
            } else {
                value.runCatching(String::toInt).map(
                    PROXY_RANGE::contains,
                ).getOrDefault(false)
            }
            is Int -> PROXY_RANGE.contains(value)
            else -> false
        }
        fun isValidHostname(hostname: String): Boolean =
            IP_ADDRESS_REGEX.matches(hostname) || HOSTNAME_REGEX.matches(hostname)
        const val MIN_PROXY_PORT = 1
        const val MAX_PROXY_PORT = 65535
    }
}

private val PROXY_RANGE = IntRange(
    ProxyConfiguration.MIN_PROXY_PORT,
    ProxyConfiguration.MAX_PROXY_PORT,
)
private val IP_ADDRESS_REGEX = Regex("^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$")
private val HOSTNAME_REGEX = Regex("^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$")
