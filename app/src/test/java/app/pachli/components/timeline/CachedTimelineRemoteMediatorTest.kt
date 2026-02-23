package app.pachli.components.timeline

import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.pachli.components.timeline.viewmodel.CachedTimelineRemoteMediator
import app.pachli.core.common.PachliThrowable
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.database.AppDatabase
import app.pachli.core.database.dao.TimelineStatusWithAccount
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.RemoteKeyEntity.RemoteKeyKind
import app.pachli.core.database.model.TimelineStatusWithQuote
import app.pachli.core.model.Timeline
import app.pachli.core.network.di.test.DEFAULT_INSTANCE_V2
import app.pachli.core.network.model.AccountSource
import app.pachli.core.network.model.CredentialAccount
import app.pachli.core.network.model.Status
import app.pachli.core.network.model.nodeinfo.UnvalidatedJrd
import app.pachli.core.network.model.nodeinfo.UnvalidatedNodeInfo
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.NodeInfoApi
import app.pachli.core.network.retrofit.apiresult.ApiError
import app.pachli.core.network.retrofit.apiresult.ApiResponse
import app.pachli.core.network.retrofit.apiresult.ClientError
import app.pachli.core.network.retrofit.apiresult.ServerError
import app.pachli.core.testing.extensions.insertTimelineStatusWithQuote
import app.pachli.core.testing.failure
import app.pachli.core.testing.fakes.fakeStatus
import app.pachli.core.testing.fakes.fakeStatusEntityWithAccount
import app.pachli.core.testing.rules.MainCoroutineRule
import app.pachli.core.testing.success
import app.pachli.util.HiltTestApplication_Application
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.onSuccess
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doCallRealMethod
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.stub
import org.robolectric.annotation.Config
import retrofit2.HttpException

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@Config(application = HiltTestApplication_Application::class)
@RunWith(AndroidJUnit4::class)
class CachedTimelineRemoteMediatorTest {
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
    lateinit var db: AppDatabase

    @Inject
    lateinit var transactionProvider: TransactionProvider

    private lateinit var activeAccount: AccountEntity

    private val account = CredentialAccount(
        id = "1",
        localUsername = "username",
        username = "username@domain.example",
        displayName = "Display Name",
        createdAt = Instant.now(),
        note = "",
        url = "",
        avatar = "",
        header = "",
        source = AccountSource(),
    )

