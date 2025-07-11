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

import app.pachli.core.common.PachliError
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.database.dao.StatusDao
import app.pachli.core.database.dao.TranslatedStatusDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.StatusViewDataContentCollapsed
import app.pachli.core.database.model.StatusViewDataContentShowing
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
import app.pachli.core.model.Poll
import app.pachli.core.model.Status
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.apiresult.ApiError
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
 * Errors that can occur acting on a status.
 *
 * @param error The underlying error.
 */
sealed class StatusActionError(open val error: ApiError) : PachliError by error {
    /** Bookmarking a status failed. */
    data class Bookmark(override val error: ApiError) : StatusActionError(error)

    /** Favouriting a status failed. */
    data class Favourite(override val error: ApiError) : StatusActionError(error)

    /** Reblogging a status failed. */
    data class Reblog(override val error: ApiError) : StatusActionError(error)

    /** Mute/unmuting a status failed. */
    data class Mute(override val error: ApiError) : StatusActionError(error)

    /** Pin/unpinning a status failed. */
    data class Pin(override val error: ApiError) : StatusActionError(error)

    /** Voting in a poll failed. */
    data class VoteInPoll(override val error: ApiError) : StatusActionError(error)
}

class StatusRepository @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val mastodonApi: MastodonApi,
    private val transactionProvider: TransactionProvider,
    private val statusDao: StatusDao,
    private val translatedStatusDao: TranslatedStatusDao,
    private val eventHub: EventHub,
) {
    /**
     * Sets the bookmarked state of [statusId] to [bookmarked].
     *
     * Sends [BookmarkEvent] if successful.
     *
     * @param pachliAccountId
     * @param statusId ID of the status to affect.
     * @param bookmarked New bookmarked state.
     */
    suspend fun bookmark(
        pachliAccountId: Long,
        statusId: String,
        bookmarked: Boolean,
    ): Result<Status, StatusActionError.Bookmark> = externalScope.async {
        transactionProvider {
            statusDao.setBookmarked(pachliAccountId, statusId, bookmarked)
            if (bookmarked) {
                mastodonApi.bookmarkStatus(statusId)
            } else {
                mastodonApi.unbookmarkStatus(statusId)
            }
                .onSuccess { eventHub.dispatch(BookmarkEvent(statusId, bookmarked)) }
                .onFailure { statusDao.setBookmarked(pachliAccountId, statusId, !bookmarked) }
                .mapEither({ it.body.asModel() }, { StatusActionError.Bookmark(it) })
        }
    }.await()

    /**
     * Sets the favourite state of [statusId] to [favourited].
     *
     * Sends [FavoriteEvent] if successful.
     *
     * @param pachliAccountId
     * @param statusId ID of the status to affect.
     * @param favourited New favourite state.
     */
    suspend fun favourite(
        pachliAccountId: Long,
        statusId: String,
        favourited: Boolean,
    ): Result<Status, StatusActionError.Favourite> = externalScope.async {
        transactionProvider {
            statusDao.setFavourited(pachliAccountId, statusId, favourited)
            if (favourited) {
                mastodonApi.favouriteStatus(statusId)
            } else {
                mastodonApi.unfavouriteStatus(statusId)
            }
                .onSuccess { eventHub.dispatch(FavoriteEvent(statusId, favourited)) }
                .onFailure { statusDao.setFavourited(pachliAccountId, statusId, !favourited) }
                .mapEither({ it.body.asModel() }, { StatusActionError.Favourite(it) })
        }
    }.await()

    /**
     * Sets the reblog state of [statusId] to [reblogged].
     *
     * Sends [ReblogEvent] if successful.
     *
     * @param pachliAccountId
     * @param statusId ID of the status to affect.
     * @param reblogged New reblog state.
     */
    suspend fun reblog(
        pachliAccountId: Long,
        statusId: String,
        reblogged: Boolean,
    ): Result<Status, StatusActionError.Reblog> = externalScope.async {
        transactionProvider {
            statusDao.setReblogged(pachliAccountId, statusId, reblogged)
            if (reblogged) {
                mastodonApi.reblogStatus(statusId)
            } else {
                mastodonApi.unreblogStatus(statusId)
            }
                .onSuccess { eventHub.dispatch(ReblogEvent(statusId, reblogged)) }
                .onFailure { statusDao.setReblogged(pachliAccountId, statusId, !reblogged) }
                .mapEither({ it.body.asModel() }, { StatusActionError.Reblog(it) })
        }
    }.await()

    /**
     * Sets the muted state of [statusId] to [muted].
     *
     * Sends [app.pachli.core.eventhub.MuteConversationEvent] if successful.
     *
     * @param pachliAccountId
     * @param statusId ID of the status to affect.
     * @param muted New pinned state.
     */
    suspend fun mute(
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
            .onSuccess { eventHub.dispatch(MuteConversationEvent(pachliAccountId, statusId, muted)) }
            .onFailure { statusDao.setMuted(pachliAccountId, statusId, !muted) }
            .mapEither({ it.body.asModel() }, { StatusActionError.Mute(it) })
    }.await()

    /**
     * Sets the pinned state of [statusId] to [pinned].
     *
     * Sends [PinEvent] if successful.
     *
     * @param pachliAccountId
     * @param statusId ID of the status to affect.
     * @param pinned New pinned state.
     */
    suspend fun pin(
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
            .onSuccess { eventHub.dispatch(PinEvent(statusId, pinned)) }
            .onFailure { statusDao.setPinned(pachliAccountId, statusId, !pinned) }
            .mapEither({ it.body.asModel() }, { StatusActionError.Pin(it) })
    }.await()

    /**
     * Votes [choices] in [pollId] in [statusId],
     *
     * Sends [PollVoteEvent] if successful.
     *
     * @param pachliAccountId
     * @param statusId ID of the status to affect.
     * @param pollId ID of the poll to vote in.
     * @param choices Array of indices of the poll options being voted for.
     */
    suspend fun voteInPoll(
        pachliAccountId: Long,
        statusId: String,
        pollId: String,
        choices: List<Int>,
    ): Result<Poll, StatusActionError.VoteInPoll> = externalScope.async {
        transactionProvider {
            val poll = statusDao.getStatus(pachliAccountId, statusId)?.poll
            poll?.let {
                statusDao.setPoll(pachliAccountId, statusId, poll.votedCopy(choices))
            }

            mastodonApi.voteInPoll(pollId, choices)
                .map { it.body.asModel() }
                .onSuccess { poll ->
                    statusDao.setPoll(pachliAccountId, statusId, poll)
                    eventHub.dispatch(PollVoteEvent(statusId, poll))
                }
                .onFailure { poll?.let { statusDao.setPoll(pachliAccountId, statusId, it) } }
                .mapError { StatusActionError.VoteInPoll(it) }
        }
    }.await()

    /**
     * Sets the expanded state of [statusId] to [expanded].
     */
    suspend fun setExpanded(pachliAccountId: Long, statusId: String, expanded: Boolean) {
        statusDao.setExpanded(
            StatusViewDataExpanded(
                pachliAccountId = pachliAccountId,
                serverId = statusId,
                expanded = expanded,
            ),
        )
    }

    /**
     * Sets the showing state of [statusId] to [contentShowing].
     */
    suspend fun setContentShowing(pachliAccountId: Long, statusId: String, contentShowing: Boolean) {
        statusDao.setContentShowing(
            StatusViewDataContentShowing(
                pachliAccountId = pachliAccountId,
                serverId = statusId,
                contentShowing = contentShowing,
            ),
        )
    }

    /**
     * Sets the content-collapsed state of [statusId] to [contentCollapsed].
     */
    suspend fun setContentCollapsed(pachliAccountId: Long, statusId: String, contentCollapsed: Boolean) {
        statusDao.setContentCollapsed(
            StatusViewDataContentCollapsed(
                pachliAccountId = pachliAccountId,
                serverId = statusId,
                contentCollapsed = contentCollapsed,
            ),
        )
    }

    /**
     * Sets the translation state of [statusId] to [translationState].
     */
    suspend fun setTranslationState(pachliAccountId: Long, statusId: String, translationState: TranslationState) {
        statusDao.setTranslationState(
            StatusViewDataTranslationState(
                pachliAccountId = pachliAccountId,
                serverId = statusId,
                translationState = translationState,
            ),
        )
    }

    suspend fun getStatusViewData(pachliAccountId: Long, statusId: String) = statusDao.getStatusViewData(pachliAccountId, statusId)

    suspend fun getTranslation(pachliAccountId: Long, statusId: String) = translatedStatusDao.getTranslation(pachliAccountId, statusId)
}
