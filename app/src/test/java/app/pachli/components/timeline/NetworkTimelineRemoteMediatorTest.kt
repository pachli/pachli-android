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

import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pachli.components.timeline.viewmodel.NetworkTimelineRemoteMediator
import app.pachli.components.timeline.viewmodel.Page
import app.pachli.components.timeline.viewmodel.PageCache
import app.pachli.components.timeline.viewmodel.asTimelineStatusWithQuote
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.OfflineFirstStatusRepository
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.TimelineStatusWithQuote
import app.pachli.core.model.Timeline
import app.pachli.core.network.di.test.DEFAULT_INSTANCE_V2
import app.pachli.core.network.model.AccountSource
import app.pachli.core.network.model.CredentialAccount
import app.pachli.core.network.model.asModel
import app.pachli.core.network.model.nodeinfo.UnvalidatedJrd
import app.pachli.core.network.model.nodeinfo.UnvalidatedNodeInfo
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.NodeInfoApi
import app.pachli.core.testing.failure
import app.pachli.core.testing.fakes.fakeStatus
import app.pachli.core.testing.rules.MainCoroutineRule
import app.pachli.core.testing.success
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.onSuccess
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config
import retrofit2.HttpException

@HiltAndroidTest
@Config(application = HiltTestApplication_Application::class)
@RunWith(AndroidJUnit4::class)
class NetworkTimelineRemoteMediatorTest {
    @get:Rule(order = 0)
    var hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val mainCoroutineRule = MainCoroutineRule()

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var nodeInfoApi: NodeInfoApi

    @Inject
    lateinit var remoteKeyDao: RemoteKeyDao

    @Inject
    lateinit var statusRepository: OfflineFirstStatusRepository

    val account = CredentialAccount(
        id = "1",
        localUsername = "username",
        username = "username@mastodon.example",
        displayName = "Display Name",
        createdAt = Instant.now(),
        note = "",
        url = "",
        avatar = "",
        header = "",
        source = AccountSource(),
    )

    private lateinit var activeAccount: AccountEntity

    private lateinit var pagingSourceFactory: InvalidatingPagingSourceFactory<String, TimelineStatusWithQuote>

    @Before
    fun setup() = runTest {
        hilt.inject()

        reset(mastodonApi)
        mastodonApi.stub {
            onBlocking { accountVerifyCredentials(anyOrNull(), anyOrNull()) } doReturn success(account)
            onBlocking { getInstanceV2() } doReturn success(DEFAULT_INSTANCE_V2)
            onBlocking { getLists() } doReturn success(emptyList())
            onBlocking { getCustomEmojis() } doReturn failure()
            onBlocking { getContentFilters() } doReturn success(emptyList())
            onBlocking { listAnnouncements(any()) } doReturn success(emptyList())
            onBlocking { getContentFiltersV1() } doReturn success(emptyList())
            onBlocking { accountFollowing(any(), anyOrNull(), any()) } doReturn success(emptyList())
        }

        reset(nodeInfoApi)
        nodeInfoApi.stub {
            onBlocking { nodeInfoJrd() } doReturn success(
                UnvalidatedJrd(
                    listOf(
                        UnvalidatedJrd.Link(
                            "http://nodeinfo.diaspora.software/ns/schema/2.1",
                            "https://example.com",
                        ),
                    ),
                ),
            )
            onBlocking { nodeInfo(any()) } doReturn success(
                UnvalidatedNodeInfo(UnvalidatedNodeInfo.Software("mastodon", "4.2.0")),
            )
        }

        accountManager.verifyAndAddAccount(
            accessToken = "token",
            domain = "domain.example",
            clientId = "id",
            clientSecret = "secret",
            oauthScopes = "scopes",
        )
            .andThen {
                accountManager.setActiveAccount(it).onSuccess {
                    accountManager.refresh(it)
                    activeAccount = it
                }
            }

        pagingSourceFactory = mock()
    }

