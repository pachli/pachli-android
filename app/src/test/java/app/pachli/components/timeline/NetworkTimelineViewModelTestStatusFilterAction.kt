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

import app.cash.turbine.test
import app.pachli.components.timeline.viewmodel.FallibleStatusAction
import app.pachli.components.timeline.viewmodel.StatusActionSuccess
import app.pachli.components.timeline.viewmodel.UiError
import app.pachli.components.timeline.viewmodel.UiSuccess
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.database.model.TranslationState
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.testing.extensions.insertTimelineStatusWithQuote
import app.pachli.core.testing.failure
import app.pachli.core.testing.fakes.fakeStatus
import app.pachli.core.testing.fakes.fakeStatusEntityWithAccount
import app.pachli.core.testing.success
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub

/**
 * Verify that [FallibleStatusAction] are handled correctly on receipt:
 *
 * - Is the correct [UiSuccess] or [UiError] value emitted?
 * - Is the correct [TimelineCases] function called, with the correct arguments?
 *   This is only tested in the success case; if it passed there it must also
 *   have passed in the error case.
 */
// TODO: With the exception of the types, this is identical to
// NotificationsViewModelTestStatusAction.
@HiltAndroidTest
class NetworkTimelineViewModelTestStatusFilterAction : NetworkTimelineViewModelTestBase() {
    private val fakeStatus = fakeStatus(pollOptions = listOf("Choice 1", "Choice 2", "Choice 3"))
    private val fakeStatusEntityWithAccount = fakeStatusEntityWithAccount(makeFakeStatus = { fakeStatus })

    private val statusViewData = StatusViewData(
        pachliAccountId = 1L,
        status = fakeStatus.asModel(),
        isExpanded = true,
        isCollapsed = false,
        translationState = TranslationState.SHOW_ORIGINAL,
        attachmentDisplayAction = AttachmentDisplayAction.Show(),
        replyToAccount = null,
        isUsersStatus = false,
    )

    /** Action to bookmark a status */
    private val bookmarkAction = FallibleStatusAction.Bookmark(true, statusViewData)

    /** Action to favourite a status */
    private val favouriteAction = FallibleStatusAction.Favourite(true, statusViewData)

    /** Action to reblog a status */
    private val reblogAction = FallibleStatusAction.Reblog(true, statusViewData)

    /** Action to vote in a poll */
    private val voteInPollAction = FallibleStatusAction.VoteInPoll(
        poll = fakeStatus.poll!!.asModel(),
        choices = listOf(1, 0, 0),
        statusViewData,
    )

    @Before
    override fun setup() = runTest {
        super.setup()

        appDatabase.insertTimelineStatusWithQuote(listOf(fakeStatusEntityWithAccount))
    }

    @Test
    fun `bookmark succeeds && emits Ok uiResult`() = runTest {
        // Given
        mastodonApi.stub { onBlocking { bookmarkStatus(any()) } doReturn success(fakeStatus) }

        viewModel.uiResult.test {
            // When
            viewModel.accept(bookmarkAction)

            // Then
            val item = awaitItem().get() as? StatusActionSuccess.Bookmark
            assertThat(item?.action).isEqualTo(bookmarkAction)
        }
    }

    @Test
    fun `bookmark fails && emits Err uiResult`() = runTest {
        // Given
        mastodonApi.stub { onBlocking { bookmarkStatus(any()) } doReturn failure() }

        viewModel.uiResult.test {
            // When
            viewModel.accept(bookmarkAction)

            // Then
            val item = awaitItem().getError() as? UiError.Bookmark
            assertThat(item?.action).isEqualTo(bookmarkAction)
        }
    }

    @Test
    fun `favourite succeeds && emits Ok uiResult`() = runTest {
        // Given
        mastodonApi.stub { onBlocking { favouriteStatus(any()) } doReturn success(fakeStatus) }

        viewModel.uiResult.test {
            // When
            viewModel.accept(favouriteAction)

            // Then
            val item = awaitItem().get() as? StatusActionSuccess.Favourite
            assertThat(item?.action).isEqualTo(favouriteAction)
        }
    }

    @Test
    fun `favourite fails && emits Err uiResult`() = runTest {
        // Given
        mastodonApi.stub { onBlocking { favouriteStatus(any()) } doReturn failure() }

        viewModel.uiResult.test {
            // When
            viewModel.accept(favouriteAction)

            // Then
            val item = awaitItem().getError() as? UiError.Favourite
            assertThat(item?.action).isEqualTo(favouriteAction)
        }
    }

    @Test
    fun `reblog succeeds && emits Ok uiResult`() = runTest {
        // Given
        mastodonApi.stub { onBlocking { reblogStatus(any()) } doReturn success(fakeStatus) }

        viewModel.uiResult.test {
            // When
            viewModel.accept(reblogAction)

            // Then
            val item = awaitItem().get() as? StatusActionSuccess.Reblog
            assertThat(item?.action).isEqualTo(reblogAction)
        }
    }

    @Test
    fun `reblog fails && emits Err uiResult`() = runTest {
        // Given
        mastodonApi.stub { onBlocking { reblogStatus(any()) } doReturn failure() }

        viewModel.uiResult.test {
            // When
            viewModel.accept(reblogAction)

            // Then
            val item = awaitItem().getError() as? UiError.Reblog
            assertThat(item?.action).isEqualTo(reblogAction)
        }
    }

    @Test
    fun `voteinpoll succeeds && emits Ok uiResult`() = runTest {
        // Given
        mastodonApi.stub { onBlocking { voteInPoll(any(), any()) } doReturn success(fakeStatus.poll!!) }

        viewModel.uiResult.test {
            // When
            viewModel.accept(voteInPollAction)

            // Then
            val item = awaitItem().get() as? StatusActionSuccess.VoteInPoll
            assertThat(item?.action).isEqualTo(voteInPollAction)
        }
    }

    @Test
    fun `voteinpoll fails && emits Err uiResult`() = runTest {
        // Given
        mastodonApi.stub { onBlocking { voteInPoll(any(), any()) } doReturn failure() }

        viewModel.uiResult.test {
            // When
            viewModel.accept(voteInPollAction)

            // Then
            val item = awaitItem().getError() as? UiError.VoteInPoll
            assertThat(item?.action).isEqualTo(voteInPollAction)
        }
    }
}
