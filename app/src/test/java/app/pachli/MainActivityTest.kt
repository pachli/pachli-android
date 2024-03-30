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
import app.pachli.components.notifications.createNotificationChannelsForAccount
import app.pachli.components.notifications.makeNotification
import app.pachli.core.accounts.AccountManager
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.defaultTabs
import app.pachli.core.model.Timeline
import app.pachli.core.navigation.AccountListActivityIntent
import app.pachli.core.network.model.Account
import app.pachli.core.network.model.Notification
import app.pachli.core.network.model.TimelineAccount
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.testing.rules.lazyActivityScenarioRule
import app.pachli.db.DraftsAlert
import at.connyduck.calladapter.networkresult.NetworkResult
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.util.Date
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.stub
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.util.concurrent.BackgroundExecutor.runInBackground
import org.robolectric.annotation.Config

open class PachliHiltApplication : PachliApplication()

@CustomTestApplication(PachliHiltApplication::class)
interface HiltTestApplication

@HiltAndroidTest
@Config(application = HiltTestApplication_Application::class)
@RunWith(AndroidJUnit4::class)
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

    @Inject
    lateinit var mastodonApi: MastodonApi

    val account = Account(
        id = "1",
        localUsername = "username",
        username = "username@domain.example",
        displayName = "Display Name",
        createdAt = Date.from(Instant.now()),
        note = "",
        url = "",
        avatar = "",
        header = "",
    )

    @Inject
    lateinit var accountManager: AccountManager

    @BindValue
    @JvmField
    val draftsAlert: DraftsAlert = mock()

    @Before
    fun setup() {
        hilt.inject()

        reset(mastodonApi)
        mastodonApi.stub {
            onBlocking { accountVerifyCredentials() } doReturn NetworkResult.success(account)
            onBlocking { listAnnouncements(false) } doReturn NetworkResult.success(emptyList())
        }

        accountManager.addAccount(
            accessToken = "token",
            domain = "domain.example",
            clientId = "id",
            clientSecret = "secret",
            oauthScopes = "scopes",
            newAccount = account,
        )

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
            val notificationTab = defaultTabs().indexOfFirst { it is Timeline.Notifications }
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
                AccountListActivityIntent.Kind.FOLLOW_REQUESTS,
                nextActivity.getSerializableExtra("type"),
            )
        }
    }

    private fun showNotification(context: Context, type: Notification.Type): Intent {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val shadowNotificationManager = shadowOf(notificationManager)

        createNotificationChannelsForAccount(accountEntity, context)

        runInBackground {
            val notification = makeNotification(
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
