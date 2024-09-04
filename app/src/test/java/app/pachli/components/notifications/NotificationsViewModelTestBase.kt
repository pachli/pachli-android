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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.pachli.appstore.EventHub
import app.pachli.core.accounts.AccountManager
import app.pachli.core.data.repository.AccountPreferenceDataStore
import app.pachli.core.data.repository.ContentFilters
import app.pachli.core.data.repository.ContentFiltersError
import app.pachli.core.data.repository.ContentFiltersRepository
import app.pachli.core.data.repository.ServerRepository
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.network.model.nodeinfo.UnvalidatedJrd
import app.pachli.core.network.model.nodeinfo.UnvalidatedNodeInfo
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.NodeInfoApi
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.testing.fakes.InMemorySharedPreferences
import app.pachli.core.testing.rules.MainCoroutineRule
import app.pachli.usecase.TimelineCases
import at.connyduck.calladapter.networkresult.NetworkResult
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
abstract class NotificationsViewModelTestBase {
    protected lateinit var notificationsRepository: NotificationsRepository
    protected lateinit var sharedPreferencesRepository: SharedPreferencesRepository
    protected lateinit var accountManager: AccountManager
    protected lateinit var timelineCases: TimelineCases
    protected lateinit var viewModel: NotificationsViewModel
    private lateinit var statusDisplayOptionsRepository: StatusDisplayOptionsRepository
    private lateinit var contentFiltersRepository: ContentFiltersRepository

    private val eventHub = EventHub()

    private lateinit var accountPreferenceDataStore: AccountPreferenceDataStore

    /** Empty success response, for API calls that return one */
    protected var emptySuccess: Response<ResponseBody> = Response.success("".toResponseBody())

    /** Empty error response, for API calls that return one */
    protected var emptyError: Response<ResponseBody> = Response.error(404, "".toResponseBody())

    /** Exception to throw when testing errors */
    protected val httpException = HttpException(emptyError)

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    @Before
    fun setup() {
        notificationsRepository = mock()

        val defaultAccount = AccountEntity(
            id = 1,
            domain = "mastodon.test",
            accessToken = "fakeToken",
            clientId = "fakeId",
            clientSecret = "fakeSecret",
            isActive = true,
            notificationsFilter = "['follow']",
        )

        val activeAccountFlow = MutableStateFlow(defaultAccount)

        accountManager = mock {
            on { activeAccount } doReturn defaultAccount
            whenever(it.activeAccountFlow).thenReturn(activeAccountFlow)
        }

        accountPreferenceDataStore = AccountPreferenceDataStore(
            accountManager,
            TestScope(),
        )

        timelineCases = mock()

        contentFiltersRepository = mock {
            whenever(it.contentFilters).thenReturn(MutableStateFlow<Result<ContentFilters?, ContentFiltersError.GetContentFiltersError>>(Ok(null)))
        }

        sharedPreferencesRepository = SharedPreferencesRepository(
            InMemorySharedPreferences(),
            TestScope(),
        )

        val mastodonApi: MastodonApi = mock {
            onBlocking { getInstanceV2() } doAnswer { null }
            onBlocking { getInstanceV1() } doAnswer { null }
        }

        val nodeInfoApi: NodeInfoApi = mock {
            onBlocking { nodeInfoJrd() } doReturn NetworkResult.success(
                UnvalidatedJrd(
                    listOf(
                        UnvalidatedJrd.Link(
                            "http://nodeinfo.diaspora.software/ns/schema/2.1",
                            "https://example.com",
                        ),
                    ),
                ),
            )
            onBlocking { nodeInfo(any()) } doReturn NetworkResult.success(
                UnvalidatedNodeInfo(UnvalidatedNodeInfo.Software("mastodon", "4.2.0")),
            )
        }

        val serverRepository = ServerRepository(
            mastodonApi,
            nodeInfoApi,
            accountManager,
            TestScope(),
        )

        statusDisplayOptionsRepository = StatusDisplayOptionsRepository(
            sharedPreferencesRepository,
            serverRepository,
            accountManager,
            accountPreferenceDataStore,
            TestScope(),
        )

        viewModel = NotificationsViewModel(
            InstrumentationRegistry.getInstrumentation().targetContext,
            notificationsRepository,
            accountManager,
            timelineCases,
            eventHub,
            contentFiltersRepository,
            statusDisplayOptionsRepository,
            sharedPreferencesRepository,
        )
    }
}
