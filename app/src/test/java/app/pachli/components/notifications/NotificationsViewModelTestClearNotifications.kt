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
import app.pachli.core.testing.failure
import app.pachli.core.testing.success
import com.github.michaelbull.result.getError
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

/**
 * Verify that [FallibleUiAction.ClearNotifications] is handled correctly on receipt:
 *
 * - Is the correct [UiSuccess] or [UiError] value emitted?
 * - Are the correct [NotificationsRepository] functions called, with the correct arguments?
 *   This is only tested in the success case; if it passed there it must also
 *   have passed in the error case.
 */
@HiltAndroidTest
class NotificationsViewModelTestClearNotifications : NotificationsViewModelTestBase() {
    @Test
    fun `clearing notifications succeeds`() = runTest {
        // Given
        mastodonApi.stub { onBlocking { clearNotifications() } doReturn success(Unit) }

        // When
        viewModel.accept(FallibleUiAction.ClearNotifications)

        // Then
        verify(notificationsRepository).clearNotifications()
    }

    @Test
    fun `clearing notifications fails && emits UiError`() = runTest {
        // Given
        notificationsRepository.stub { onBlocking { clearNotifications() } doReturn failure() }

        viewModel.uiResult.test {
            // When
            viewModel.accept(FallibleUiAction.ClearNotifications)

            // Then
            val item = awaitItem().getError() as? UiError.ClearNotifications
            assertThat(item?.action).isEqualTo(FallibleUiAction.ClearNotifications)
        }
    }
}