    private lateinit var pagingSourceFactory: InvalidatingPagingSourceFactory<Int, TimelineStatusWithAccount>

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() = runTest {
        hilt.inject()

        reset(mastodonApi)
        mastodonApi.stub {
            onBlocking { accountVerifyCredentials(anyOrNull(), anyOrNull()) } doReturn success(account)
            onBlocking { getInstanceV2(anyOrNull()) } doReturn success(DEFAULT_INSTANCE_V2)
            onBlocking { getLists() } doReturn success(emptyList())
            onBlocking { getCustomEmojis() } doReturn failure()
            onBlocking { getContentFilters() } doReturn success(emptyList())
            onBlocking { listAnnouncements(anyOrNull()) } doReturn success(emptyList())
            onBlocking { accountFollowing(any(), anyOrNull(), any()) } doReturn success(emptyList())
            onBlocking { resolveShallowQuotes(any<ApiResponse<List<Status>>>()) }.doCallRealMethod()
            onBlocking { resolveShallowQuotes(any<List<Status>>()) }.doCallRealMethod()
            onBlocking { resolveShallowQuotes(any<Status>()) }.doCallRealMethod()
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
            .andThen { accountManager.setActiveAccount(it) }
            .onSuccess {
                accountManager.refresh(it)
                activeAccount = it
            }

        pagingSourceFactory = mock()
    }

    @Test
    @ExperimentalPagingApi
    fun `should return ServerError Internal on HTTP 500`() {
        val remoteMediator = CachedTimelineRemoteMediator(
            context = context,
            mastodonApi = mock {
                onBlocking { homeTimeline(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) } doReturn failure(code = 500)
            },
            pachliAccountId = activeAccount.id,
            transactionProvider = transactionProvider,
            timelineDao = db.timelineDao(),
            remoteKeyDao = db.remoteKeyDao(),
            statusDao = db.statusDao(),
        )

        val result = runBlocking { remoteMediator.load(LoadType.REFRESH, state()) }

        assertTrue(result is RemoteMediator.MediatorResult.Error)
        assertTrue((result as RemoteMediator.MediatorResult.Error).throwable is PachliThrowable)

        val pachliError = (result.throwable as PachliThrowable).pachliError as ApiError
        assertTrue(pachliError is ServerError.Internal)

        assertEquals(500, (pachliError.throwable as HttpException).code())
    }

    @Test
    @ExperimentalPagingApi
    fun `should return error when network call fails`() {
        val remoteMediator = CachedTimelineRemoteMediator(
            context = context,
            mastodonApi = mock {
                onBlocking { homeTimeline(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) } doReturn failure()
            },
            pachliAccountId = activeAccount.id,
            transactionProvider = transactionProvider,
            timelineDao = db.timelineDao(),
            remoteKeyDao = db.remoteKeyDao(),
            statusDao = db.statusDao(),
        )

        val result = runBlocking { remoteMediator.load(LoadType.REFRESH, state()) }

        assertTrue(result is RemoteMediator.MediatorResult.Error)
        assertTrue((result as RemoteMediator.MediatorResult.Error).throwable is PachliThrowable)

        val pachliError = (result.throwable as PachliThrowable).pachliError as ApiError
        assertTrue(pachliError is ClientError.NotFound)
        assertEquals(404, (pachliError.throwable as HttpException).code())
    }

    @Test
    @ExperimentalPagingApi
    fun `should not prepend statuses`() {
        val remoteMediator = CachedTimelineRemoteMediator(
            context = context,
            mastodonApi = mock(),
            pachliAccountId = activeAccount.id,
            transactionProvider = transactionProvider,
            timelineDao = db.timelineDao(),
            remoteKeyDao = db.remoteKeyDao(),
            statusDao = db.statusDao(),
        )

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = listOf(
                        fakeStatusEntityWithAccount("3"),
                    ),
                    prevKey = null,
                    nextKey = 1,
                ),
            ),
        )

        val result = runBlocking { remoteMediator.load(LoadType.PREPEND, state) }

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
    }

    @Test
    @ExperimentalPagingApi
    fun `should not try to refresh already cached statuses when db is empty`() {
        mastodonApi.stub {
            onBlocking { homeTimeline(maxId = anyOrNull(), minId = anyOrNull(), sinceId = anyOrNull(), limit = any()) } doReturn success(
                listOf(
                    fakeStatus("5"),
                    fakeStatus("4"),
                    fakeStatus("3"),
                ),
            )
        }

        val remoteMediator = CachedTimelineRemoteMediator(
            context = context,
            mastodonApi = mastodonApi,
            pachliAccountId = activeAccount.id,
            transactionProvider = transactionProvider,
            timelineDao = db.timelineDao(),
            remoteKeyDao = db.remoteKeyDao(),
            statusDao = db.statusDao(),
        )

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = emptyList(),
                    prevKey = null,
                    nextKey = 0,
                ),
            ),
        )

        val result = runBlocking { remoteMediator.load(LoadType.REFRESH, state) }

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(true, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        db.assertStatuses(
            listOf(
                fakeStatusEntityWithAccount("5"),
                fakeStatusEntityWithAccount("4"),
                fakeStatusEntityWithAccount("3"),
            ),
        )
    }

    @Test
    @ExperimentalPagingApi
    fun `should remove deleted status from db and keep state of other cached statuses`() = runTest {
        // Given
        val statusesAlreadyInDb = listOf(
            fakeStatusEntityWithAccount("3", expanded = true),
            fakeStatusEntityWithAccount("2"),
            fakeStatusEntityWithAccount("1", expanded = false),
        )

        db.insertTimelineStatusWithQuote(statusesAlreadyInDb)

        mastodonApi.stub {
            onBlocking { homeTimeline(maxId = anyOrNull(), minId = anyOrNull(), sinceId = anyOrNull(), limit = any()) } doReturn success(
                listOf(
                    fakeStatus("3"),
                    fakeStatus("1"),
                ),
            )
        }

        val remoteMediator = CachedTimelineRemoteMediator(
            context = context,
            mastodonApi = mastodonApi,
            pachliAccountId = activeAccount.id,
            transactionProvider = transactionProvider,
            timelineDao = db.timelineDao(),
            remoteKeyDao = db.remoteKeyDao(),
            statusDao = db.statusDao(),
        )

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = statusesAlreadyInDb,
                    prevKey = null,
                    nextKey = 0,
                ),
            ),
        )

        // When
        val result = remoteMediator.load(LoadType.REFRESH, state)

        // Then
        assertThat(result).isInstanceOf(RemoteMediator.MediatorResult.Success::class.java)

        db.assertStatuses(
            listOf(
                // id="2" was in the database initially, but not in the results returned
                // from the API, so it should have been deleted here.
                fakeStatusEntityWithAccount("3", expanded = true),
                fakeStatusEntityWithAccount("1", expanded = false),
            ),
        )
    }

    @Test
    @ExperimentalPagingApi
    fun `should append statuses`() = runTest {
        val statusesAlreadyInDb = listOf(
            fakeStatusEntityWithAccount("8"),
            fakeStatusEntityWithAccount("7"),
            fakeStatusEntityWithAccount("5"),
        )

        db.insertTimelineStatusWithQuote(statusesAlreadyInDb)
        db.remoteKeyDao().upsert(RemoteKeyEntity(1, Timeline.Home.remoteKeyTimelineId, RemoteKeyKind.PREV, "8"))
        db.remoteKeyDao().upsert(RemoteKeyEntity(1, Timeline.Home.remoteKeyTimelineId, RemoteKeyKind.NEXT, "5"))

        mastodonApi.stub {
            onBlocking { homeTimeline(maxId = "5", limit = 20) } doReturn success(
                listOf(
                    fakeStatus("3"),
                    fakeStatus("2"),
                    fakeStatus("1"),
                ),
                headers = arrayOf("Link", "<http://example.com/?min_id=3>; rel=\"prev\", <http://example.com/?max_id=1>; rel=\"next\""),
            )
        }

        val remoteMediator = CachedTimelineRemoteMediator(
            context = context,
            mastodonApi = mastodonApi,
            pachliAccountId = activeAccount.id,
            transactionProvider = transactionProvider,
            timelineDao = db.timelineDao(),
            remoteKeyDao = db.remoteKeyDao(),
            statusDao = db.statusDao(),
        )

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = statusesAlreadyInDb,
                    prevKey = null,
                    nextKey = 0,
                ),
            ),
        )

        val result = remoteMediator.load(LoadType.APPEND, state)

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        db.assertStatuses(
            listOf(
                fakeStatusEntityWithAccount("8"),
                fakeStatusEntityWithAccount("7"),
                fakeStatusEntityWithAccount("5"),
                fakeStatusEntityWithAccount("3"),
                fakeStatusEntityWithAccount("2"),
                fakeStatusEntityWithAccount("1"),
            ),
        )
    }

    private fun state(
        pages: List<PagingSource.LoadResult.Page<Int, TimelineStatusWithQuote>> = emptyList(),
        pageSize: Int = 20,
    ) = PagingState(
        pages = pages,
        anchorPosition = null,
        config = PagingConfig(
            pageSize = pageSize,
        ),
        leadingPlaceholderCount = 0,
    )

    private fun AppDatabase.assertStatuses(
        expected: List<TimelineStatusWithQuote>,
        forAccount: Long = 1,
    ) {
        val pagingSource = timelineDao().getStatuses(forAccount)

        val loadResult = runBlocking {
            pagingSource.load(PagingSource.LoadParams.Refresh(null, 100, false))
        }

        val loadedStatuses = (loadResult as PagingSource.LoadResult.Page).data

        assertEquals(expected.size, loadedStatuses.size)

        for ((expected, actual) in expected.zip(loadedStatuses)) {
            assertEquals(expected.timelineStatus.status, actual.status)
            assertEquals(expected.timelineStatus.account, actual.account)
            assertEquals(expected.timelineStatus.reblogAccount, actual.reblogAccount)
        }
    }
}
