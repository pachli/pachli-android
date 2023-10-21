package app.pachli.components.viewthread

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pachli.PachliApplication
import app.pachli.appstore.BookmarkEvent
import app.pachli.appstore.EventHub
import app.pachli.appstore.FavoriteEvent
import app.pachli.appstore.ReblogEvent
import app.pachli.components.compose.HiltTestApplication_Application
import app.pachli.components.timeline.CachedTimelineRepository
import app.pachli.components.timeline.FilterKind
import app.pachli.components.timeline.FiltersRepository
import app.pachli.components.timeline.mockStatus
import app.pachli.components.timeline.mockStatusViewData
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.database.AccountManager
import app.pachli.entity.Account
import app.pachli.entity.StatusContext
import app.pachli.network.FilterModel
import app.pachli.settings.AccountPreferenceDataStore
import app.pachli.usecase.TimelineCases
import app.pachli.util.StatusDisplayOptionsRepository
import at.connyduck.calladapter.networkresult.NetworkResult
import com.google.gson.Gson
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.stub
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant
import java.util.Date
import javax.inject.Inject

open class PachliHiltApplication : PachliApplication()

@CustomTestApplication(PachliHiltApplication::class)
interface HiltTestApplication

@HiltAndroidTest
@Config(application = HiltTestApplication_Application::class)
@RunWith(AndroidJUnit4::class)
class ViewThreadViewModelTest {
    @get:Rule(order = 0)
    var hilt = HiltAndroidRule(this)

    /**
     * Execute each task synchronously.
     *
     * If you do not do this, and you have code like this under test:
     *
     * ```
     * fun someFunc() = viewModelScope.launch {
     *     _uiState.value = "initial value"
     *     // ...
     *     call_a_suspend_fun()
     *     // ...
     *     _uiState.value = "new value"
     * }
     * ```
     *
     * and a test like:
     *
     * ```
     * someFunc()
     * assertEquals("new value", viewModel.uiState.value)
     * ```
     *
     * The test will fail, because someFunc() yields at the `call_a_suspend_func()` point,
     * and control returns to the test before `_uiState.value` has been changed.
     */
    @get:Rule(order = 1)
    val instantTaskRule = InstantTaskExecutorRule()

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var sharedPreferencesRepository: SharedPreferencesRepository

    @Inject
    lateinit var timelineCases: TimelineCases

    @Inject
    lateinit var timelineDao: TimelineDao

    @Inject
    lateinit var gson: Gson

    @BindValue @JvmField
    val filtersRepository: FiltersRepository = mock()

    private lateinit var accountPreferenceDataStore: AccountPreferenceDataStore

    private lateinit var statusDisplayOptionsRepository: StatusDisplayOptionsRepository

    private lateinit var viewModel: ViewThreadViewModel

    private val threadId = "1234"

    @Before
    fun setup() {
        hilt.inject()

        reset(filtersRepository)
        filtersRepository.stub {
            onBlocking { getFilters() } doReturn FilterKind.V2(emptyList())
        }

        val filterModel = FilterModel()

        val defaultAccount = AccountEntity(
            id = 1,
            domain = "mastodon.test",
            accessToken = "fakeToken",
            clientId = "fakeId",
            clientSecret = "fakeSecret",
            isActive = true,
        )

        accountManager.addAccount(
            accessToken = "token",
            domain = "domain.example",
            clientId = "id",
            clientSecret = "secret",
            oauthScopes = "scopes",
            newAccount = Account(
                id = "1",
                localUsername = "username",
                username = "username@domain.example",
                displayName = "Display Name",
                createdAt = Date.from(Instant.now()),
                note = "",
                url = "",
                avatar = "",
                header = "",
            ),
        )

        accountPreferenceDataStore = AccountPreferenceDataStore(
            accountManager,
            TestScope(),
        )

        val cachedTimelineRepository: CachedTimelineRepository = mock {
            onBlocking { getStatusViewData(any()) } doReturn emptyMap()
        }

        statusDisplayOptionsRepository = StatusDisplayOptionsRepository(
            sharedPreferencesRepository,
            accountManager,
            accountPreferenceDataStore,
            TestScope(),
        )

        viewModel = ViewThreadViewModel(
            mastodonApi,
            filterModel,
            timelineCases,
            eventHub,
            accountManager,
            timelineDao,
            gson,
            cachedTimelineRepository,
            statusDisplayOptionsRepository,
            filtersRepository,
        )
    }

