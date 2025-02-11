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
import app.pachli.core.database.model.AnnouncementEntity
import app.pachli.core.database.model.ContentFiltersEntity
import app.pachli.core.database.model.ConversationAccountEntity
import app.pachli.core.database.model.ConversationEntity
import app.pachli.core.database.model.ConversationStatus
import app.pachli.core.database.model.DraftEntity
import app.pachli.core.database.model.EmojisEntity
import app.pachli.core.database.model.FilterActionUpdate
import app.pachli.core.database.model.FollowingAccountEntity
import app.pachli.core.database.model.MastodonListEntity
import app.pachli.core.database.model.NotificationEntity
import app.pachli.core.database.model.NotificationViewDataEntity
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.ServerEntity
import app.pachli.core.database.model.StatusViewDataEntity
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.database.model.TranslatedStatusEntity
import app.pachli.core.database.model.TranslationState
import app.pachli.core.model.ContentFilterVersion
import app.pachli.core.model.FilterAction
import app.pachli.core.model.ServerKind
import app.pachli.core.network.model.Announcement
import app.pachli.core.network.model.Status
import app.pachli.core.network.model.UserListRepliesPolicy
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.z4kn4fein.semver.Version
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ensures foreign key relationships are created in entities that reference
 * an [AccountEntity] so that deleting the user's account also deletes all data
 * associated with their account.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AccountEntityForeignKeyTest {
    @get:Rule(order = 0)
    var hilt = HiltAndroidRule(this)

    @Inject lateinit var db: AppDatabase

    @Inject lateinit var accountDao: AccountDao

    @Inject lateinit var announcementDao: AnnouncementsDao

    @Inject lateinit var contentFiltersDao: ContentFiltersDao

    @Inject lateinit var conversationDao: ConversationsDao

    @Inject lateinit var draftDao: DraftDao

    @Inject lateinit var followingAccountDao: FollowingAccountDao

    @Inject lateinit var instanceInfoDao: InstanceDao

    @Inject lateinit var mastodonListDao: ListsDao

    @Inject lateinit var notificationDao: NotificationDao

    @Inject lateinit var remoteKeyDao: RemoteKeyDao

    @Inject lateinit var timelineDao: TimelineDao

    @Inject lateinit var translatedStatusDao: TranslatedStatusDao

    @Inject lateinit var statusDao: StatusDao

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

    @Test
    fun `deleting account deletes AccountEntity`() = runTest {
        // When -- delete the account.
        accountDao.delete(activeAccount)

        // Then -- the account table should be empty.
        assertThat(accountDao.loadAll()).isEmpty()
    }

    @Test
    fun `deleting account deletes AnnouncementEntity`() = runTest {
        val announcement = AnnouncementEntity(
            accountId = pachliAccountId,
            announcementId = "1",
            announcement = Announcement(
                id = "1",
                content = "Test announcement",
                startsAt = null,
                endsAt = null,
                allDay = false,
                publishedAt = Date(),
                updatedAt = Date(),
                read = false,
                mentions = emptyList(),
                statuses = emptyList(),
                tags = emptyList(),
                emojis = emptyList(),
                reactions = emptyList(),
            ),
        )
        announcementDao.upsert(announcement)

        // Check everything is as expected.
        assertThat(announcementDao.loadAllForAccount(pachliAccountId)).containsExactly(announcement)

        // When -- delete the account.
        accountDao.delete(activeAccount)

        // Then -- all the other tables should be empty.
        assertThat(announcementDao.loadAllForAccount(pachliAccountId)).isEmpty()
    }

    @Test
    fun `deleting account deletes ContentFiltersEntity`() = runTest {
        val contentFilters = ContentFiltersEntity(
            accountId = pachliAccountId,
            version = ContentFilterVersion.V2,
            contentFilters = emptyList(),
        )
        contentFiltersDao.upsert(contentFilters)

        // Check everything is as expected.
        assertThat(contentFiltersDao.getByAccount(pachliAccountId)).isEqualTo(contentFilters)

        // When -- delete the account
        accountDao.delete(activeAccount)

        // Then
        assertThat(contentFiltersDao.getByAccount(pachliAccountId)).isNull()
    }

    @Test
    fun `deleting account deletes ConversationEntity`() = runTest {
        val conversation = ConversationEntity(
            accountId = pachliAccountId,
            id = "1",
            order = 1,
            accounts = emptyList(),
            unread = true,
            lastStatus = ConversationStatus(
                id = "1",
                url = null,
                inReplyToId = null,
                inReplyToAccountId = null,
                account = ConversationAccountEntity(
                    id = "1",
                    localUsername = "foo@bar",
                    username = "foo",
                    displayName = "Foo",
                    avatar = "",
                    emojis = emptyList(),
                    createdAt = Instant.now(),
                ),
                content = "",
                createdAt = Date(),
                editedAt = Date(),
                emojis = emptyList(),
                favouritesCount = 0,
                repliesCount = 0,
                favourited = false,
                bookmarked = false,
                sensitive = false,
                spoilerText = "",
                attachments = emptyList(),
                mentions = emptyList(),
                tags = emptyList(),
                showingHiddenContent = false,
                expanded = false,
                collapsed = false,
                muted = false,
                poll = null,
                language = null,
            ),
        )
        conversationDao.upsert(conversation)

        // Check everything is as expected.
        assertThat(conversationDao.loadAllForAccount(pachliAccountId)).containsExactly(conversation)

        // When -- delete the account.
        accountDao.delete(activeAccount)

        // Then.
        @Suppress("DEPRECATION")
        assertThat(conversationDao.loadAllForAccount(pachliAccountId)).isEmpty()
    }

    @Test
    fun `deleting account deletes DraftEntity`() = runTest {
        val draft = DraftEntity(
            id = 1,
            accountId = pachliAccountId,
            inReplyToId = null,
            content = null,
            contentWarning = null,
            sensitive = false,
            visibility = Status.Visibility.PUBLIC,
            attachments = emptyList(),
            poll = null,
            failedToSend = false,
            failedToSendNew = false,
            scheduledAt = null,
            language = null,
            statusId = null,
        )
        draftDao.upsert(draft)

        // Check everything is as expected.
        assertThat(draftDao.loadDrafts(pachliAccountId)).containsExactly(draft)

        // When -- delete the account.
        accountDao.delete(activeAccount)

        // Then
        assertThat(draftDao.loadDrafts(pachliAccountId)).isEmpty()
    }

    @Test
    fun `deleting account deletes EmojisEntity`() = runTest {
        val emoji = EmojisEntity(
            accountId = pachliAccountId,
            emojiList = emptyList(),
        )
        instanceInfoDao.upsert(emoji)

        // Check everything is as expected.
        assertThat(instanceInfoDao.getEmojiInfo(pachliAccountId)).isEqualTo(emoji)

        // When -- delete the account.
        accountDao.delete(activeAccount)

        // Then
        assertThat(instanceInfoDao.getEmojiInfo(pachliAccountId)).isNull()
    }

    @Test
    fun `deleting account deletes FollowingAccountEntity`() = runTest {
        val followingAccount = FollowingAccountEntity(
            pachliAccountId = pachliAccountId,
            serverId = "2",
        )
        followingAccountDao.insert(followingAccount)

        // Check everything is as expected.
        assertThat(followingAccountDao.loadAllForAccount(pachliAccountId)).containsExactly(followingAccount)

        // When -- delete the account.
        accountDao.delete(activeAccount)

        // Then
        assertThat(followingAccountDao.loadAllForAccount(pachliAccountId)).isEmpty()
    }

    // InstanceInfoEntity
    //
    // InstanceInfoEntity is not checked, as it is not specified per-account,
    // and contains no account-specific information. If two or more Pachli accounts
    // are on the same server they share this information.

    // LogEntryEntity
    //
    // LogEntryEntity is not checked, as it is not specified per-account.

    @Test
    fun `deleting account deletes MastodonListEntity`() = runTest {
        val mastodonList = MastodonListEntity(
            accountId = pachliAccountId,
            listId = "1",
            title = "Test list",
            repliesPolicy = UserListRepliesPolicy.LIST,
            exclusive = false,
        )
        mastodonListDao.upsert(mastodonList)

        // Check everything is as expected.
        assertThat(mastodonListDao.get(pachliAccountId)).containsExactly(mastodonList)

        // When -- delete the account.
        accountDao.delete(activeAccount)

        // Then
        assertThat(mastodonListDao.get(pachliAccountId)).isEmpty()
    }

    @Suppress("DEPRECATION")
    @Test
    fun `deleting account deletes notification and viewdata`() = runTest {
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

        // When -- delete the account.
        accountDao.delete(activeAccount)

        // Then
        assertThat(notificationDao.loadAllForAccount(pachliAccountId)).isEmpty()
        assertThat(notificationDao.loadViewData(pachliAccountId, "1")).isNull()
    }

    @Test
    fun `deleting account deletes RemoteKeyEntity`() = runTest {
        // RemoteKeyEntity
        val remoteKey = RemoteKeyEntity(
            accountId = pachliAccountId,
            timelineId = "test",
            kind = RemoteKeyEntity.RemoteKeyKind.NEXT,
            key = "1",
        )
        remoteKeyDao.upsert(remoteKey)

        // Check everything is as expected.
        assertThat(remoteKeyDao.loadAllForAccount(pachliAccountId)).containsExactly(remoteKey)

        // When -- delete the account.
        accountDao.delete(activeAccount)

        // Then
        assertThat(remoteKeyDao.loadAllForAccount(pachliAccountId)).isEmpty()
    }

    @Test
    fun `deleting account deletes ServerEntity`() = runTest {
        val server = ServerEntity(
            accountId = pachliAccountId,
            serverKind = ServerKind.MASTODON,
            version = Version.parse("1.0.0"),
            capabilities = emptyMap(),
        )
        instanceInfoDao.upsert(server)

        // Check everything is as expected.
        assertThat(instanceInfoDao.getServer(pachliAccountId)).isEqualTo(server)

        // When -- delete the account.
        accountDao.delete(activeAccount)

        // Then
        assertThat(instanceInfoDao.getServer(pachliAccountId)).isNull()
    }

    @Test
    fun `deleting account deletes StatusViewDataEntity`() = runTest {
        val statusViewData = StatusViewDataEntity(
            serverId = "1",
            timelineUserId = pachliAccountId,
            expanded = false,
            contentShowing = false,
            contentCollapsed = false,
            translationState = TranslationState.SHOW_ORIGINAL,
        )
        statusDao.upsertStatusViewData(statusViewData)

        // Check everything is as expected.
        assertThat(statusDao.getStatusViewData(pachliAccountId, listOf("1"))).containsExactly("1", statusViewData)

        // When -- delete the account.
        accountDao.delete(activeAccount)

        // Then
        assertThat(statusDao.getStatusViewData(pachliAccountId, listOf("1"))).isEmpty()
    }

    @Test
    fun `deleting account deletes TimelineAccountEntity`() = runTest {
        val timelineAccount = TimelineAccountEntity(
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
        )
        timelineDao.insertAccount(timelineAccount)

        // Check everything is as expected.
        assertThat(timelineDao.loadTimelineAccountsForAccount(pachliAccountId)).containsExactly(timelineAccount)

        // When -- delete the account.
        accountDao.delete(activeAccount)

        // Then
        @Suppress("DEPRECATION")
        assertThat(timelineDao.loadTimelineAccountsForAccount(pachliAccountId)).isEmpty()
    }

    @Test
    fun `deleting account deletes TranslatedStatusEntity`() = runTest {
        val translatedStatus = TranslatedStatusEntity(
            serverId = "1",
            timelineUserId = pachliAccountId,
            content = "",
            spoilerText = "",
            poll = null,
            attachments = emptyList(),
            provider = "",
        )
        translatedStatusDao.upsert(translatedStatus)

        // Check everything is as expected.
        assertThat(translatedStatusDao.getTranslations(pachliAccountId, listOf("1"))).containsExactly("1", translatedStatus)

        // When -- delete the account.
        accountDao.delete(activeAccount)

        // Then
        translatedStatusDao.getTranslations(pachliAccountId, listOf("1")).isEmpty()
    }
}
