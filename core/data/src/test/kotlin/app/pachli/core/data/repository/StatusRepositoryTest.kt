/*
 * Copyright (c) 2025 Pachli Association
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

package app.pachli.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.database.dao.StatusDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.PinEvent
import app.pachli.core.network.extensions.getServerErrorMessage
import app.pachli.core.network.model.Status
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.testing.failure
import app.pachli.core.testing.success
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.getError
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.stub
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@Config(application = HiltTestApplication_Application::class)
@RunWith(AndroidJUnit4::class)
class StatusRepositoryTest {
    @get:Rule(order = 0)
    var hilt = HiltAndroidRule(this)

    @Inject
    @ApplicationScope lateinit var externalScope: CoroutineScope

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var transactionProvider: TransactionProvider

    @Inject
    lateinit var statusDao: StatusDao

    @Inject
    lateinit var eventHub: EventHub

    lateinit var statusRepository: StatusRepository

    private val statusId = "1234"

    @Before
    fun setup() {
        hilt.inject()
        reset(mastodonApi)

        statusRepository = StatusRepository(
            externalScope,
            mastodonApi,
            transactionProvider,
            statusDao,
            eventHub,
        )
    }

    @Test
    fun `pin success emits PinEvent`() = runTest {
        mastodonApi.stub {
            onBlocking { pinStatus(statusId) } doReturn success(mockStatus(pinned = true))
        }

        eventHub.events.test {
            statusRepository.pin(1L, statusId, true)
            assertThat(PinEvent(statusId, true)).isEqualTo(awaitItem())
        }
    }

    @Test
    fun `pin failure with server error returns failure with server message`() {
        val apiResult = failure<Status>(
            code = 422,
            responseBody = "{\"error\":\"Validation Failed: You have already pinned the maximum number of toots\"}",
        )

        mastodonApi.stub {
            onBlocking { pinStatus(statusId) } doReturn apiResult
        }

        runBlocking {
            val result = statusRepository.pin(1L, statusId, true) as Err<StatusActionError.Pin>

            assertThat(result.getError()!!.error.throwable.getServerErrorMessage()).isEqualTo(
                "Validation Failed: You have already pinned the maximum number of toots",
            )
        }
    }

    private fun mockStatus(pinned: Boolean = false): Status {
        return Status(
            id = "123",
            url = "https://mastodon.social/@Tusky/100571663297225812",
            account = mock(),
            inReplyToId = null,
            inReplyToAccountId = null,
            reblog = null,
            content = "",
            createdAt = Date(),
            editedAt = null,
            emojis = emptyList(),
            reblogsCount = 0,
            favouritesCount = 0,
            repliesCount = 0,
            reblogged = false,
            favourited = false,
            bookmarked = false,
            sensitive = false,
            spoilerText = "",
            visibility = Status.Visibility.PUBLIC,
            attachments = arrayListOf(),
            mentions = listOf(),
            tags = listOf(),
            application = null,
            pinned = pinned,
            muted = false,
            poll = null,
            card = null,
            language = null,
            filtered = null,
        )
    }
}
