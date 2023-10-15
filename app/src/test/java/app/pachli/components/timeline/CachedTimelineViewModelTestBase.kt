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
import app.pachli.PachliApplication
import app.pachli.appstore.EventHub
import app.pachli.components.timeline.viewmodel.CachedTimelineViewModel
import app.pachli.components.timeline.viewmodel.TimelineViewModel
import app.pachli.db.AccountManager
import app.pachli.entity.Account
import app.pachli.network.FilterModel
import app.pachli.network.MastodonApi
import app.pachli.settings.AccountPreferenceDataStore
import app.pachli.usecase.TimelineCases
import app.pachli.util.SharedPreferencesRepository
import app.pachli.util.StatusDisplayOptionsRepository
import at.connyduck.calladapter.networkresult.NetworkResult
import com.google.gson.Gson
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.TestScope
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.stub
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.time.Instant
import java.util.Date
import javax.inject.Inject

open class PachliHiltApplication : PachliApplication()

@CustomTestApplication(PachliHiltApplication::class)
interface HiltTestApplication

@HiltAndroidTest
@Config(application = HiltTestApplication_Application::class)
@RunWith(AndroidJUnit4::class)
abstract class CachedTimelineViewModelTestBase {
    @get:Rule(order = 0)
    var hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val mainCoroutineRule = MainCoroutineRule()

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var sharedPreferencesRepository: SharedPreferencesRepository

    private lateinit var cachedTimelineRepository: CachedTimelineRepository
    private lateinit var accountPreferenceDataStore: AccountPreferenceDataStore
    protected lateinit var timelineCases: TimelineCases
    private lateinit var statusDisplayOptionsRepository: StatusDisplayOptionsRepository
    private lateinit var filtersRepository: FiltersRepository
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

        reset(mastodonApi)
        mastodonApi.stub {
            onBlocking { getCustomEmojis() } doReturn NetworkResult.failure(Exception())
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

        cachedTimelineRepository = mock()

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

        viewModel = CachedTimelineViewModel(
            cachedTimelineRepository,
            timelineCases,
            eventHub,
            filtersRepository,
            accountManager,
            statusDisplayOptionsRepository,
            sharedPreferencesRepository,
            filterModel,
            Gson(),
        )

        // Initialisation with the Home timeline
        viewModel.init(TimelineKind.Home)
    }
}
