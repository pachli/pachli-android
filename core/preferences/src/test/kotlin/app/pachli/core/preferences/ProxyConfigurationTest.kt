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

import org.junit.Assert
import org.junit.Test

class ProxyConfigurationTest {
    @Test
    fun `serialized non-int is not valid proxy port`() {
        Assert.assertFalse(ProxyConfiguration.isValidProxyPort("should fail"))
        Assert.assertFalse(ProxyConfiguration.isValidProxyPort("1.5"))
    }

    @Test
    fun `number outside port range is not valid`() {
        Assert.assertFalse(ProxyConfiguration.isValidProxyPort("${ProxyConfiguration.MIN_PROXY_PORT - 1}"))
        Assert.assertFalse(ProxyConfiguration.isValidProxyPort("${ProxyConfiguration.MAX_PROXY_PORT + 1}"))
    }

    @Test
    fun `number in port range, inclusive of min and max, is valid`() {
        Assert.assertTrue(ProxyConfiguration.isValidProxyPort(ProxyConfiguration.MIN_PROXY_PORT))
        Assert.assertTrue(ProxyConfiguration.isValidProxyPort(ProxyConfiguration.MAX_PROXY_PORT))
        Assert.assertTrue(ProxyConfiguration.isValidProxyPort((ProxyConfiguration.MIN_PROXY_PORT + ProxyConfiguration.MAX_PROXY_PORT) / 2))
    }

    @Test
    fun `create with invalid port yields null`() {
        Assert.assertNull(ProxyConfiguration.create("hostname", ProxyConfiguration.MIN_PROXY_PORT - 1))
    }

    @Test
    fun `create with invalid hostname yields null`() {
        Assert.assertNull(ProxyConfiguration.create(".", ProxyConfiguration.MIN_PROXY_PORT))
    }

    @Test
    fun `create with valid hostname and port yields the config object`() {
        Assert.assertTrue(ProxyConfiguration.create("hostname", ProxyConfiguration.MIN_PROXY_PORT) is ProxyConfiguration)
    }

    @Test
    fun `unicode hostname allowed`() {
        Assert.assertTrue(ProxyConfiguration.create("federação.social", ProxyConfiguration.MIN_PROXY_PORT) is ProxyConfiguration)
    }
}