    @Test
    fun `should emit status and context when both load`() {
        mockSuccessResponses()

        viewModel.loadThread(threadId)

        runBlocking {
            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(id = "1", spoilerText = "Test"),
                        mockStatusViewData(
                            id = "2",
                            inReplyToId = "1",
                            inReplyToAccountId = "1",
                            isDetailed = true,
                            spoilerText = "Test",
                        ),
                        mockStatusViewData(
                            id = "3",
                            inReplyToId = "2",
                            inReplyToAccountId = "1",
                            spoilerText = "Test",
                        ),
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                viewModel.uiState.first(),
            )
        }
    }

    @Test
    fun `should emit status even if context fails to load`() {
        mastodonApi.stub {
            onBlocking { status(threadId) } doReturn NetworkResult.success(mockStatus(id = "2", inReplyToId = "1", inReplyToAccountId = "1"))
            onBlocking { statusContext(threadId) } doReturn NetworkResult.failure(IOException())
        }

        viewModel.loadThread(threadId)

        runBlocking {
            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(
                            id = "2",
                            inReplyToId = "1",
                            inReplyToAccountId = "1",
                            isDetailed = true,
                        ),
                    ),
                    detailedStatusPosition = 0,
                    revealButton = RevealButtonState.NO_BUTTON,
                ),
                viewModel.uiState.first(),
            )
        }
    }

    @Test
    fun `should emit error when status and context fail to load`() {
        mastodonApi.stub {
            onBlocking { status(threadId) } doReturn NetworkResult.failure(IOException())
            onBlocking { statusContext(threadId) } doReturn NetworkResult.failure(IOException())
        }

        viewModel.loadThread(threadId)

        runBlocking {
            assertEquals(
                ThreadUiState.Error::class.java,
                viewModel.uiState.first().javaClass,
            )
        }
    }

    @Test
    fun `should emit error when status fails to load`() {
        mastodonApi.stub {
            onBlocking { status(threadId) } doReturn NetworkResult.failure(IOException())
            onBlocking { statusContext(threadId) } doReturn NetworkResult.success(
                StatusContext(
                    ancestors = listOf(mockStatus(id = "1")),
                    descendants = listOf(mockStatus(id = "3", inReplyToId = "2", inReplyToAccountId = "1")),
                ),
            )
        }

        viewModel.loadThread(threadId)

        runBlocking {
            assertEquals(
                ThreadUiState.Error::class.java,
                viewModel.uiState.first().javaClass,
            )
        }
    }

    @Test
    fun `should update state when reveal button is toggled`() {
        mockSuccessResponses()

        viewModel.loadThread(threadId)
        viewModel.toggleRevealButton()

        runBlocking {
            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(id = "1", spoilerText = "Test", isExpanded = true),
                        mockStatusViewData(
                            id = "2",
                            inReplyToId = "1",
                            inReplyToAccountId = "1",
                            isDetailed = true,
                            spoilerText = "Test",
                            isExpanded = true,
                        ),
                        mockStatusViewData(
                            id = "3",
                            inReplyToId = "2",
                            inReplyToAccountId = "1",
                            spoilerText = "Test",
                            isExpanded = true,
                        ),
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.HIDE,
                ),
                viewModel.uiState.first(),
            )
        }
    }

    @Test
    fun `should handle favorite event`() {
        mockSuccessResponses()

        viewModel.loadThread(threadId)

        runBlocking {
            eventHub.dispatch(FavoriteEvent(statusId = "1", false))

            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(id = "1", spoilerText = "Test", favourited = false),
                        mockStatusViewData(
                            id = "2",
                            inReplyToId = "1",
                            inReplyToAccountId = "1",
                            isDetailed = true,
                            spoilerText = "Test",
                        ),
                        mockStatusViewData(
                            id = "3",
                            inReplyToId = "2",
                            inReplyToAccountId = "1",
                            spoilerText = "Test",
                        ),
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                viewModel.uiState.first(),
            )
        }
    }

    @Test
    fun `should handle reblog event`() {
        mockSuccessResponses()

        viewModel.loadThread(threadId)

        runBlocking {
            eventHub.dispatch(ReblogEvent(statusId = "2", true))

            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(id = "1", spoilerText = "Test"),
                        mockStatusViewData(
                            id = "2",
                            inReplyToId = "1",
                            inReplyToAccountId = "1",
                            isDetailed = true,
                            spoilerText = "Test",
                            reblogged = true,
                        ),
                        mockStatusViewData(
                            id = "3",
                            inReplyToId = "2",
                            inReplyToAccountId = "1",
                            spoilerText = "Test",
                        ),
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                viewModel.uiState.first(),
            )
        }
    }

    @Test
    fun `should handle bookmark event`() {
        mockSuccessResponses()

        viewModel.loadThread(threadId)

        runBlocking {
            eventHub.dispatch(BookmarkEvent(statusId = "3", false))

            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(id = "1", spoilerText = "Test"),
                        mockStatusViewData(
                            id = "2",
                            inReplyToId = "1",
                            inReplyToAccountId = "1",
                            isDetailed = true,
                            spoilerText = "Test",
                        ),
                        mockStatusViewData(
                            id = "3",
                            inReplyToId = "2",
                            inReplyToAccountId = "1",
                            spoilerText = "Test",
                            bookmarked = false,
                        ),
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                viewModel.uiState.first(),
            )
        }
    }

    @Test
    fun `should remove status`() {
        mockSuccessResponses()

        viewModel.loadThread(threadId)

        viewModel.removeStatus(mockStatusViewData(id = "3", inReplyToId = "2", inReplyToAccountId = "1", spoilerText = "Test"))

        runBlocking {
            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(id = "1", spoilerText = "Test"),
                        mockStatusViewData(
                            id = "2",
                            inReplyToId = "1",
                            inReplyToAccountId = "1",
                            isDetailed = true,
                            spoilerText = "Test",
                        ),
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                viewModel.uiState.first(),
            )
        }
    }

    @Test
    fun `should change status expanded state`() {
        mockSuccessResponses()

        viewModel.loadThread(threadId)

        viewModel.changeExpanded(
            true,
            mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test"),
        )

        runBlocking {
            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(id = "1", spoilerText = "Test"),
                        mockStatusViewData(
                            id = "2",
                            inReplyToId = "1",
                            inReplyToAccountId = "1",
                            isDetailed = true,
                            spoilerText = "Test",
                            isExpanded = true,
                        ),
                        mockStatusViewData(
                            id = "3",
                            inReplyToId = "2",
                            inReplyToAccountId = "1",
                            spoilerText = "Test",
                        ),
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                viewModel.uiState.first(),
            )
        }
    }

    @Test
    fun `should change content collapsed state`() {
        mockSuccessResponses()

        viewModel.loadThread(threadId)

        viewModel.changeContentCollapsed(
            true,
            mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test"),
        )

        runBlocking {
            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(id = "1", spoilerText = "Test"),
                        mockStatusViewData(
                            id = "2",
                            inReplyToId = "1",
                            inReplyToAccountId = "1",
                            isDetailed = true,
                            spoilerText = "Test",
                            isCollapsed = true,
                        ),
                        mockStatusViewData(
                            id = "3",
                            inReplyToId = "2",
                            inReplyToAccountId = "1",
                            spoilerText = "Test",
                        ),
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                viewModel.uiState.first(),
            )
        }
    }

    @Test
    fun `should change content showing state`() {
        mockSuccessResponses()

        viewModel.loadThread(threadId)

        viewModel.changeContentShowing(
            true,
            mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test"),
        )

        runBlocking {
            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(id = "1", spoilerText = "Test"),
                        mockStatusViewData(
                            id = "2",
                            inReplyToId = "1",
                            inReplyToAccountId = "1",
                            isDetailed = true,
                            spoilerText = "Test",
                            isShowingContent = true,
                        ),
                        mockStatusViewData(
                            id = "3",
                            inReplyToId = "2",
                            inReplyToAccountId = "1",
                            spoilerText = "Test",
                        ),
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                viewModel.uiState.first(),
            )
        }
    }

    private fun mockSuccessResponses() {
        mastodonApi.stub {
            onBlocking { status(threadId) } doReturn NetworkResult.success(mockStatus(id = "2", inReplyToId = "1", inReplyToAccountId = "1", spoilerText = "Test"))
            onBlocking { statusContext(threadId) } doReturn NetworkResult.success(
                StatusContext(
                    ancestors = listOf(mockStatus(id = "1", spoilerText = "Test")),
                    descendants = listOf(mockStatus(id = "3", inReplyToId = "2", inReplyToAccountId = "1", spoilerText = "Test")),
                ),
            )
        }
    }
}
