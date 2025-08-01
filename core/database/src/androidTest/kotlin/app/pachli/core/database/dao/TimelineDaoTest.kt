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

package app.pachli.core.database.dao

import androidx.paging.PagingSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pachli.core.database.AppDatabase
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.StatusEntity
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.database.model.TimelineStatusEntity
import app.pachli.core.database.model.TimelineStatusWithAccount
import app.pachli.core.model.Card
import app.pachli.core.model.Emoji
import app.pachli.core.model.PreviewCardKind
import app.pachli.core.model.Status
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TimelineDaoTest {
    @get:Rule(order = 0)
    var hilt = HiltAndroidRule(this)

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var accountDao: AccountDao

    @Inject
    lateinit var timelineDao: TimelineDao

    @Inject
    lateinit var statusDao: StatusDao

    @Before
    fun setup() {
        hilt.inject()

        runTest {
            // Insert two accounts with IDs 1 and 2, which are assumed to exist
            // by the tests.
            val activeAccount = AccountEntity(
                id = 1L,
                domain = "mastodon.example",
                accessToken = "token",
                clientId = "id",
                clientSecret = "secret",
                isActive = true,
            )

            accountDao.upsert(activeAccount)

            // Like activeAccount, but inactive, and the {id, domain} pair have to
            // be unique to match the database constraints.
            val inactiveAccount = activeAccount.copy(
                isActive = false,
                id = 2L,
                domain = "example.com",
            )

            accountDao.upsert(inactiveAccount)
        }
    }

    @Test
    fun insertGetStatus() = runTest {
        val setOne = makeStatus(statusId = 3)
        val setTwo = makeStatus(statusId = 20, reblog = true)
        val ignoredOne = makeStatus(statusId = 1)
        val ignoredTwo = makeStatus(accountId = 2)

        for ((status, author, reblogger) in listOf(setOne, setTwo, ignoredOne, ignoredTwo)) {
            timelineDao.insertAccount(author)
            reblogger?.let {
                timelineDao.insertAccount(it)
            }
            statusDao.insertStatus(status)
            timelineDao.upsertStatuses(
                listOf(
                    TimelineStatusEntity(
                        kind = TimelineStatusEntity.Kind.Home,
                        pachliAccountId = status.timelineUserId,
                        statusId = status.serverId,
                    ),
                ),
            )
        }

        val pagingSource = timelineDao.getStatuses(setOne.first.timelineUserId)

        val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh(null, 2, false))

        val loadedStatuses = (loadResult as PagingSource.LoadResult.Page).data

        assertEquals(2, loadedStatuses.size)
        assertStatuses(listOf(setTwo, setOne), loadedStatuses)
    }

    @Test
    fun cleanup() = runTest {
        val initialStatuses = listOf(
            makeStatus(statusId = 100),
            makeStatus(statusId = 10, authorServerId = "3"),
            makeStatus(statusId = 8, reblog = true, authorServerId = "10"),
            makeStatus(statusId = 5),
            makeStatus(statusId = 3, authorServerId = "4"),
            makeStatus(statusId = 2, accountId = 2, authorServerId = "5"),
            makeStatus(statusId = 1, authorServerId = "5"),
        )

        for ((status, author, reblogAuthor) in initialStatuses) {
            timelineDao.insertAccount(author)
            reblogAuthor?.let {
                timelineDao.insertAccount(it)
            }
            statusDao.insertStatus(status)
            timelineDao.upsertStatuses(
                listOf(
                    TimelineStatusEntity(
                        pachliAccountId = status.timelineUserId,
                        kind = TimelineStatusEntity.Kind.Home,
                        statusId = status.serverId,
                    ),
                ),
            )
        }

        // Remove some statuses from the home timeline for account 1L. This makes
        // them targets for the cleanup.
        arrayOf("5", "3", "1").forEach {
            timelineDao.delete(
                TimelineStatusEntity(
                    pachliAccountId = 1L,
                    kind = TimelineStatusEntity.Kind.Home,
                    statusId = it,
                ),
            )
        }

        timelineDao.cleanup(accountId = 1)

        val wantAccount1StatusesAfterCleanup = listOf(
            makeStatus(statusId = 100),
            makeStatus(statusId = 10, authorServerId = "3"),
            makeStatus(statusId = 8, reblog = true, authorServerId = "10"),
        )

        val wantAccount2StatusesAfterCleanup = listOf(
            makeStatus(statusId = 2, accountId = 2, authorServerId = "5"),
        )

        val loadParams: PagingSource.LoadParams<Int> = PagingSource.LoadParams.Refresh(null, 100, true)

        val gotAccount1StatusesAfterCleanup = (timelineDao.getStatuses(1).load(loadParams) as PagingSource.LoadResult.Page).data
        val gotAccount2StatusesAfterCleanup = (timelineDao.getStatuses(2).load(loadParams) as PagingSource.LoadResult.Page).data

        assertStatuses(wantAccount1StatusesAfterCleanup, gotAccount1StatusesAfterCleanup)
        assertStatuses(wantAccount2StatusesAfterCleanup, gotAccount2StatusesAfterCleanup)

        val loadedAccounts: MutableList<Pair<Long, String>> = mutableListOf()
        val accountCursor = db.query("SELECT timelineUserId, serverId FROM TimelineAccountEntity ORDER BY timelineUserId, serverId", null)
        accountCursor.moveToFirst()
        while (!accountCursor.isAfterLast) {
            val accountId: Long = accountCursor.getLong(accountCursor.getColumnIndex("timelineUserId"))
            val serverId: String = accountCursor.getString(accountCursor.getColumnIndex("serverId"))
            loadedAccounts.add(accountId to serverId)
            accountCursor.moveToNext()
        }
        accountCursor.close()

        val expectedAccounts = listOf(
            1L to "10",
            1L to "20",
            1L to "3",
            1L to "R10",
            2L to "5",
        )

        assertEquals(expectedAccounts, loadedAccounts)
    }

    @Test
    fun overwriteDeletedStatus() = runTest {
        val oldStatuses = listOf(
            makeStatus(statusId = 3),
            makeStatus(statusId = 2),
            makeStatus(statusId = 1),
        )

        for ((status, author, reblogAuthor) in oldStatuses) {
            timelineDao.insertAccount(author)
            reblogAuthor?.let {
                timelineDao.insertAccount(it)
            }
            statusDao.insertStatus(status)
        }

        // status 2 gets deleted, newly loaded status contain only 1 + 3
        val newStatuses = listOf(
            makeStatus(statusId = 3),
            makeStatus(statusId = 1),
        )

        val deletedCount = timelineDao.deleteRange(1, newStatuses.last().first.serverId, newStatuses.first().first.serverId)
        assertEquals(3, deletedCount)

        for ((status, author, reblogAuthor) in newStatuses) {
            timelineDao.insertAccount(author)
            reblogAuthor?.let {
                timelineDao.insertAccount(it)
            }
            statusDao.insertStatus(status)
        }

        // make sure status 2 is no longer in db

        val pagingSource = timelineDao.getStatuses(1)

        val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh(null, 100, false))

        val loadedStatuses = (loadResult as PagingSource.LoadResult.Page).data

        assertStatuses(newStatuses, loadedStatuses)
    }

    @Test
    fun deleteRange() = runTest {
        val statuses = listOf(
            makeStatus(statusId = 100),
            makeStatus(statusId = 50),
            makeStatus(statusId = 15),
            makeStatus(statusId = 14),
            makeStatus(statusId = 13),
            makeStatus(statusId = 13, accountId = 2),
            makeStatus(statusId = 12),
            makeStatus(statusId = 11),
            makeStatus(statusId = 9),
        )

        for ((status, author, reblogAuthor) in statuses) {
            timelineDao.insertAccount(author)
            reblogAuthor?.let {
                timelineDao.insertAccount(it)
            }
            statusDao.insertStatus(status)
        }

        assertEquals(3, timelineDao.deleteRange(1, "12", "14"))
        assertEquals(0, timelineDao.deleteRange(1, "80", "80"))
        assertEquals(0, timelineDao.deleteRange(1, "60", "80"))
        assertEquals(0, timelineDao.deleteRange(1, "5", "8"))
        assertEquals(0, timelineDao.deleteRange(1, "101", "1000"))
        assertEquals(1, timelineDao.deleteRange(1, "50", "50"))

        val loadParams: PagingSource.LoadParams<Int> = PagingSource.LoadParams.Refresh(null, 100, false)

        val statusesAccount1 = (timelineDao.getStatuses(1).load(loadParams) as PagingSource.LoadResult.Page).data
        val statusesAccount2 = (timelineDao.getStatuses(2).load(loadParams) as PagingSource.LoadResult.Page).data

        val remainingStatusesAccount1 = listOf(
            makeStatus(statusId = 100),
            makeStatus(statusId = 15),
            makeStatus(statusId = 11),
            makeStatus(statusId = 9),
        )

        val remainingStatusesAccount2 = listOf(
            makeStatus(statusId = 13, accountId = 2),
        )

        assertStatuses(remainingStatusesAccount1, statusesAccount1)
        assertStatuses(remainingStatusesAccount2, statusesAccount2)
    }

    @Test
    fun deleteAllForInstance() = runTest {
        val statusWithRedDomain1 = makeStatus(
            statusId = 15,
            accountId = 1,
            domain = "mastodon.red",
            authorServerId = "1",
        )
        val statusWithRedDomain2 = makeStatus(
            statusId = 14,
            accountId = 1,
            domain = "mastodon.red",
            authorServerId = "2",
        )
        val statusWithRedDomainOtherAccount = makeStatus(
            statusId = 12,
            accountId = 2,
            domain = "mastodon.red",
            authorServerId = "2",
        )
        val statusWithBlueDomain = makeStatus(
            statusId = 10,
            accountId = 1,
            domain = "mastodon.blue",
            authorServerId = "4",
        )
        val statusWithBlueDomainOtherAccount = makeStatus(
            statusId = 10,
            accountId = 2,
            domain = "mastodon.blue",
            authorServerId = "5",
        )
        val statusWithGreenDomain = makeStatus(
            statusId = 8,
            accountId = 1,
            domain = "mastodon.green",
            authorServerId = "6",
        )

        for ((status, author, reblogAuthor) in listOf(statusWithRedDomain1, statusWithRedDomain2, statusWithRedDomainOtherAccount, statusWithBlueDomain, statusWithBlueDomainOtherAccount, statusWithGreenDomain)) {
            timelineDao.insertAccount(author)
            reblogAuthor?.let {
                timelineDao.insertAccount(it)
            }
            statusDao.insertStatus(status)
        }

        timelineDao.deleteAllFromInstance(1, "mastodon.red")
        timelineDao.deleteAllFromInstance(1, "mastodon.blu") // shouldn't delete anything
        timelineDao.deleteAllFromInstance(1, "greenmastodon.green") // shouldn't delete anything

        val loadParams: PagingSource.LoadParams<Int> = PagingSource.LoadParams.Refresh(null, 100, false)

        val statusesAccount1 = (timelineDao.getStatuses(1).load(loadParams) as PagingSource.LoadResult.Page).data
        val statusesAccount2 = (timelineDao.getStatuses(2).load(loadParams) as PagingSource.LoadResult.Page).data

        assertStatuses(listOf(statusWithBlueDomain, statusWithGreenDomain), statusesAccount1)
        assertStatuses(listOf(statusWithRedDomainOtherAccount, statusWithBlueDomainOtherAccount), statusesAccount2)
    }

    @Test
    fun previewCardSurvivesRoundtrip() = runTest {
        val setOne = makeStatus(statusId = 3, cardUrl = "https://foo.bar")

        for ((status, author, reblogger) in listOf(setOne)) {
            timelineDao.insertAccount(author)
            reblogger?.let {
                timelineDao.insertAccount(it)
            }
            statusDao.insertStatus(status)
            timelineDao.upsertStatuses(
                listOf(
                    TimelineStatusEntity(
                        pachliAccountId = status.timelineUserId,
                        kind = TimelineStatusEntity.Kind.Home,
                        statusId = status.serverId,
                    ),
                ),
            )
        }

        val pagingSource = timelineDao.getStatuses(setOne.first.timelineUserId)

        val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh(null, 2, false))

        val loadedStatuses = (loadResult as PagingSource.LoadResult.Page).data

        assertEquals(1, loadedStatuses.size)
        assertStatuses(listOf(setOne), loadedStatuses)
    }

    private fun makeStatus(
        accountId: Long = 1,
        statusId: Long = 10,
        reblog: Boolean = false,
        createdAt: Long = statusId,
        authorServerId: String = "20",
        domain: String = "mastodon.example",
        cardUrl: String? = null,
    ): Triple<StatusEntity, TimelineAccountEntity, TimelineAccountEntity?> {
        val author = TimelineAccountEntity(
            serverId = authorServerId,
            timelineUserId = accountId,
            localUsername = "localUsername@$domain",
            username = "username@$domain",
            displayName = "displayName",
            url = "blah",
            avatar = "avatar",
            emojis = listOf(Emoji("pachli", "http://pachli.cool/emoji.jpg", "", null)),
            bot = false,
            createdAt = null,
            note = "",
            roles = null,
        )

        val reblogAuthor = if (reblog) {
            TimelineAccountEntity(
                serverId = "R$authorServerId",
                timelineUserId = accountId,
                localUsername = "RlocalUsername",
                username = "Rusername",
                displayName = "RdisplayName",
                url = "Rblah",
                avatar = "Ravatar",
                emojis = emptyList(),
                bot = false,
                createdAt = null,
                note = "",
                roles = null,
            )
        } else {
            null
        }

        val card = when (cardUrl) {
            null -> null
            else -> Card(cardUrl, "", "", PreviewCardKind.LINK, providerName = "", providerUrl = "")
        }
        val even = accountId % 2 == 0L
        val status = StatusEntity(
            serverId = statusId.toString(),
            url = "https://$domain/whatever/$statusId",
            timelineUserId = accountId,
            authorServerId = authorServerId,
            inReplyToId = "inReplyToId$statusId",
            inReplyToAccountId = "inReplyToAccountId$statusId",
            content = "Content!$statusId",
            createdAt = createdAt,
            editedAt = null,
            emojis = emptyList(),
            reblogsCount = 1 * statusId.toInt(),
            favouritesCount = 2 * statusId.toInt(),
            repliesCount = 3 * statusId.toInt(),
            reblogged = even,
            favourited = !even,
            bookmarked = false,
            sensitive = even,
            spoilerText = "spoiler$statusId",
            visibility = Status.Visibility.PRIVATE,
            attachments = null,
            mentions = null,
            tags = null,
            application = null,
            reblogServerId = if (reblog) (statusId * 100).toString() else null,
            reblogAccountId = reblogAuthor?.serverId,
            poll = null,
            muted = false,
            pinned = false,
            card = card,
            language = null,
            filtered = null,
        )
        return Triple(status, author, reblogAuthor)
    }

    private fun assertStatuses(
        expected: List<Triple<StatusEntity, TimelineAccountEntity, TimelineAccountEntity?>>,
        provided: List<TimelineStatusWithAccount>,
    ) {
        for ((exp, prov) in expected.zip(provided)) {
            val (status, author, reblogger) = exp
            assertEquals(status, prov.status)
            assertEquals(author, prov.account)
            assertEquals(reblogger, prov.reblogAccount)
        }
    }
}
