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

import android.content.SharedPreferences
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pachli.appstore.EventHub
import app.pachli.appstore.PreferenceChangedEvent
import app.pachli.components.timeline.viewmodel.NetworkTimelineViewModel
import app.pachli.components.timeline.viewmodel.TimelineViewModel
import app.pachli.db.AccountEntity
import app.pachli.db.AccountManager
import app.pachli.fakes.InMemorySharedPreferences
import app.pachli.network.FilterModel
import app.pachli.network.MastodonApi
import app.pachli.network.ServerCapabilitiesRepository
import app.pachli.settings.AccountPreferenceDataStore
import app.pachli.settings.PrefKeys
import app.pachli.usecase.TimelineCases
import app.pachli.util.SharedPreferencesRepository
import app.pachli.util.StatusDisplayOptionsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
abstract class NetworkTimelineViewModelTestBase {
    private lateinit var networkTimelineRepository: NetworkTimelineRepository
    protected lateinit var sharedPreferences: SharedPreferences
    private lateinit var accountPreferencesMap: MutableMap<String, Boolean>
    private lateinit var accountPreferenceDataStore: AccountPreferenceDataStore
    protected lateinit var accountManager: AccountManager
    protected lateinit var timelineCases: TimelineCases
    private lateinit var filtersRepository: FiltersRepository
    private lateinit var statusDisplayOptionsRepository: StatusDisplayOptionsRepository
    private lateinit var filterModel: FilterModel
    protected lateinit var viewModel: TimelineViewModel

    private val eventHub = EventHub()

    /** Empty success response, for API calls that return one */
    protected var emptySuccess = Response.success("".toResponseBody())

    /** Empty error response, for API calls that return one */
    private var emptyError: Response<ResponseBody> = Response.error(404, "".toResponseBody())

    /** Exception to throw when testing errors */
    protected val httpException = HttpException(emptyError)

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    @Before
    fun setup() = runTest {
        shadowOf(Looper.getMainLooper()).idle()

        networkTimelineRepository = mock()

        // Backing store for sharedPreferences, to allow mutation in tests
        sharedPreferences = InMemorySharedPreferences()

        // Backing store for account preferences, to allow mutation in tests
        accountPreferencesMap = mutableMapOf(
            PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA to false,
            PrefKeys.ALWAYS_OPEN_SPOILER to false,
            PrefKeys.MEDIA_PREVIEW_ENABLED to true,
        )

        // Any getBoolean() call looks for the result in accountPreferencesMap.
        // Any putBoolean() call updates the map and dispatches an event
        accountPreferenceDataStore = mock {
            on { getBoolean(any(), any()) } doAnswer { accountPreferencesMap[it.arguments[0]] }
            on { putBoolean(anyString(), anyBoolean()) } doAnswer {
                accountPreferencesMap[it.arguments[0] as String] = it.arguments[1] as Boolean
                runBlocking { eventHub.dispatch(PreferenceChangedEvent(it.arguments[0] as String)) }
            }
        }

        val defaultAccount = AccountEntity(
            id = 1,
            domain = "mastodon.test",
            accessToken = "fakeToken",
            clientId = "fakeId",
            clientSecret = "fakeSecret",
            isActive = true,
            lastVisibleHomeTimelineStatusId = null,
            notificationsFilter = "['follow']",
        )

        val activeAccountFlow = MutableStateFlow(defaultAccount)

        accountManager = mock {
            on { activeAccount } doReturn defaultAccount
            whenever(it.activeAccountFlow).thenReturn(activeAccountFlow)
        }

        timelineCases = mock()
        filtersRepository = mock()
        filterModel = mock()

        val sharedPreferencesRepository = SharedPreferencesRepository(
            sharedPreferences,
            TestScope()
        )

        val mastodonApi: MastodonApi = mock {
            onBlocking { getInstanceV2() } doAnswer { null }
            onBlocking { getInstanceV1() } doAnswer { null }
        }

        val serverCapabilitiesRepository = ServerCapabilitiesRepository(
            mastodonApi,
            accountManager,
            TestScope(),
        )

        statusDisplayOptionsRepository = StatusDisplayOptionsRepository(
            sharedPreferencesRepository,
            serverCapabilitiesRepository,
            accountManager,
            TestScope(),
        )

        viewModel = NetworkTimelineViewModel(
            networkTimelineRepository,
            timelineCases,
            eventHub,
            filtersRepository,
            accountManager,
            statusDisplayOptionsRepository,
            sharedPreferencesRepository,
            filterModel,
        )

        // Initialisation with any timeline kind, as long as it's not Home
        // (Home uses CachedTimelineViewModel)
        viewModel.init(TimelineKind.Bookmarks)
    }
}
