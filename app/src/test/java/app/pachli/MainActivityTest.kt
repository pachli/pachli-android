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

package app.pachli

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import androidx.work.testing.WorkManagerTestInitHelper
import app.pachli.components.accountlist.AccountListActivity
import app.pachli.components.compose.HiltTestApplication_Application
import app.pachli.components.notifications.NotificationHelper
import app.pachli.db.AccountEntity
import app.pachli.db.AccountManager
import app.pachli.db.DraftsAlert
import app.pachli.di.MastodonApiModule
import app.pachli.entity.Account
import app.pachli.entity.Notification
import app.pachli.entity.TimelineAccount
import app.pachli.network.MastodonApi
import app.pachli.rules.lazyActivityScenarioRule
import at.connyduck.calladapter.networkresult.NetworkResult
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.util.concurrent.BackgroundExecutor.runInBackground
import org.robolectric.annotation.Config
import java.util.Date
import javax.inject.Singleton

open class PachliHiltApplication : PachliApplication() { }

@CustomTestApplication(PachliHiltApplication::class)
interface HiltTestApplication

@HiltAndroidTest
@Config(application = HiltTestApplication_Application::class)
@RunWith(AndroidJUnit4::class)
@UninstallModules(MastodonApiModule::class)
class MainActivityTest {
    @get:Rule(order = 0)
    var hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    var rule = lazyActivityScenarioRule<MainActivity>(
        launchActivity = false,
    )

    private val accountEntity = AccountEntity(
        id = 1,
        domain = "test.domain",
        accessToken = "fakeToken",
        clientId = "fakeId",
        clientSecret = "fakeSecret",
        isActive = true,
    )

    @InstallIn(SingletonComponent::class)
    @Module
    object FakeNetworkModule {
        val account = Account(
            id = "1",
            localUsername = "",
            username = "",
            displayName = "",
            createdAt = Date(),
            note = "",
            url = "",
            avatar = "",
            header = "",
        )

        @Provides
        @Singleton
        fun providesApi(): MastodonApi = mock {
            onBlocking { accountVerifyCredentials() } doReturn NetworkResult.success(account)
            onBlocking { listAnnouncements(false) } doReturn NetworkResult.success(emptyList())
        }
    }

    @BindValue
    @JvmField
    val accountManager: AccountManager = mock { on { activeAccount } doReturn accountEntity }

    @BindValue
    @JvmField
    val draftsAlert: DraftsAlert = mock()

    @Before
    fun setup() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            ApplicationProvider.getApplicationContext(),
        )
    }

    // Both tests here hang deep in the Robolectric code that runs as part of `rule.launch()`.
    // From chasing down Robolectric bug reports I suspect MainActivity is doing something
    // in `onCreate` that should be in `onStart`, but I'm not sure what yet. Refactoring
    // to better reflect MVVM may also help.
    //
    // Tests are kept here but ignored for the moment.

    // TODO: Check and see whether refactoring MainActivity has fixed the hangs.

    @Ignore("Hangs, see comment")
    @Test
    fun `clicking notification of type FOLLOW shows notification tab`() {
        val intent = showNotification(
            ApplicationProvider.getApplicationContext(),
            Notification.Type.FOLLOW,
        )
        rule.launch(intent)
        rule.getScenario().onActivity {
            val currentTab = it.findViewById<ViewPager2>(R.id.viewPager).currentItem
            val notificationTab = defaultTabs().indexOfFirst { it.id == NOTIFICATIONS }
            assertEquals(currentTab, notificationTab)
        }
    }

    @Ignore("Hangs, see comment")
    @Test
    fun `clicking notification of type FOLLOW_REQUEST shows follow requests`() {
        val context: Context = ApplicationProvider.getApplicationContext()!!
        val intent = showNotification(
            ApplicationProvider.getApplicationContext(),
            Notification.Type.FOLLOW_REQUEST,
        )

        rule.launch(intent)
        rule.getScenario().onActivity {
            val nextActivity = shadowOf(it).peekNextStartedActivity()
            assertNotNull(nextActivity)
            assertEquals(
                ComponentName(context, AccountListActivity::class.java.name),
                nextActivity.component,
            )
            assertEquals(
                AccountListActivity.Type.FOLLOW_REQUESTS,
                nextActivity.getSerializableExtra("type"),
            )

        }
    }

    private fun showNotification(context: Context, type: Notification.Type): Intent {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val shadowNotificationManager = shadowOf(notificationManager)

        NotificationHelper.createNotificationChannelsForAccount(accountEntity, context)

        runInBackground {
            val notification = NotificationHelper.make(
                context,
                notificationManager,
                Notification(
                    type = type,
                    id = "id",
                    account = TimelineAccount(
                        id = "1",
                        localUsername = "connyduck",
                        username = "connyduck@mastodon.example",
                        displayName = "Conny Duck",
                        note = "This is their bio",
                        url = "https://mastodon.example/@ConnyDuck",
                        avatar = "https://mastodon.example/system/accounts/avatars/000/150/486/original/ab27d7ddd18a10ea.jpg",
                    ),
                    status = null,
                    report = null,
                ),
                accountEntity,
                true,
            )
            notificationManager.notify("id", 1, notification)
        }

        val notification = shadowNotificationManager.allNotifications.first()
        return shadowOf(notification.contentIntent).savedIntent
    }
}
