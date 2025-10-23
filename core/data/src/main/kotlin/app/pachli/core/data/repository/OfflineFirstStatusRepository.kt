/*
 * Copyright (c) 2025 Pachli Association
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

package app.pachli.core.data.repository

import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.repository.notifications.asEntities
import app.pachli.core.database.dao.StatusDao
import app.pachli.core.database.dao.TranslatedStatusDao
import app.pachli.core.database.model.StatusViewDataAttachmentDisplayAction
import app.pachli.core.database.model.StatusViewDataContentCollapsed
import app.pachli.core.database.model.StatusViewDataExpanded
import app.pachli.core.database.model.StatusViewDataTranslationState
import app.pachli.core.database.model.TranslationState
import app.pachli.core.eventhub.BookmarkEvent
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.FavoriteEvent
import app.pachli.core.eventhub.MuteConversationEvent
import app.pachli.core.eventhub.PinEvent
import app.pachli.core.eventhub.PollVoteEvent
import app.pachli.core.eventhub.ReblogEvent
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.Poll
import app.pachli.core.model.Status
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

/**
 * Repository for managing actions on statuses ([bookmark], etc).
 *
 * Operations are generally performed locally first so the UI updates quickly,
 * then sent to the server, and if they fail the local operation is undone.
 *
 * This should not be used by anything other than other repositories. Those
 * repositories should inject this and implement [StatusRepository] by
 * delegating to this repository.
 *
 * ```kotlin
 * class SomeOtherRepository @Inject constructor(
 *     statusRepository: OfflineFirstStatusRepository,
 *     // ...
 * ) : StatusRepository by statusRepository { /* ... */ }
 */
