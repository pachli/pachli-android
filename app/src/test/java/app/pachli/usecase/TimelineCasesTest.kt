package app.pachli.usecase

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.pachli.components.timeline.CachedTimelineRepository
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.PinEvent
import app.pachli.core.network.extensions.getServerErrorMessage
import app.pachli.core.network.model.Status
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.testing.failure
import app.pachli.core.testing.success
import com.github.michaelbull.result.getErrorOr
import java.util.Date
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

@RunWith(AndroidJUnit4::class)
class TimelineCasesTest {

    private lateinit var api: MastodonApi
    private lateinit var eventHub: EventHub
    private lateinit var cachedTimelineRepository: CachedTimelineRepository
    private lateinit var timelineCases: TimelineCases

    private val statusId = "1234"

    @Before
    fun setup() {
        api = mock()
        eventHub = EventHub()
        cachedTimelineRepository = mock()
        timelineCases = TimelineCases(api, eventHub, cachedTimelineRepository)
    }

    @Test
    fun `pin success emits PinEvent`() {
        api.stub {
            onBlocking { pinStatus(statusId) } doReturn success(mockStatus(pinned = true))
        }

        runBlocking {
            eventHub.events.test {
                timelineCases.pin(statusId, true)
                assertEquals(PinEvent(statusId, true), awaitItem())
            }
        }
    }

    @Test
    fun `pin failure with server error returns failure with server message`() {
        api.stub {
            onBlocking { pinStatus(statusId) } doReturn failure(
                code = 422,
                responseBody = "{\"error\":\"Validation Failed: You have already pinned the maximum number of toots\"}",
            )
        }
        runBlocking {
            assertEquals(
                "Validation Failed: You have already pinned the maximum number of toots",
                timelineCases.pin(statusId, true).getErrorOr(null)?.throwable?.getServerErrorMessage(),
            )
        }
    }

    private fun mockStatus(pinned: Boolean = false): Status {
        return Status(
            id = "123",
            url = "https://mastodon.social/@Tusky/100571663297225812",
            account = mock(),
            inReplyToId = null,
            inReplyToAccountId = null,
            reblog = null,
            content = "",
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
            attachments = arrayListOf(),
            mentions = listOf(),
            tags = listOf(),
            application = null,
            pinned = pinned,
            muted = false,
            poll = null,
            card = null,
            language = null,
            filtered = null,
        )
    }
}
