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
import app.pachli.settings.PrefKeys
import app.pachli.usecase.TimelineCases
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.Shadows.shadowOf
import retrofit2.HttpException
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
abstract class NotificationsViewModelTestBase {
    protected lateinit var notificationsRepository: NotificationsRepository
    protected lateinit var sharedPreferences: SharedPreferences
    protected lateinit var accountManager: AccountManager
    protected lateinit var timelineCases: TimelineCases
    protected lateinit var eventHub: EventHub
    protected lateinit var viewModel: NotificationsViewModel
    protected lateinit var filtersRepository: FiltersRepository
    protected lateinit var filterModel: FilterModel

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

        sharedPreferences = InMemorySharedPreferences(
            mapOf(
                PrefKeys.ANIMATE_GIF_AVATARS to false,
                PrefKeys.ANIMATE_CUSTOM_EMOJIS to false,
                PrefKeys.ABSOLUTE_TIME_VIEW to false,
                PrefKeys.SHOW_BOT_OVERLAY to true,
                PrefKeys.USE_BLURHASH to true,
                PrefKeys.CONFIRM_REBLOGS to true,
                PrefKeys.CONFIRM_FAVOURITES to false,
                PrefKeys.WELLBEING_HIDE_STATS_POSTS to false,
                PrefKeys.FAB_HIDE to false,
            ),
        )

        accountManager = mock {
            on { activeAccount } doReturn AccountEntity(
                id = 1,
                domain = "mastodon.test",
                accessToken = "fakeToken",
                clientId = "fakeId",
                clientSecret = "fakeSecret",
                isActive = true,
                notificationsFilter = "['follow']",
                mediaPreviewEnabled = true,
                alwaysShowSensitiveMedia = true,
                alwaysOpenSpoiler = true,
            )
        }
        eventHub = EventHub()
        timelineCases = mock()
        filtersRepository = mock()
        filterModel = mock()

        viewModel = NotificationsViewModel(
            notificationsRepository,
            sharedPreferences,
            accountManager,
            timelineCases,
            eventHub,
            filtersRepository,
            filterModel,
        )
    }
}
