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

package app.pachli.network

import app.pachli.network.ServerKind.AKKOMA
import app.pachli.network.ServerKind.MASTODON
import app.pachli.network.ServerKind.PLEROMA
import app.pachli.network.ServerKind.UNKNOWN
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.github.z4kn4fein.semver.Version
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class ServerKindTest(
    private val input: String,
    private val want: Result<Pair<ServerKind, Version>, ServerCapabilitiesError>,
) {
    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun data(): Iterable<Any> {
            return listOf(
                arrayOf(
                    "4.0.0",
                    Ok(Pair(MASTODON, Version.parse("4.0.0", strict = false)))
                ),
                arrayOf(
                    "4.2.1 (compatible; Iceshrimp 2023.11)",
                    Ok(Pair(UNKNOWN, Version.parse("2023.11", strict = false)))
                ),
                arrayOf(
                    "2.7.2 (compatible; Akkoma 3.10.3-202-g1b838627-1-CI-COMMIT-TAG---)",
                    Ok(Pair(AKKOMA, Version.parse("3.10.3-202-g1b838627-1-CI-COMMIT-TAG---", strict = false)))
                ),
                arrayOf(
                    "2.7.2 (compatible; Pleroma 2.5.54-640-gacbec640.develop+soapbox)",
                    Ok(Pair(PLEROMA, Version.parse("2.5.54-640-gacbec640.develop+soapbox", strict = false)))
                ),
            )
        }
    }

    @Test
    fun `ServerKind parse works`() {
        assertEquals(want, ServerKind.parse(input))
    }
}
