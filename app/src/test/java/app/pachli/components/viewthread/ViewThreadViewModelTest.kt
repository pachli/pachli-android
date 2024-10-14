package app.pachli.components.viewthread

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.pachli.PachliApplication
import app.pachli.appstore.BookmarkEvent
import app.pachli.appstore.EventHub
import app.pachli.appstore.FavoriteEvent
import app.pachli.appstore.ReblogEvent
import app.pachli.components.compose.HiltTestApplication_Application
import app.pachli.components.timeline.CachedTimelineRepository
import app.pachli.components.timeline.mockStatus
import app.pachli.components.timeline.mockStatusViewData
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.network.di.test.DEFAULT_INSTANCE_V2
import app.pachli.core.network.model.Account
import app.pachli.core.network.model.StatusContext
import app.pachli.core.network.model.nodeinfo.UnvalidatedJrd
import app.pachli.core.network.model.nodeinfo.UnvalidatedNodeInfo
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.NodeInfoApi
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.testing.failure
import app.pachli.core.testing.rules.MainCoroutineRule
import app.pachli.core.testing.success
import app.pachli.usecase.TimelineCases
import at.connyduck.calladapter.networkresult.NetworkResult
import com.github.michaelbull.result.andThen
import com.squareup.moshi.Moshi
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.IOException
import java.time.Instant
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
import org.robolectric.annotation.Config

open class PachliHiltApplication : PachliApplication()

@CustomTestApplication(PachliHiltApplication::class)
interface HiltTestApplication

@HiltAndroidTest
@Config(application = HiltTestApplication_Application::class)
@RunWith(AndroidJUnit4::class)
class ViewThreadViewModelTest {
    @get:Rule(order = 0)
    var hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val instantTaskRule = MainCoroutineRule()

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var nodeInfoApi: NodeInfoApi

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var sharedPreferencesRepository: SharedPreferencesRepository

    @Inject
    lateinit var timelineCases: TimelineCases

    @Inject
    lateinit var timelineDao: TimelineDao

    @Inject
    lateinit var moshi: Moshi

    @Inject
    lateinit var statusDisplayOptionsRepository: StatusDisplayOptionsRepository

    private lateinit var viewModel: ViewThreadViewModel

    private val threadId = "1234"

    private val account = Account(
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

    @Before
    fun setup() = runTest {
        hilt.inject()

        reset(mastodonApi)
        mastodonApi.stub {
            onBlocking { accountVerifyCredentials(anyOrNull(), anyOrNull()) } doReturn success(account)
            onBlocking { getInstanceV2(anyOrNull()) } doReturn success(DEFAULT_INSTANCE_V2)
            onBlocking { getCustomEmojis() } doReturn failure()
            onBlocking { listAnnouncements(any()) } doReturn success(emptyList())
            onBlocking { getContentFilters() } doReturn success(emptyList())
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
        ).andThen { accountManager.setActiveAccount(it) }

        val cachedTimelineRepository: CachedTimelineRepository = mock {
            onBlocking { getStatusViewData(any()) } doReturn emptyMap()
            onBlocking { getStatusTranslations(any()) } doReturn emptyMap()
        }

        viewModel = ViewThreadViewModel(
            mastodonApi,
            timelineCases,
            eventHub,
            accountManager,
            timelineDao,
            moshi,
            cachedTimelineRepository,
            statusDisplayOptionsRepository,
        )
    }

    @Test
    fun `should emit status and context when both load`() = runTest {
        mockSuccessResponses()

        viewModel.uiState.test {
            viewModel.loadThread(threadId)
            var item: ThreadUiState

            do {
                item = awaitItem()
            } while (item is ThreadUiState.Loading)

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
                item,
            )
        }
    }

