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

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pachli.appstore.EventHub
import app.pachli.components.timeline.viewmodel.NetworkTimelineViewModel
import app.pachli.components.timeline.viewmodel.TimelineViewModel
import app.pachli.core.accounts.AccountManager
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.model.Timeline
import app.pachli.core.network.model.Account
import app.pachli.core.network.model.nodeinfo.UnvalidatedJrd
import app.pachli.core.network.model.nodeinfo.UnvalidatedNodeInfo
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.NodeInfoApi
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.testing.rules.MainCoroutineRule
import app.pachli.usecase.TimelineCases
import app.pachli.util.HiltTestApplication_Application
import at.connyduck.calladapter.networkresult.NetworkResult
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.util.Date
import javax.inject.Inject
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.stub
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

@HiltAndroidTest
@Config(application = HiltTestApplication_Application::class)
@RunWith(AndroidJUnit4::class)
abstract class NetworkTimelineViewModelTestBase {
    @get:Rule(order = 0)
    var hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val mainCoroutineRule = MainCoroutineRule()

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var nodeInfoApi: NodeInfoApi

    @Inject
    lateinit var sharedPreferencesRepository: SharedPreferencesRepository

    @Inject
    lateinit var filtersRepository: FiltersRepository

    @Inject
    lateinit var networkTimelineRepository: NetworkTimelineRepository

    @Inject
    lateinit var statusDisplayOptionsRepository: StatusDisplayOptionsRepository

    protected lateinit var timelineCases: TimelineCases
    protected lateinit var viewModel: TimelineViewModel

    private val eventHub = EventHub()

    /** Empty error response, for API calls that return one */
    private var emptyError: Response<ResponseBody> = Response.error(404, "".toResponseBody())

    /** Exception to throw when testing errors */
    protected val httpException = HttpException(emptyError)

    @Before
    fun setup() {
        hilt.inject()

        reset(mastodonApi)
        mastodonApi.stub {
            onBlocking { getCustomEmojis() } doReturn NetworkResult.failure(Exception())
            onBlocking { getFilters() } doReturn NetworkResult.success(emptyList())
        }

        reset(nodeInfoApi)
        nodeInfoApi.stub {
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

        accountManager.addAccount(
            accessToken = "token",
            domain = "domain.example",
            clientId = "id",
            clientSecret = "secret",
            oauthScopes = "scopes",
            newAccount = Account(
                id = "1",
                localUsername = "username",
                username = "username@domain.example",
                displayName = "Display Name",
                createdAt = Date.from(Instant.now()),
                note = "",
                url = "",
                avatar = "",
                header = "",
            ),
        )

        timelineCases = mock()

        viewModel = NetworkTimelineViewModel(
            SavedStateHandle(mapOf(TimelineViewModel.TIMELINE_TAG to Timeline.Bookmarks)),
            networkTimelineRepository,
            timelineCases,
            eventHub,
            filtersRepository,
            accountManager,
            statusDisplayOptionsRepository,
            sharedPreferencesRepository,
        )
    }
}
