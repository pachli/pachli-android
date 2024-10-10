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
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify

/**
 * Verify that [ApplyFilter] is handled correctly on receipt:
 *
 * - Is the [UiState] updated correctly?
 * - Are the correct [AccountManager] functions called, with the correct arguments?
 */
class NotificationsViewModelTestContentFilter : NotificationsViewModelTestBase() {

    @Test
    fun `should load initial filter from active account`() = runTest {
        viewModel.uiState.test {
            assertThat(awaitItem().activeFilter)
                .containsExactlyElementsIn(setOf(Notification.Type.FOLLOW))
        }
    }

    @Test
    fun `should save filter to active account && update state`() = runTest {
        viewModel.uiState.test {
            // When
            viewModel.accept(InfallibleUiAction.ApplyFilter(setOf(Notification.Type.REBLOG)))

            // Then
            // - filter saved to active account
            argumentCaptor<Pair<Long, String>>().apply {
                verify(accountManager).setNotificationsFilter(capture().first, capture().second)
            }

            // - filter updated in uiState
            assertThat(expectMostRecentItem().activeFilter)
                .containsExactlyElementsIn(setOf(Notification.Type.REBLOG))
        }
    }
}
