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
import app.pachli.core.network.model.Relationship
import at.connyduck.calladapter.networkresult.NetworkResult
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

/**
 * Verify that [NotificationAction] are handled correctly on receipt:
 *
 * - Is the correct [UiSuccess] or [UiError] value emitted?
 * - Is the correct [TimelineCases] function called, with the correct arguments?
 *   This is only tested in the success case; if it passed there it must also
 *   have passed in the error case.
 */
@HiltAndroidTest
class NotificationsViewModelTestNotificationFilterAction : NotificationsViewModelTestBase() {
    /** Dummy relationship */
    private val relationship = Relationship(
        // Nothing special about these values, it's just to have something to return
        "1234",
        following = true,
        followedBy = true,
        blocking = false,
        muting = false,
        mutingNotifications = false,
        requested = false,
        showingReblogs = false,
        subscribing = null,
        blockingDomain = false,
        note = null,
        notifying = null,
    )

    /** Action to accept a follow request */
    private val acceptAction = NotificationAction.AcceptFollowRequest("1234")

    /** Action to reject a follow request */
    private val rejectAction = NotificationAction.RejectFollowRequest("1234")

    @Test
    fun `accepting follow request succeeds && emits UiSuccess`() = runTest {
        // Given
        timelineCases.stub {
            onBlocking { acceptFollowRequest(any()) } doReturn NetworkResult.success(relationship)
        }

        viewModel.uiSuccess.test {
            // When
            viewModel.accept(acceptAction)

            // Then
            val item = awaitItem()
            assertThat(item).isInstanceOf(NotificationActionSuccess::class.java)
            assertThat((item as NotificationActionSuccess).action).isEqualTo(acceptAction)
        }

        // Then
        argumentCaptor<String>().apply {
            verify(timelineCases).acceptFollowRequest(capture())
            assertThat(this.lastValue).isEqualTo("1234")
        }
    }

    @Test
    fun `accepting follow request fails && emits UiError`() = runTest {
        // Given
        timelineCases.stub { onBlocking { acceptFollowRequest(any()) } doThrow httpException }

        viewModel.uiError.test {
            // When
            viewModel.accept(acceptAction)

            // Then
            val item = awaitItem()
            assertThat(item).isInstanceOf(UiError.AcceptFollowRequest::class.java)
            assertThat(item.action).isEqualTo(acceptAction)
        }
    }

    @Test
    fun `rejecting follow request succeeds && emits UiSuccess`() = runTest {
        // Given
        timelineCases.stub { onBlocking { rejectFollowRequest(any()) } doReturn NetworkResult.success(relationship) }

        viewModel.uiSuccess.test {
            // When
            viewModel.accept(rejectAction)

            // Then
            val item = awaitItem()
            assertThat(item).isInstanceOf(NotificationActionSuccess::class.java)
            assertThat((item as NotificationActionSuccess).action).isEqualTo(rejectAction)
        }

        // Then
        argumentCaptor<String>().apply {
            verify(timelineCases).rejectFollowRequest(capture())
            assertThat(this.lastValue).isEqualTo("1234")
        }
    }

    @Test
    fun `rejecting follow request fails && emits UiError`() = runTest {
        // Given
        timelineCases.stub { onBlocking { rejectFollowRequest(any()) } doThrow httpException }

        viewModel.uiError.test {
            // When
            viewModel.accept(rejectAction)

            // Then
            val item = awaitItem()
            assertThat(item).isInstanceOf(UiError.RejectFollowRequest::class.java)
            assertThat(item.action).isEqualTo(rejectAction)
        }
    }
}
