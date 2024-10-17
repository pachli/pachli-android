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

import app.cash.turbine.test
import app.pachli.core.network.model.Notification
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Verify that [ApplyFilter] is handled correctly on receipt:
 */
@HiltAndroidTest
class NotificationsViewModelTestContentFilter : NotificationsViewModelTestBase() {

    @Test
    fun `should save filter to active account && update state`() = runTest {
        viewModel.uiState.test {
            // Given
            // - Initial filter is from the active account
            assertThat(expectMostRecentItem().activeFilter)
                .containsExactlyElementsIn(setOf(Notification.Type.FOLLOW))

            // When
            // - Updating the filter
            viewModel.accept(InfallibleUiAction.ApplyFilter(setOf(Notification.Type.REBLOG)))

            // Then
            // - filter updated in uiState
            assertThat(awaitItem().activeFilter)
                .containsExactlyElementsIn(setOf(Notification.Type.REBLOG))
        }
    }
}
