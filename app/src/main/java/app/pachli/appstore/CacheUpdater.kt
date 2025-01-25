package app.pachli.appstore

import app.pachli.core.data.repository.AccountManager
import app.pachli.core.database.dao.StatusDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.eventhub.BookmarkEvent
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.FavoriteEvent
import app.pachli.core.eventhub.PinEvent
import app.pachli.core.eventhub.PollVoteEvent
import app.pachli.core.eventhub.ReblogEvent
import app.pachli.core.eventhub.StatusDeletedEvent
import app.pachli.core.eventhub.UnfollowEvent
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CacheUpdater @Inject constructor(
    eventHub: EventHub,
    accountManager: AccountManager,
    timelineDao: TimelineDao,
    statusDao: StatusDao,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            eventHub.events.collect { event ->
                val accountId = accountManager.activeAccount?.id ?: return@collect
                when (event) {
                    is FavoriteEvent ->
                        statusDao.setFavourited(accountId, event.statusId, event.favourite)
                    is ReblogEvent ->
                        statusDao.setReblogged(accountId, event.statusId, event.reblog)
                    is BookmarkEvent ->
                        statusDao.setBookmarked(accountId, event.statusId, event.bookmark)
                    is UnfollowEvent ->
                        timelineDao.removeAllByUser(accountId, event.accountId)
                    is StatusDeletedEvent ->
                        statusDao.delete(accountId, event.statusId)
                    is PollVoteEvent -> {
                        statusDao.setVoted(accountId, event.statusId, event.poll)
                    }
                    is PinEvent ->
                        statusDao.setPinned(accountId, event.statusId, event.pinned)
                }
            }
        }
    }

    fun stop() {
        this.scope.cancel()
    }
}
