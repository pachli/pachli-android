package app.pachli.components.timeline

import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.pachli.components.timeline.viewmodel.CachedTimelineRemoteMediator
import app.pachli.core.common.PachliThrowable
import app.pachli.core.database.AppDatabase
import app.pachli.core.database.Converters
import app.pachli.core.database.dao.TimelineStatusWithAccount
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.RemoteKeyEntity.RemoteKeyKind
import app.pachli.core.database.model.TimelineStatusWithQuote
import app.pachli.core.model.Timeline
import app.pachli.core.model.VersionAdapter
import app.pachli.core.network.json.BooleanIfNull
import app.pachli.core.network.json.DefaultIfNull
import app.pachli.core.network.json.Guarded
import app.pachli.core.network.json.InstantJsonAdapter
import app.pachli.core.network.json.LenientRfc3339DateJsonAdapter
import app.pachli.core.network.json.UriAdapter
import app.pachli.core.network.retrofit.apiresult.ApiError
import app.pachli.core.network.retrofit.apiresult.ClientError
import app.pachli.core.network.retrofit.apiresult.ServerError
import app.pachli.core.testing.extensions.insertTimelineStatusWithQuote
import app.pachli.core.testing.failure
import app.pachli.core.testing.fakes.fakeStatus
import app.pachli.core.testing.fakes.fakeStatusEntityWithAccount
import app.pachli.core.testing.success
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import retrofit2.HttpException

@RunWith(AndroidJUnit4::class)
class CachedTimelineRemoteMediatorTest {

    private val activeAccount = AccountEntity(
        id = 1,
        domain = "mastodon.example",
        accessToken = "token",
        clientId = "id",
        clientSecret = "secret",
        isActive = true,
    )

    private lateinit var db: AppDatabase
    private lateinit var transactionProvider: TransactionProvider

    private lateinit var pagingSourceFactory: InvalidatingPagingSourceFactory<Int, TimelineStatusWithAccount>

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val moshi: Moshi = Moshi.Builder()
        .add(Date::class.java, LenientRfc3339DateJsonAdapter())
        .add(Instant::class.java, InstantJsonAdapter())
        .add(UriAdapter())
        .add(VersionAdapter())
        .add(Guarded.Factory())
        .add(DefaultIfNull.Factory())
        .add(BooleanIfNull.Factory())
        .build()

    @Before
    @ExperimentalCoroutinesApi
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addTypeConverter(Converters(moshi))
            .build()
        transactionProvider = TransactionProvider(db)

        runTest { db.accountDao().upsert(activeAccount) }

        pagingSourceFactory = mock()
    }

    @Test
    @ExperimentalPagingApi
    fun `should return ServerError Internal on HTTP 500`() {
        val remoteMediator = CachedTimelineRemoteMediator(
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
        val remoteMediator = CachedTimelineRemoteMediator(
            mastodonApi = mock {
                onBlocking { homeTimeline(maxId = anyOrNull(), minId = anyOrNull(), sinceId = anyOrNull(), limit = any()) } doReturn success(
                    listOf(
                        fakeStatus("5"),
                        fakeStatus("4"),
                        fakeStatus("3"),
                    ),
                )
            },
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

        val remoteMediator = CachedTimelineRemoteMediator(
            mastodonApi = mock {
                onBlocking { homeTimeline(maxId = anyOrNull(), minId = anyOrNull(), sinceId = anyOrNull(), limit = any()) } doReturn success(
                    listOf(
                        fakeStatus("3"),
                        fakeStatus("1"),
                    ),
                )
            },
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

        val remoteMediator = CachedTimelineRemoteMediator(
            mastodonApi = mock {
                onBlocking { homeTimeline(maxId = "5", limit = 20) } doReturn success(
                    listOf(
                        fakeStatus("3"),
                        fakeStatus("2"),
                        fakeStatus("1"),
                    ),
                    headers = arrayOf("Link", "<http://example.com/?min_id=3>; rel=\"prev\", <http://example.com/?max_id=1>; rel=\"next\""),

                )
            },
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

        val result = runBlocking { remoteMediator.load(LoadType.APPEND, state) }

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
