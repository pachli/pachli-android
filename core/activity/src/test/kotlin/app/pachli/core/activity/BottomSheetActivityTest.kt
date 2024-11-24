/*
 * Copyright 2018 Levi Bard
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

package app.pachli.core.activity

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.pachli.core.network.model.SearchResult
import app.pachli.core.network.model.Status
import app.pachli.core.network.model.TimelineAccount
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.testing.rules.MainCoroutineRule
import at.connyduck.calladapter.networkresult.NetworkResult
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.eq
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class BottomSheetActivityTest {
    @get:Rule
    val instantTaskExecutorRule: InstantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(dispatcher = StandardTestDispatcher())

    private lateinit var activity: FakeBottomSheetActivity
    private lateinit var apiMock: MastodonApi
    private val accountQuery = "http://mastodon.foo.bar/@User"
    private val statusQuery = "http://mastodon.foo.bar/@User/345678"
    private val nonexistentStatusQuery = "http://mastodon.foo.bar/@User/345678000"
    private val nonMastodonQuery = "http://medium.com/@correspondent/345678"
    private val emptyResponse = NetworkResult.success(
        SearchResult(emptyList(), emptyList(), emptyList()),
    )

    private val pachliAccountId = 0L

    private val account = TimelineAccount(
        id = "1",
        localUsername = "admin",
        username = "admin",
        displayName = "Ad Min",
        note = "This is their bio",
        url = "http://mastodon.foo.bar/@User",
        avatar = "",
        createdAt = Instant.now(),
    )
    private val accountResponse = NetworkResult.success(
        SearchResult(listOf(account), emptyList(), emptyList()),
    )

    private val status = Status(
        id = "1",
        url = statusQuery,
        account = account,
        inReplyToId = null,
        inReplyToAccountId = null,
        reblog = null,
        content = "omgwat",
        createdAt = Date(),
        editedAt = null,
        emojis = emptyList(),
        reblogsCount = 0,
        favouritesCount = 0,
        repliesCount = 0,
        reblogged = false,
        favourited = false,
        bookmarked = false,
        sensitive = false,
        spoilerText = "",
        visibility = Status.Visibility.PUBLIC,
        attachments = ArrayList(),
        mentions = emptyList(),
        tags = emptyList(),
        application = null,
        pinned = false,
        muted = false,
        poll = null,
        card = null,
        language = null,
        filtered = null,
    )
    private val statusResponse = NetworkResult.success(
        SearchResult(emptyList(), listOf(status), emptyList()),
    )

    @Before
    fun setup() {
        apiMock = mock {
            onBlocking { search(eq(accountQuery), eq(null), anyBoolean(), eq(null), eq(null), eq(null)) } doReturn accountResponse
            onBlocking { search(eq(statusQuery), eq(null), anyBoolean(), eq(null), eq(null), eq(null)) } doReturn statusResponse
            onBlocking { search(eq(nonexistentStatusQuery), eq(null), anyBoolean(), eq(null), eq(null), eq(null)) } doReturn accountResponse
            onBlocking { search(eq(nonMastodonQuery), eq(null), anyBoolean(), eq(null), eq(null), eq(null)) } doReturn emptyResponse
        }

        activity = FakeBottomSheetActivity(apiMock)
    }

    @Test
    fun beginEndSearch_setIsSearching_isSearchingAfterBegin() = runTest {
        activity.onBeginSearch("https://mastodon.foo.bar/@User")
        assertTrue(activity.isSearching())
    }

    @Test
    fun beginEndSearch_setIsSearching_isNotSearchingAfterEnd() = runTest {
        val validUrl = "https://mastodon.foo.bar/@User"
        activity.onBeginSearch(validUrl)
        activity.onEndSearch(validUrl)
        assertFalse(activity.isSearching())
    }

    @Test
    fun beginEndSearch_setIsSearching_doesNotCancelSearchWhenResponseFromPreviousSearchIsReceived() = runTest {
        val validUrl = "https://mastodon.foo.bar/@User"
        val invalidUrl = ""

        activity.onBeginSearch(validUrl)
        activity.onEndSearch(invalidUrl)
        assertTrue(activity.isSearching())
    }

    @Test
    fun cancelActiveSearch() = runTest {
        val url = "https://mastodon.foo.bar/@User"

        activity.onBeginSearch(url)
        activity.cancelActiveSearch()
        assertFalse(activity.isSearching())
    }

    @Test
    fun getCancelSearchRequested_detectsURL() = runTest {
        val firstUrl = "https://mastodon.foo.bar/@User"
        val secondUrl = "https://mastodon.foo.bar/@meh"

        activity.onBeginSearch(firstUrl)
        activity.cancelActiveSearch()

        activity.onBeginSearch(secondUrl)
        assertTrue(activity.getCancelSearchRequested(firstUrl))
        assertFalse(activity.getCancelSearchRequested(secondUrl))
    }

    @Test
    fun search_inIdealConditions_returnsRequestedResults_forAccount() = runTest {
        activity.viewUrl(pachliAccountId, accountQuery)
        advanceUntilIdle()
        assertEquals(pachliAccountId, activity.pachliAccountId)
        assertEquals(account.id, activity.accountId)
    }

    @Test
    fun search_inIdealConditions_returnsRequestedResults_forStatus() = runTest {
        activity.viewUrl(pachliAccountId, statusQuery)
        advanceUntilIdle()
        assertEquals(pachliAccountId, activity.pachliAccountId)
        assertEquals(status.id, activity.statusId)
    }

    @Test
    fun search_inIdealConditions_returnsRequestedResults_forNonMastodonURL() = runTest {
        activity.viewUrl(pachliAccountId, nonMastodonQuery)
        advanceUntilIdle()
        assertEquals(nonMastodonQuery, activity.link)
    }

    @Test
    fun search_withNoResults_appliesRequestedFallbackBehavior() = runTest {
        for (fallbackBehavior in listOf(PostLookupFallbackBehavior.OPEN_IN_BROWSER, PostLookupFallbackBehavior.DISPLAY_ERROR)) {
            activity.viewUrl(pachliAccountId, nonMastodonQuery, fallbackBehavior)
            advanceUntilIdle()
            assertEquals(nonMastodonQuery, activity.link)
            assertEquals(fallbackBehavior, activity.fallbackBehavior)
        }
    }

    @Test
    fun search_doesNotRespectUnrelatedResult() = runTest {
        activity.viewUrl(pachliAccountId, nonexistentStatusQuery)
        advanceUntilIdle()
        assertEquals(null, activity.accountId)
    }

    @Test
    fun search_withCancellation_doesNotLoadUrl_forAccount() = runTest {
        activity.viewUrl(pachliAccountId, accountQuery)
        assertTrue(activity.isSearching())
        activity.cancelActiveSearch()
        assertFalse(activity.isSearching())
        assertEquals(null, activity.accountId)
    }

    @Test
    fun search_withCancellation_doesNotLoadUrl_forStatus() = runTest {
        activity.viewUrl(pachliAccountId, accountQuery)
        activity.cancelActiveSearch()
        assertEquals(null, activity.accountId)
    }

    @Test
    fun search_withCancellation_doesNotLoadUrl_forNonMastodonURL() = runTest {
        activity.viewUrl(pachliAccountId, nonMastodonQuery)
        activity.cancelActiveSearch()
        assertEquals(null, activity.searchUrl)
    }

    @Test
    fun search_withPreviousCancellation_completes() = runTest {
        // begin/cancel account search
        activity.viewUrl(pachliAccountId, accountQuery)
        activity.cancelActiveSearch()

        // begin status search
        activity.viewUrl(pachliAccountId, statusQuery)

        // ensure that search is still ongoing
        assertTrue(activity.isSearching())

        // return searchResults
        advanceUntilIdle()

        // ensure that the result of the status search was recorded
        // and the account search wasn't
        assertEquals(pachliAccountId, activity.pachliAccountId)
        assertEquals(status.id, activity.statusId)
        assertEquals(null, activity.accountId)
    }

    class FakeBottomSheetActivity(api: MastodonApi) : BottomSheetActivity() {

        var pachliAccountId: Long? = null
        var statusId: String? = null
        var accountId: String? = null
        var link: String? = null
        var fallbackBehavior: PostLookupFallbackBehavior? = null

        init {
            mastodonApi = api
            bottomSheet = mock()
        }

        override fun openLink(url: String) {
            this.link = url
        }

        override fun viewAccount(pachliAccountId: Long, id: String) {
            this.pachliAccountId = pachliAccountId
            this.accountId = id
        }

        override fun viewThread(pachliAccountId: Long, statusId: String, url: String?) {
            this.pachliAccountId = pachliAccountId
            this.statusId = statusId
        }

        override fun performUrlFallbackAction(url: String, fallbackBehavior: PostLookupFallbackBehavior) {
            this.link = url
            this.fallbackBehavior = fallbackBehavior
        }
    }
}
