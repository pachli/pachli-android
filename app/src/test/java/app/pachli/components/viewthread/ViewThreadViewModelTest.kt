package app.pachli.components.viewthread

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.pachli.PachliApplication
import app.pachli.components.compose.HiltTestApplication_Application
import app.pachli.components.timeline.CachedTimelineRepository
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.OfflineFirstStatusRepository
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.eventhub.BookmarkEvent
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.FavoriteEvent
import app.pachli.core.eventhub.ReblogEvent
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.AttachmentDisplayReason
import app.pachli.core.network.di.test.DEFAULT_INSTANCE_V2
import app.pachli.core.network.model.AccountSource
import app.pachli.core.network.model.CredentialAccount
import app.pachli.core.network.model.StatusContext
import app.pachli.core.network.model.nodeinfo.UnvalidatedJrd
import app.pachli.core.network.model.nodeinfo.UnvalidatedNodeInfo
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.NodeInfoApi
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.testing.failure
import app.pachli.core.testing.fakes.fakeStatus
import app.pachli.core.testing.fakes.fakeStatusViewData
import app.pachli.core.testing.rules.MainCoroutineRule
import app.pachli.core.testing.success
import app.pachli.usecase.TimelineCases
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyLong
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
    lateinit var statusDisplayOptionsRepository: StatusDisplayOptionsRepository

    @Inject
    lateinit var statusRepository: OfflineFirstStatusRepository

    private lateinit var viewModel: ViewThreadViewModel

    private val threadId = "1234"

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

    @Before
    fun setup() = runTest {
        hilt.inject()

        reset(mastodonApi)
        mastodonApi.stub {
            onBlocking { accountVerifyCredentials(anyOrNull(), anyOrNull()) } doReturn success(account)
            onBlocking { getInstanceV2(anyOrNull()) } doReturn success(DEFAULT_INSTANCE_V2)
            onBlocking { getCustomEmojis() } doReturn failure()
            onBlocking { listAnnouncements(any()) } doReturn success(emptyList())
            onBlocking { getLists() } doReturn success(emptyList())
            onBlocking { getContentFilters() } doReturn success(emptyList())
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
            .andThen { accountManager.setActiveAccount(it) }
            .onSuccess { accountManager.refresh(it) }

        val cachedTimelineRepository: CachedTimelineRepository = mock {
            onBlocking { getStatusViewData(anyLong(), any<List<String>>()) } doReturn emptyMap()
            onBlocking { getStatusTranslations(anyLong(), any()) } doReturn emptyMap()
        }

        viewModel = ViewThreadViewModel(
            mastodonApi,
            eventHub,
            accountManager,
            timelineDao,
            cachedTimelineRepository,
            statusDisplayOptionsRepository,
            timelineCases,
        )
    }

    @Test
    fun `should emit status and context when both load`() = runTest {
        mockSuccessResponses()

        viewModel.uiResult.test {
            viewModel.loadThread(threadId)
            var item: ThreadUiState?

            do {
                item = awaitItem().get()
            } while (item !is ThreadUiState.Loaded)

            assertEquals(
                ThreadUiState.Loaded(
                    statusViewData = listOf(
                        fakeStatusViewData(id = "1", spoilerText = "Test"),
                        fakeStatusViewData(
                            id = "2",
                            inReplyToId = "1",
                            inReplyToAccountId = "1",
                            isDetailed = true,
                            spoilerText = "Test",
                        ),
                        fakeStatusViewData(
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
            onBlocking { status(threadId) } doReturn success(fakeStatus(id = "2", inReplyToId = "1", inReplyToAccountId = "1"))
            onBlocking { statusContext(threadId) } doReturn failure()
        }

        viewModel.uiResult.test {
            viewModel.loadThread(threadId)
            var item: ThreadUiState?

            do {
                item = awaitItem().get()
            } while (item !is ThreadUiState.Loaded)

            assertEquals(
                ThreadUiState.Loaded(
                    statusViewData = listOf(
                        fakeStatusViewData(
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
            onBlocking { status(threadId) } doReturn failure()
            onBlocking { statusContext(threadId) } doReturn failure()
        }

        viewModel.uiResult.test {
            viewModel.loadThread(threadId)
            var item: ThreadError?

            do {
                item = awaitItem().getError()
            } while (item !is ThreadError)

            assertEquals(
                ThreadError.Api::class.java,
                item.javaClass,
            )
        }
    }

    @Test
    fun `should emit error when status fails to load`() = runTest {
        mastodonApi.stub {
            onBlocking { status(threadId) } doReturn failure()
            onBlocking { statusContext(threadId) } doReturn success(
                StatusContext(
                    ancestors = listOf(fakeStatus(id = "1")),
                    descendants = listOf(fakeStatus(id = "3", inReplyToId = "2", inReplyToAccountId = "1")),
                ),
            )
        }

        viewModel.uiResult.test {
            viewModel.loadThread(threadId)
            var item: ThreadError?

            do {
                item = awaitItem().getError()
            } while (item !is ThreadError)

            assertEquals(
                ThreadError.Api::class.java,
                item.javaClass,
            )
        }
    }

    @Test
    fun `should update state when reveal button is toggled`() = runTest {
        viewModel.uiResult.test {
            mockSuccessResponses()

            viewModel.loadThread(threadId)
            while (awaitItem().get() !is ThreadUiState.Loaded) {
            }
            viewModel.toggleRevealButton()

            assertEquals(
                ThreadUiState.Loaded(
                    statusViewData = listOf(
                        fakeStatusViewData(id = "1", spoilerText = "Test", isExpanded = true),
                        fakeStatusViewData(
                            id = "2",
                            inReplyToId = "1",
                            inReplyToAccountId = "1",
                            isDetailed = true,
                            spoilerText = "Test",
                            isExpanded = true,
                        ),
                        fakeStatusViewData(
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
                expectMostRecentItem().get(),
            )
        }
    }

    @Test
    fun `should handle favorite event`() = runTest {
        viewModel.uiResult.test {
            mockSuccessResponses()

            viewModel.loadThread(threadId)
            while (awaitItem().get() !is ThreadUiState.Loaded) {
            }

            eventHub.dispatch(FavoriteEvent(statusId = "1", false))
            assertEquals(
                ThreadUiState.Loaded(
                    statusViewData = listOf(
                        fakeStatusViewData(id = "1", spoilerText = "Test", favourited = false),
                        fakeStatusViewData(
                            id = "2",
                            inReplyToId = "1",
                            inReplyToAccountId = "1",
                            isDetailed = true,
                            spoilerText = "Test",
                        ),
                        fakeStatusViewData(
                            id = "3",
                            inReplyToId = "2",
                            inReplyToAccountId = "1",
                            spoilerText = "Test",
                        ),
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                expectMostRecentItem().get(),
            )
        }
    }

    @Test
    fun `should handle reblog event`() = runTest {
        viewModel.uiResult.test {
            mockSuccessResponses()

            viewModel.loadThread(threadId)
            while (awaitItem().get() !is ThreadUiState.Loaded) {
            }
            eventHub.dispatch(ReblogEvent(statusId = "2", true))

            assertEquals(
                ThreadUiState.Loaded(
                    statusViewData = listOf(
                        fakeStatusViewData(id = "1", spoilerText = "Test"),
                        fakeStatusViewData(
                            id = "2",
                            inReplyToId = "1",
                            inReplyToAccountId = "1",
                            isDetailed = true,
                            spoilerText = "Test",
                            reblogged = true,
                        ),
                        fakeStatusViewData(
                            id = "3",
                            inReplyToId = "2",
                            inReplyToAccountId = "1",
                            spoilerText = "Test",
                        ),
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                expectMostRecentItem().get(),
            )
        }
    }

    @Test
    fun `should handle bookmark event`() = runTest {
        viewModel.uiResult.test {
            mockSuccessResponses()

            viewModel.loadThread(threadId)
            while (awaitItem().get() !is ThreadUiState.Loaded) {
            }
            eventHub.dispatch(BookmarkEvent(statusId = "3", false))

            assertEquals(
                ThreadUiState.Loaded(
                    statusViewData = listOf(
                        fakeStatusViewData(id = "1", spoilerText = "Test"),
                        fakeStatusViewData(
                            id = "2",
                            inReplyToId = "1",
                            inReplyToAccountId = "1",
                            isDetailed = true,
                            spoilerText = "Test",
                        ),
                        fakeStatusViewData(
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
                expectMostRecentItem().get(),
            )
        }
    }

    @Test
    fun `should remove status`() = runTest {
        viewModel.uiResult.test {
            mockSuccessResponses()

            viewModel.loadThread(threadId)
            while (awaitItem().get() !is ThreadUiState.Loaded) {
            }
            viewModel.removeStatus(fakeStatusViewData(id = "3", inReplyToId = "2", inReplyToAccountId = "1", spoilerText = "Test"))

            assertEquals(
                ThreadUiState.Loaded(
                    statusViewData = listOf(
                        fakeStatusViewData(id = "1", spoilerText = "Test"),
                        fakeStatusViewData(
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
                expectMostRecentItem().get(),
            )
        }
    }

    @Test
    fun `should change status expanded state`() = runTest {
        viewModel.uiResult.test {
            mockSuccessResponses()

            viewModel.loadThread(threadId)
            while (awaitItem().get() !is ThreadUiState.Loaded) {
            }
            viewModel.changeExpanded(
                true,
                fakeStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test"),
            )

            assertEquals(
                ThreadUiState.Loaded(
                    statusViewData = listOf(
                        fakeStatusViewData(id = "1", spoilerText = "Test"),
                        fakeStatusViewData(
                            id = "2",
                            inReplyToId = "1",
                            inReplyToAccountId = "1",
                            isDetailed = true,
                            spoilerText = "Test",
                            isExpanded = true,
                        ),
                        fakeStatusViewData(
                            id = "3",
                            inReplyToId = "2",
                            inReplyToAccountId = "1",
                            spoilerText = "Test",
                        ),
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                expectMostRecentItem().get(),
            )
        }
    }

    @Test
    fun `should change content collapsed state`() = runTest {
        viewModel.uiResult.test {
            mockSuccessResponses()

            viewModel.loadThread(threadId)
            while (awaitItem().get() !is ThreadUiState.Loaded) {
            }
            viewModel.changeContentCollapsed(
                false,
                fakeStatusViewData(id = "3", inReplyToId = "2", inReplyToAccountId = "1", isDetailed = false, spoilerText = "Test"),
            )

            assertEquals(
                ThreadUiState.Loaded(
                    statusViewData = listOf(
                        fakeStatusViewData(id = "1", spoilerText = "Test"),
                        fakeStatusViewData(
                            id = "2",
                            inReplyToId = "1",
                            inReplyToAccountId = "1",
                            isDetailed = true,
                            spoilerText = "Test",
                        ),
                        fakeStatusViewData(
                            id = "3",
                            inReplyToId = "2",
                            inReplyToAccountId = "1",
                            spoilerText = "Test",
                            isCollapsed = false,
                        ),
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                expectMostRecentItem().get(),
            )
        }
    }

    @Test
    fun `should change content showing state`() = runTest {
        viewModel.uiResult.test {
            mockSuccessResponses()

            viewModel.loadThread(threadId)
            while (awaitItem().get() !is ThreadUiState.Loaded) {
            }
            viewModel.changeAttachmentDisplayAction(
                fakeStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test"),
                AttachmentDisplayAction.Show(originalAction = AttachmentDisplayAction.Hide(AttachmentDisplayReason.Sensitive)),
            )

            assertEquals(
                ThreadUiState.Loaded(
                    statusViewData = listOf(
                        fakeStatusViewData(id = "1", spoilerText = "Test"),
                        fakeStatusViewData(
                            id = "2",
                            inReplyToId = "1",
                            inReplyToAccountId = "1",
                            isDetailed = true,
                            spoilerText = "Test",
                            isShowingContent = true,
                        ),
                        fakeStatusViewData(
                            id = "3",
                            inReplyToId = "2",
                            inReplyToAccountId = "1",
                            spoilerText = "Test",
                        ),
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                expectMostRecentItem().get(),
            )
        }
    }

    private fun mockSuccessResponses() {
        mastodonApi.stub {
            onBlocking { status(threadId) } doReturn success(fakeStatus(id = "2", inReplyToId = "1", inReplyToAccountId = "1", spoilerText = "Test"))
            onBlocking { statusContext(threadId) } doReturn success(
                StatusContext(
                    ancestors = listOf(fakeStatus(id = "1", spoilerText = "Test")),
                    descendants = listOf(fakeStatus(id = "3", inReplyToId = "2", inReplyToAccountId = "1", spoilerText = "Test")),
                ),
            )
        }
    }
}
