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
import app.pachli.core.database.AppDatabase
import app.pachli.core.database.dao.StatusDao
import app.pachli.core.database.dao.TranslatedStatusDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.PinEvent
import app.pachli.core.network.extensions.getServerErrorMessage
import app.pachli.core.network.model.AccountSource
import app.pachli.core.network.model.CredentialAccount
import app.pachli.core.network.model.InstanceConfiguration
import app.pachli.core.network.model.InstanceV1
import app.pachli.core.network.model.Status
import app.pachli.core.network.model.nodeinfo.UnvalidatedJrd
import app.pachli.core.network.model.nodeinfo.UnvalidatedNodeInfo
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.NodeInfoApi
import app.pachli.core.testing.extensions.insertStatuses
import app.pachli.core.testing.failure
import app.pachli.core.testing.fakes.fakeStatus
import app.pachli.core.testing.fakes.fakeStatusEntityWithAccount
import app.pachli.core.testing.rules.MainCoroutineRule
import app.pachli.core.testing.success
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.onSuccess
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
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

    @get:Rule(order = 1)
    val mainCoroutineRule = MainCoroutineRule()

    @Inject
    @ApplicationScope lateinit var externalScope: CoroutineScope

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var nodeInfoApi: NodeInfoApi

    @Inject
    lateinit var transactionProvider: TransactionProvider

    @Inject
    lateinit var statusDao: StatusDao

    @Inject
    lateinit var translatedStatusDao: TranslatedStatusDao

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var accountManager: AccountManager

    private val account = CredentialAccount(
        id = "1",
        localUsername = "username",
        username = "username@domain.example",
        displayName = "Display Name",
        createdAt = Instant.now(),
        note = "",
        url = "",
        avatar = "",
        header = "",
        source = AccountSource(),
    )

    private lateinit var statusRepository: OfflineFirstStatusRepository

    @Before
    fun setup() = runTest {
        hilt.inject()

        reset(mastodonApi)
        mastodonApi.stub {
            onBlocking { accountVerifyCredentials(anyOrNull(), anyOrNull()) } doReturn success(account)
            onBlocking { getCustomEmojis() } doReturn success(emptyList())
            onBlocking { getInstanceV2() } doReturn failure()
            onBlocking { getInstanceV1(anyOrNull()) } doReturn success(
                InstanceV1(
                    uri = "https://example.token",
                    version = "2.6.3",
                    maxTootChars = 500,
                    pollConfiguration = null,
                    configuration = InstanceConfiguration(),
                    pleroma = null,
                    uploadLimit = null,
                    rules = emptyList(),
                ),
            )
            onBlocking { getLists() } doReturn success(emptyList())
            onBlocking { listAnnouncements(any()) } doReturn success(emptyList())
            onBlocking { getContentFilters() } doReturn success(emptyList())
            onBlocking { getContentFiltersV1() } doReturn success(emptyList())
            onBlocking { accountFollowing(any(), anyOrNull(), any()) } doReturn success(emptyList())
        }

        reset(nodeInfoApi)
        nodeInfoApi.stub {
            onBlocking { nodeInfoJrd() } doReturn success(
                UnvalidatedJrd(
                    listOf(
                        UnvalidatedJrd.Link(
                            "http://nodeinfo.diaspora.software/ns/schema/2.1",
                            "https://example.com",
                        ),
                    ),
                ),
            )
            onBlocking { nodeInfo(any()) } doReturn success(
                UnvalidatedNodeInfo(UnvalidatedNodeInfo.Software("mastodon", "4.2.0")),
            )
        }

        accountManager.verifyAndAddAccount(
            accessToken = "token",
            domain = "example.com",
            clientId = "id",
            clientSecret = "secret",
            oauthScopes = "scopes",
        )
            .andThen { accountManager.setActiveAccount(it) }
            .onSuccess { accountManager.refresh(it) }

        statusRepository = OfflineFirstStatusRepository(
            externalScope,
            mastodonApi,
            transactionProvider,
            statusDao,
            translatedStatusDao,
            eventHub,
        )
    }

    @Test
    fun `pin success emits PinEvent`() = runTest {
        val fakeStatus = fakeStatus()
        val fakeStatusEntityWithAccount = fakeStatusEntityWithAccount(makeFakeStatus = { fakeStatus })
        val statusId = fakeStatus.id

        appDatabase.insertStatuses(listOf(fakeStatusEntityWithAccount))

        mastodonApi.stub {
            onBlocking { pinStatus(statusId) } doReturn success(fakeStatus)
        }

        eventHub.events.test {
            statusRepository.pin(1L, statusId, true)
            assertThat(PinEvent(statusId, true)).isEqualTo(awaitItem())
        }
    }

    @Test
    fun `pin failure with server error returns failure with server message`() {
        val statusId = "1234"

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
}
