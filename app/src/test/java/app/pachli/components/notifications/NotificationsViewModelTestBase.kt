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

import android.content.SharedPreferences
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pachli.appstore.EventHub
import app.pachli.components.timeline.FiltersRepository
import app.pachli.components.timeline.MainCoroutineRule
import app.pachli.db.AccountEntity
import app.pachli.db.AccountManager
import app.pachli.fakes.InMemorySharedPreferences
import app.pachli.network.FilterModel
import app.pachli.network.MastodonApi
import app.pachli.usecase.TimelineCases
import app.pachli.util.SharedPreferencesRepository
import app.pachli.util.StatusDisplayOptionsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
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
abstract class NotificationsViewModelTestBase {
    protected lateinit var notificationsRepository: NotificationsRepository
    protected lateinit var sharedPreferences: SharedPreferences
    protected lateinit var accountManager: AccountManager
    protected lateinit var timelineCases: TimelineCases
    protected lateinit var viewModel: NotificationsViewModel
    private lateinit var statusDisplayOptionsRepository: StatusDisplayOptionsRepository
    private lateinit var filtersRepository: FiltersRepository
    private lateinit var filterModel: FilterModel

    private val eventHub = EventHub()

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
        shadowOf(Looper.getMainLooper()).idle()

        notificationsRepository = mock()

        sharedPreferences = InMemorySharedPreferences(null)

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

        viewModel = NotificationsViewModel(
            notificationsRepository,
            accountManager,
            timelineCases,
            eventHub,
            filtersRepository,
            filterModel,
            statusDisplayOptionsRepository,
            sharedPreferencesRepository,
        )
    }
}
