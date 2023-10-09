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

package app.pachli.components.notifications

import androidx.core.content.edit
import app.cash.turbine.test
import app.pachli.entity.Notification
import app.pachli.settings.PrefKeys
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Verify that [UiState] is handled correctly.
 *
 * - Is the initial value taken from values in sharedPreferences and account?
 * - Is the correct update emitted when a relevant preference changes?
 */
class NotificationsViewModelTestUiState : NotificationsViewModelTestBase() {

    private val initialUiState = UiState(
        activeFilter = setOf(Notification.Type.FOLLOW),
        showFabWhileScrolling = true,
    )

    @Test
    fun `should load initial filter from active account`() = runTest {
        viewModel.uiState.test {
            assertThat(expectMostRecentItem()).isEqualTo(initialUiState)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `showFabWhileScrolling depends on FAB_HIDE preference`() = runTest {
        // Given
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().showFabWhileScrolling).isTrue()
        }

        // When
        sharedPreferencesRepository.edit(commit = true) {
            putBoolean(PrefKeys.FAB_HIDE, true)
        }

        // Then
        viewModel.uiState.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem().showFabWhileScrolling).isFalse()
        }
    }
}