    @Test
    @ExperimentalPagingApi
    fun `should return error when network call returns error code`() = runTest {
        // Given
        val remoteMediator = NetworkTimelineRemoteMediator(
            api = mock(defaultAnswer = { failure<Unit>(code = 500) }),
            pachliAccountId = activeAccount.id,
            factory = pagingSourceFactory,
            pageCache = PageCache(),
            timeline = Timeline.Home,
            remoteKeyDao = remoteKeyDao,
        )

        // When
        val result = remoteMediator.load(LoadType.REFRESH, state())

        // Then
        assertThat(result).isInstanceOf(RemoteMediator.MediatorResult.Error::class.java)
        assertThat((result as RemoteMediator.MediatorResult.Error).throwable).isInstanceOf(HttpException::class.java)
        assertThat((result.throwable as HttpException).code()).isEqualTo(500)
    }

    @Test
    @ExperimentalPagingApi
    fun `should do initial loading`() = runTest {
        // Given
        val pages = PageCache()
        val remoteMediator = NetworkTimelineRemoteMediator(
            mastodonApi.stub {
                onBlocking { homeTimeline(maxId = anyOrNull(), minId = anyOrNull(), limit = anyOrNull(), sinceId = anyOrNull()) } doReturn success(
                    listOf(fakeStatus("7"), fakeStatus("6"), fakeStatus("5")),
                    headers = arrayOf(
                        "Link",
                        "<https://mastodon.example/api/v1/timelines/home?max_id=5>; rel=\"next\", <https://mastodon.example/api/v1/timelines/homefavourites?min_id=7>; rel=\"prev\"",
                    ),
                )
            },
            pachliAccountId = activeAccount.id,
            factory = pagingSourceFactory,
            pageCache = pages,
            timeline = Timeline.Home,
            remoteKeyDao = remoteKeyDao,
        )

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = emptyList(),
                    prevKey = null,
                    nextKey = null,
                ),
            ),
        )

        // When
        val result = remoteMediator.load(LoadType.REFRESH, state)

        // Then
        val expectedPages = PageCache().apply {
            withLock {
                add(
                    Page(
                        data = listOf(fakeStatus("7"), fakeStatus("6"), fakeStatus("5")).asModel().toMutableList(),
                        prevKey = "7",
                        nextKey = "5",
                    ),
                )
            }
        }

        assertThat(result).isInstanceOf(RemoteMediator.MediatorResult.Success::class.java)
        assertThat((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached).isFalse()
        assertThat(pages.idToPage).containsExactlyEntriesIn(expectedPages.idToPage)

        // Page cache was modified, so the pager should have been invalidated
        verify(pagingSourceFactory).invalidate()
    }

    @Test
    @ExperimentalPagingApi
    fun `should prepend statuses`() = runTest {
        // Given
        val pages = PageCache().apply {
            withLock {
                add(
                    Page(
                        data = listOf(fakeStatus("7"), fakeStatus("6"), fakeStatus("5")).asModel().toMutableList(),
                        prevKey = "7",
                        nextKey = "5",
                    ),
                )
            }
        }

        val remoteMediator = NetworkTimelineRemoteMediator(
            mastodonApi.stub {
                onBlocking { homeTimeline(maxId = anyOrNull(), minId = anyOrNull(), limit = anyOrNull(), sinceId = anyOrNull()) } doReturn success(
                    listOf(fakeStatus("10"), fakeStatus("9"), fakeStatus("8")),
                    headers = arrayOf(
                        "Link",
                        "<https://mastodon.example/api/v1/timelines/home?max_id=8>; rel=\"next\", <https://mastodon.example/api/v1/timelines/homefavourites?min_id=10>; rel=\"prev\"",
                    ),
                )
            },
            pachliAccountId = activeAccount.id,
            factory = pagingSourceFactory,
            pageCache = pages,
            timeline = Timeline.Home,
            remoteKeyDao = remoteKeyDao,
        )

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = listOf(fakeStatus("7"), fakeStatus("6"), fakeStatus("5")).asModel()
                        .asTimelineStatusWithQuote(activeAccount.id, statusRepository),
                    prevKey = "7",
                    nextKey = "5",
                ),
            ),
        )

        // When
        val result = remoteMediator.load(LoadType.PREPEND, state)

        // Then
        val expectedPages = PageCache().apply {
            withLock {
                add(
                    Page(
                        data = listOf(fakeStatus("7"), fakeStatus("6"), fakeStatus("5")).asModel().toMutableList(),
                        prevKey = "7",
                        nextKey = "5",
                    ),
                )
                prepend(
                    Page(
                        data = listOf(fakeStatus("10"), fakeStatus("9"), fakeStatus("8")).asModel().toMutableList(),
                        prevKey = "10",
                        nextKey = "8",
                    ),
                )
            }
        }

        assertThat(result).isInstanceOf(RemoteMediator.MediatorResult.Success::class.java)
        assertThat((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached).isFalse()
        assertThat(pages.idToPage).containsExactlyEntriesIn(expectedPages.idToPage)

        // Page cache was modified, so the pager should have been invalidated
        verify(pagingSourceFactory).invalidate()
    }

    @Test
    @ExperimentalPagingApi
    fun `should append statuses`() = runTest {
        // Given
        val pages = PageCache().apply {
            withLock {
                add(
                    Page(
                        data = listOf(fakeStatus("7"), fakeStatus("6"), fakeStatus("5")).asModel().toMutableList(),
                        prevKey = "7",
                        nextKey = "5",
                    ),
                )
            }
        }

        val remoteMediator = NetworkTimelineRemoteMediator(
            mastodonApi.stub {
                onBlocking { homeTimeline(maxId = anyOrNull(), minId = anyOrNull(), limit = anyOrNull(), sinceId = anyOrNull()) } doReturn success(
                    listOf(fakeStatus("4"), fakeStatus("3"), fakeStatus("2")),
                    headers = arrayOf(
                        "Link",
                        "<https://mastodon.example/api/v1/timelines/home?max_id=2>; rel=\"next\", <https://mastodon.example/api/v1/timelines/homefavourites?min_id=4>; rel=\"prev\"",
                    ),
                )
            },
            pachliAccountId = activeAccount.id,
            factory = pagingSourceFactory,
            pageCache = pages,
            timeline = Timeline.Home,
            remoteKeyDao = remoteKeyDao,
        )

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = listOf(fakeStatus("7"), fakeStatus("6"), fakeStatus("5")).asModel().asTimelineStatusWithQuote(activeAccount.id, statusRepository).toMutableList(),
                    prevKey = "7",
                    nextKey = "5",
                ),
            ),
        )

        // When
        val result = remoteMediator.load(LoadType.APPEND, state)

        // Then
        val expectedPages = PageCache().apply {
            withLock {
                add(
                    Page(
                        data = listOf(fakeStatus("7"), fakeStatus("6"), fakeStatus("5")).asModel().toMutableList(),
                        prevKey = "7",
                        nextKey = "5",
                    ),
                )
                append(
                    Page(
                        data = listOf(fakeStatus("4"), fakeStatus("3"), fakeStatus("2")).asModel().toMutableList(),
                        prevKey = "4",
                        nextKey = "2",
                    ),
                )
            }
        }

        assertThat(result).isInstanceOf(RemoteMediator.MediatorResult.Success::class.java)
        assertThat((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached).isFalse()
        assertThat(pages.idToPage).containsExactlyEntriesIn(expectedPages.idToPage)

        // Page cache was modified, so the pager should have been invalidated
        verify(pagingSourceFactory).invalidate()
    }

    companion object {
        private const val PAGE_SIZE = 20

        private fun state(pages: List<PagingSource.LoadResult.Page<String, TimelineStatusWithQuote>> = emptyList()) = PagingState(
            pages = pages,
            anchorPosition = null,
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = PAGE_SIZE,
            ),
            leadingPlaceholderCount = 0,
        )
    }
}
