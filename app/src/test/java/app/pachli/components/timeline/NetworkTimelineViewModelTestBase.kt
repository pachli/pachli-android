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

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pachli.appstore.EventHub
import app.pachli.components.timeline.viewmodel.NetworkTimelineViewModel
import app.pachli.components.timeline.viewmodel.TimelineViewModel
import app.pachli.core.accounts.AccountManager
import app.pachli.core.network.model.Account
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.testing.rules.MainCoroutineRule
import app.pachli.network.FilterModel
import app.pachli.settings.AccountPreferenceDataStore
import app.pachli.usecase.TimelineCases
import app.pachli.util.StatusDisplayOptionsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.TestScope
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.time.Instant
import java.util.Date
import javax.inject.Inject

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
    lateinit var sharedPreferencesRepository: SharedPreferencesRepository

    private lateinit var networkTimelineRepository: NetworkTimelineRepository
    private lateinit var accountPreferenceDataStore: AccountPreferenceDataStore
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

    @Before
    fun setup() {
        hilt.inject()

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

        networkTimelineRepository = mock()

        accountPreferenceDataStore = AccountPreferenceDataStore(
            accountManager,
            TestScope(),
        )

        timelineCases = mock()
        filtersRepository = mock()
        filterModel = mock()

        statusDisplayOptionsRepository = StatusDisplayOptionsRepository(
            sharedPreferencesRepository,
            accountManager,
            accountPreferenceDataStore,
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
