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

package app.pachli.updatecheck

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.testing.failure
import app.pachli.core.testing.fakes.InMemorySharedPreferences
import app.pachli.core.testing.success
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.stub

@RunWith(AndroidJUnit4::class)
class UpdateCheckTest {
    private val fdroidService: FdroidService = mock()
    private lateinit var updateCheck: UpdateCheck

    @Before
    fun setup() {
        reset(fdroidService)
        updateCheck = UpdateCheck(
            SharedPreferencesRepository(InMemorySharedPreferences(), TestScope()),
            fdroidService,
        )
    }

    @Test
    fun `remoteFetchLatestVersionCode returns null on network error`() = runTest {
        fdroidService.stub {
            onBlocking { getPackage(any()) } doReturn failure()
        }

        assertThat(updateCheck.remoteFetchLatestVersionCode()).isNull()
    }

    @Test
    fun `remoteFetchLatestVersionCode returns suggestedVersionCode if in packages`() = runTest {
        fdroidService.stub {
            onBlocking { getPackage(any()) } doReturn success(
                FdroidPackage(
                    packageName = "app.pachli",
                    suggestedVersionCode = 3,
                    packages = listOf(
                        FdroidPackageVersion(versionName = "3.0", versionCode = 3),
                        FdroidPackageVersion(versionName = "2.0", versionCode = 2),
                        FdroidPackageVersion(versionName = "1.0", versionCode = 1),
                    ),
                ),
            )
        }

        // "3" is the `suggestedVersionCode`, and is in `packages`, so should be returned.
        assertThat(updateCheck.remoteFetchLatestVersionCode()).isEqualTo(3)
    }

    @Test
    fun `remoteFetchLatestVersionCode returns greatest code if suggestedVersionCode is missing`() = runTest {
        fdroidService.stub {
            onBlocking { getPackage(any()) } doReturn success(
                FdroidPackage(
                    packageName = "app.pachli",
                    suggestedVersionCode = 3,
                    packages = listOf(
                        FdroidPackageVersion(versionName = "2.0", versionCode = 2),
                        FdroidPackageVersion(versionName = "1.0", versionCode = 1),
                    ),
                ),
            )
        }

        // "3" is the `suggestedVersionCode`, but is not in `packages`, so the greatest
        // `versionCode` in `packages` should be returned.
        assertThat(updateCheck.remoteFetchLatestVersionCode()).isEqualTo(2)
    }
}
