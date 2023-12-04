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

package app.pachli.components.timeline

import androidx.core.content.edit
import app.cash.turbine.test
import app.pachli.core.preferences.PrefKeys
import app.pachli.util.StatusDisplayOptions
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Verify that [StatusDisplayOptions] are handled correctly.
 *
 * - Is the initial value taken from values in sharedPreferences and account?
 * - Is the correct update emitted when a relevant preference changes?
 */
// TODO: With the exception of the types, this is identical to
// NotificationsViewModelTestStatusDisplayOptions
@HiltAndroidTest
class NetworkTimelineViewModelTestStatusDisplayOptions : NetworkTimelineViewModelTestBase() {

    private val defaultStatusDisplayOptions = StatusDisplayOptions()

    @Test
    fun `initial settings are from sharedPreferences and activeAccount`() = runTest {
        viewModel.statusDisplayOptions.test {
            val item = expectMostRecentItem()
            assertThat(item).isEqualTo(defaultStatusDisplayOptions)
        }
    }

    @Test
    fun `editing preferences emits new StatusDisplayOptions`() = runTest {
        viewModel.statusDisplayOptions.test {
            // Given, should be false
            assertThat(awaitItem().animateAvatars).isFalse()

            // When
            sharedPreferencesRepository.edit {
                putBoolean(PrefKeys.ANIMATE_GIF_AVATARS, true)
            }

            // Then, should be true
            assertThat(awaitItem().animateAvatars).isTrue()
        }
    }
}