class OfflineFirstStatusRepository @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val mastodonApi: MastodonApi,
    private val statusDao: StatusDao,
    private val translatedStatusDao: TranslatedStatusDao,
    private val eventHub: EventHub,
) : StatusRepository {
    override suspend fun bookmark(
        pachliAccountId: Long,
        statusId: String,
        bookmarked: Boolean,
    ): Result<Status, StatusActionError.Bookmark> = externalScope.async {
        statusDao.setBookmarked(pachliAccountId, statusId, bookmarked)
        if (bookmarked) {
            mastodonApi.bookmarkStatus(statusId)
        } else {
            mastodonApi.unbookmarkStatus(statusId)
        }
            .onSuccess {
                statusDao.upsertStatuses(it.body.asEntities(pachliAccountId))
                eventHub.dispatch(BookmarkEvent(statusId, bookmarked))
            }
            .onFailure { statusDao.setBookmarked(pachliAccountId, statusId, !bookmarked) }
            .mapEither({ it.body.asModel() }, { StatusActionError.Bookmark(it) })
    }.await()

    override suspend fun favourite(
        pachliAccountId: Long,
        statusId: String,
        favourited: Boolean,
    ): Result<Status, StatusActionError.Favourite> = externalScope.async {
        statusDao.setFavourited(pachliAccountId, statusId, favourited)
        if (favourited) {
            mastodonApi.favouriteStatus(statusId)
        } else {
            mastodonApi.unfavouriteStatus(statusId)
        }
            .onSuccess {
                statusDao.upsertStatuses(it.body.asEntities(pachliAccountId))
                eventHub.dispatch(FavoriteEvent(statusId, favourited))
            }
            .onFailure { statusDao.setFavourited(pachliAccountId, statusId, !favourited) }
            .mapEither({ it.body.asModel() }, { StatusActionError.Favourite(it) })
    }.await()

    override suspend fun reblog(
        pachliAccountId: Long,
        statusId: String,
        reblogged: Boolean,
    ): Result<Status, StatusActionError.Reblog> = externalScope.async {
        statusDao.setReblogged(pachliAccountId, statusId, reblogged)
        if (reblogged) {
            mastodonApi.reblogStatus(statusId)
        } else {
            mastodonApi.unreblogStatus(statusId)
        }
            .onSuccess {
                statusDao.upsertStatuses(it.body.asEntities(pachliAccountId))
                eventHub.dispatch(ReblogEvent(statusId, reblogged))
            }
            .onFailure { statusDao.setReblogged(pachliAccountId, statusId, !reblogged) }
            .mapEither({ it.body.asModel() }, { StatusActionError.Reblog(it) })
    }.await()

    override suspend fun mute(
        pachliAccountId: Long,
        statusId: String,
        muted: Boolean,
    ): Result<Status, StatusActionError.Mute> = externalScope.async {
        statusDao.setMuted(pachliAccountId, statusId, muted)
        return@async if (muted) {
            mastodonApi.muteConversation(statusId)
        } else {
            mastodonApi.unmuteConversation(statusId)
        }
            .onSuccess {
                statusDao.upsertStatuses(it.body.asEntities(pachliAccountId))
                eventHub.dispatch(MuteConversationEvent(pachliAccountId, statusId, muted))
            }
            .onFailure { statusDao.setMuted(pachliAccountId, statusId, !muted) }
            .mapEither({ it.body.asModel() }, { StatusActionError.Mute(it) })
    }.await()

    override suspend fun pin(
        pachliAccountId: Long,
        statusId: String,
        pinned: Boolean,
    ): Result<Status, StatusActionError.Pin> = externalScope.async {
        statusDao.setPinned(pachliAccountId, statusId, pinned)
        return@async if (pinned) {
            mastodonApi.pinStatus(statusId)
        } else {
            mastodonApi.unpinStatus(statusId)
        }
            .onSuccess {
                statusDao.upsertStatuses(it.body.asEntities(pachliAccountId))
                eventHub.dispatch(PinEvent(statusId, pinned))
            }
            .onFailure { statusDao.setPinned(pachliAccountId, statusId, !pinned) }
            .mapEither({ it.body.asModel() }, { StatusActionError.Pin(it) })
    }.await()

    override suspend fun voteInPoll(
        pachliAccountId: Long,
        statusId: String,
        pollId: String,
        choices: List<Int>,
    ): Result<Poll, StatusActionError.VoteInPoll> = externalScope.async {
        val originalPoll = statusDao.getStatus(pachliAccountId, statusId)?.poll
        originalPoll?.let {
            statusDao.setPoll(pachliAccountId, statusId, originalPoll.votedCopy(choices))
        }

        mastodonApi.voteInPoll(pollId, choices)
            .map { it.body.asModel() }
            .onSuccess { poll ->
                if (originalPoll != null) {
                    statusDao.setPoll(pachliAccountId, statusId, poll)
                }
                eventHub.dispatch(PollVoteEvent(statusId, poll))
            }
            .onFailure { originalPoll?.let { statusDao.setPoll(pachliAccountId, statusId, it) } }
            .mapError { StatusActionError.VoteInPoll(it) }
    }.await()

    override suspend fun setExpanded(pachliAccountId: Long, statusId: String, expanded: Boolean) {
        statusDao.setExpanded(
            StatusViewDataExpanded(
                pachliAccountId = pachliAccountId,
                serverId = statusId,
                expanded = expanded,
            ),
        )
    }

    override suspend fun setAttachmentDisplayAction(pachliAccountId: Long, statusId: String, attachmentDisplayAction: AttachmentDisplayAction) {
        statusDao.setAttachmentDisplayAction(
            StatusViewDataAttachmentDisplayAction(
                pachliAccountId = pachliAccountId,
                serverId = statusId,
                attachmentDisplayAction = attachmentDisplayAction,
            ),
        )
    }

    override suspend fun setContentCollapsed(pachliAccountId: Long, statusId: String, contentCollapsed: Boolean) {
        statusDao.setContentCollapsed(
            StatusViewDataContentCollapsed(
                pachliAccountId = pachliAccountId,
                serverId = statusId,
                contentCollapsed = contentCollapsed,
            ),
        )
    }

    override suspend fun setTranslationState(pachliAccountId: Long, statusId: String, translationState: TranslationState) {
        statusDao.setTranslationState(
            StatusViewDataTranslationState(
                pachliAccountId = pachliAccountId,
                serverId = statusId,
                translationState = translationState,
            ),
        )
    }

    override suspend fun getStatusViewData(pachliAccountId: Long, statusId: String) = statusDao.getStatusViewData(pachliAccountId, statusId)

    override suspend fun getTranslation(pachliAccountId: Long, statusId: String) = translatedStatusDao.getTranslation(pachliAccountId, statusId)
}
