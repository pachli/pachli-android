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

package app.pachli.core.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pachli.core.database.AppDatabase
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.FilterActionUpdate
import app.pachli.core.database.model.NotificationEntity
import app.pachli.core.database.model.NotificationRelationshipSeveranceEventEntity
import app.pachli.core.database.model.NotificationReportEntity
import app.pachli.core.database.model.NotificationViewDataEntity
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.model.FilterAction
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ensures foreign key relationships are created in entities that reference
 * a [NotificationEntity] so that deleting the notification also deletes all data
 * associated with the notification.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NotificationEntityForeignKeyTest {
    @get:Rule(order = 0)
    var hilt = HiltAndroidRule(this)

    @Inject lateinit var db: AppDatabase

    @Inject lateinit var accountDao: AccountDao

    @Inject lateinit var notificationDao: NotificationDao

    @Inject lateinit var timelineDao: TimelineDao

    private val pachliAccountId = 1L

    /**
     * The user account that will be related to the other entities.
     * Deleting this account should trigger a cascading delete of each
     * entity under test.
     */
    private val activeAccount = AccountEntity(
        id = pachliAccountId,
        domain = "mastodon.example",
        accessToken = "token",
        clientId = "id",
        clientSecret = "secret",
        isActive = true,
    )

    /**
     * Example remote account that sends the statuses and notifications
     * referenced in these tests.
     */
    private val timelineAccount = TimelineAccountEntity(
        serverId = "1",
        timelineUserId = pachliAccountId,
        localUsername = "example",
        username = "example",
        displayName = "Example",
        url = "https://example.com",
        avatar = "https://example.com/avatar",
        emojis = emptyList(),
        bot = false,
        createdAt = Instant.now(),
        limited = false,
        note = "",
    )

    @Before
    fun setup() {
        hilt.inject()

        // Given -- create the account, and populate the other tables with
        // entities that reference this account.
        runTest {
            accountDao.upsert(activeAccount)
            timelineDao.insertAccount(timelineAccount)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Suppress("DEPRECATION")
    @Test
    fun `deleting notification deletes notification and NotificationReportEntity`() = runTest {
        val notification = NotificationEntity(
            pachliAccountId = pachliAccountId,
            serverId = "1",
            type = NotificationEntity.Type.REPORT,
            createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
            accountServerId = "1",
            statusServerId = null,
        )
        notificationDao.upsertNotifications(listOf(notification))
        val notificationReport = NotificationReportEntity(
            pachliAccountId = pachliAccountId,
            serverId = "1",
            reportId = "1",
            actionTaken = true,
            actionTakenAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
            category = NotificationReportEntity.Category.SPAM,
            comment = "",
            forwarded = false,
            createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
            statusIds = null,
            ruleIds = null,
            targetAccount = TimelineAccountEntity(
                serverId = "1",
                timelineUserId = pachliAccountId,
                localUsername = "foo@bar",
                username = "foo",
                displayName = "Foo",
                url = "",
                avatar = "",
                emojis = emptyList(),
                bot = false,
                createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
                limited = false,
                note = "",
            ),
        )
        notificationDao.upsertReports(listOf(notificationReport))

        // Check everything is as expected.
        assertThat(notificationDao.loadAllForAccount(pachliAccountId)).containsExactly(notification)
        assertThat(notificationDao.loadReportById(pachliAccountId, "1")).isEqualTo(notificationReport)

        // When -- Delete the notification, not the account
        notificationDao.deleteNotification(notification)

        // Then
        assertThat(notificationDao.loadAllForAccount(pachliAccountId)).isEmpty()
        assertThat(notificationDao.loadReportById(pachliAccountId, "1")).isNull()
    }

    // TODO: Deleting Notification with NotifcationRelationshipSeveranceEntity
    @Suppress("DEPRECATION")
    @Test
    fun `deleting notification deletes notification and NotificationRelationshipSeveranceEventEntity`() = runTest {
        val notification = NotificationEntity(
            pachliAccountId = pachliAccountId,
            serverId = "1",
            type = NotificationEntity.Type.REPORT,
            createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
            accountServerId = "1",
            statusServerId = null,
        )
        notificationDao.upsertNotifications(listOf(notification))

        val notificationRelationShipSeveranceEvent = NotificationRelationshipSeveranceEventEntity(
            pachliAccountId = pachliAccountId,
            serverId = "1",
            eventId = "1",
            type = NotificationRelationshipSeveranceEventEntity.Type.DOMAIN_BLOCK,
            purged = false,
            followersCount = 1,
            followingCount = 1,
            createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
        )
        notificationDao.upsertEvents(listOf(notificationRelationShipSeveranceEvent))

        // Check everything is as expected.
        assertThat(notificationDao.loadAllForAccount(pachliAccountId)).containsExactly(notification)
        assertThat(notificationDao.loadRelationshipSeveranceeventById(pachliAccountId, "1")).isEqualTo(notificationRelationShipSeveranceEvent)

        // When -- Delete the notification, not the account.
        notificationDao.deleteNotification(notification)

        // Then -- notification and event should be deleted.
        assertThat(notificationDao.loadAllForAccount(pachliAccountId)).isEmpty()
        assertThat(notificationDao.loadRelationshipSeveranceeventById(pachliAccountId, "1")).isNull()
    }

    /**
     * [NotificationViewDataEntity] **does not** have a cascading delete relationship
     * with [NotificationEntity]. If it did, every time notifications were deleted
     * (e.g., when refreshing) the user's notification view data would also be deleted,
     * rendering it useless.
     *
     * This test is the inverse of the previous two -- it verifies the data is
     * not deleted.
     *
     * The data is deleted when the account is deleted, see [AccountEntityForeignKeyTest.deleting account deletes notification and viewdata].
     */
    @Suppress("DEPRECATION")
    @Test
    fun `deleting notification does not delete notification view data`() = runTest {
        val notification = NotificationEntity(
            pachliAccountId = pachliAccountId,
            serverId = "1",
            type = NotificationEntity.Type.FAVOURITE,
            createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
            accountServerId = "1",
            statusServerId = "1",
        )
        notificationDao.upsertNotifications(listOf(notification))
        val filterAction = FilterActionUpdate(
            pachliAccountId = pachliAccountId,
            serverId = "1",
            contentFilterAction = FilterAction.NONE,
        )
        notificationDao.upsert(filterAction)

        // Check everything is as expected.
        val notificationViewData = NotificationViewDataEntity(
            pachliAccountId = pachliAccountId,
            serverId = "1",
            contentFilterAction = FilterAction.NONE,
            accountFilterDecision = null,
        )

        assertThat(notificationDao.loadAllForAccount(pachliAccountId)).containsExactly(notification)
        assertThat(notificationDao.loadViewData(pachliAccountId, "1")).isEqualTo(notificationViewData)

        // When -- delete the notification.
        notificationDao.deleteNotification(notification)

        // Then
        assertThat(notificationDao.loadAllForAccount(pachliAccountId)).isEmpty()
        assertThat(notificationDao.loadViewData(pachliAccountId, "1")).isEqualTo(notificationViewData)
    }
}