    @Test
    fun `should emit status even if context fails to load`() = runTest {
        mastodonApi.stub {
            onBlocking { status(threadId) } doReturn NetworkResult.success(mockStatus(id = "2", inReplyToId = "1", inReplyToAccountId = "1"))
            onBlocking { statusContext(threadId) } doReturn NetworkResult.failure(IOException())
        }

        viewModel.uiState.test {
            viewModel.loadThread(threadId)
            var item: ThreadUiState

            do {
                item = awaitItem()
            } while (item is ThreadUiState.Loading)

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
                item,
            )
        }
    }

    @Test
    fun `should emit error when status and context fail to load`() = runTest {
        mastodonApi.stub {
            onBlocking { status(threadId) } doReturn NetworkResult.failure(IOException())
            onBlocking { statusContext(threadId) } doReturn NetworkResult.failure(IOException())
        }

        viewModel.uiState.test {
            viewModel.loadThread(threadId)
            var item: ThreadUiState

            do {
                item = awaitItem()
            } while (item is ThreadUiState.Loading)

            assertEquals(
                ThreadUiState.Error::class.java,
                item.javaClass,
            )
        }
    }

    @Test
    fun `should emit error when status fails to load`() = runTest {
        mastodonApi.stub {
            onBlocking { status(threadId) } doReturn NetworkResult.failure(IOException())
            onBlocking { statusContext(threadId) } doReturn NetworkResult.success(
                StatusContext(
                    ancestors = listOf(mockStatus(id = "1")),
                    descendants = listOf(mockStatus(id = "3", inReplyToId = "2", inReplyToAccountId = "1")),
                ),
            )
        }

        viewModel.uiState.test {
            viewModel.loadThread(threadId)
            var item: ThreadUiState

            do {
                item = awaitItem()
            } while (item is ThreadUiState.Loading)

            assertEquals(
                ThreadUiState.Error::class.java,
                item.javaClass,
            )
        }
    }

    @Test
    fun `should update state when reveal button is toggled`() = runTest {
        viewModel.uiState.test {
            mockSuccessResponses()

            viewModel.loadThread(threadId)
            while (awaitItem() !is ThreadUiState.Success) {
            }
            viewModel.toggleRevealButton()

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
                expectMostRecentItem(),
            )
        }
    }

    @Test
    fun `should handle favorite event`() = runTest {
        viewModel.uiState.test {
            mockSuccessResponses()

            viewModel.loadThread(threadId)
            while (awaitItem() !is ThreadUiState.Success) {
            }

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
                expectMostRecentItem(),
            )
        }
    }

    @Test
    fun `should handle reblog event`() = runTest {
        viewModel.uiState.test {
            mockSuccessResponses()

            viewModel.loadThread(threadId)
            while (awaitItem() !is ThreadUiState.Success) {
            }
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
                expectMostRecentItem(),
            )
        }
    }

    @Test
    fun `should handle bookmark event`() = runTest {
        viewModel.uiState.test {
            mockSuccessResponses()

            viewModel.loadThread(threadId)
            while (awaitItem() !is ThreadUiState.Success) {
            }
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
                expectMostRecentItem(),
            )
        }
    }

    @Test
    fun `should remove status`() = runTest {
        viewModel.uiState.test {
            mockSuccessResponses()

            viewModel.loadThread(threadId)
            while (awaitItem() !is ThreadUiState.Success) {
            }
            viewModel.removeStatus(mockStatusViewData(id = "3", inReplyToId = "2", inReplyToAccountId = "1", spoilerText = "Test"))

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
                expectMostRecentItem(),
            )
        }
    }

    @Test
    fun `should change status expanded state`() = runTest {
        viewModel.uiState.test {
            mockSuccessResponses()

            viewModel.loadThread(threadId)
            while (awaitItem() !is ThreadUiState.Success) {
            }
            viewModel.changeExpanded(
                true,
                mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test"),
            )

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
                expectMostRecentItem(),
            )
        }
    }

    @Test
    fun `should change content collapsed state`() = runTest {
        viewModel.uiState.test {
            mockSuccessResponses()

            viewModel.loadThread(threadId)
            while (awaitItem() !is ThreadUiState.Success) {
            }
            viewModel.changeContentCollapsed(
                true,
                mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test"),
            )

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
                expectMostRecentItem(),
            )
        }
    }

    @Test
    fun `should change content showing state`() = runTest {
        viewModel.uiState.test {
            mockSuccessResponses()

            viewModel.loadThread(threadId)
            while (awaitItem() !is ThreadUiState.Success) {
            }
            viewModel.changeContentShowing(
                true,
                mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test"),
            )

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
                expectMostRecentItem(),
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
