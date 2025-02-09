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
import app.pachli.core.eventhub.BookmarkEvent
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.FavoriteEvent
import app.pachli.core.eventhub.PollVoteEvent
import app.pachli.core.eventhub.ReblogEvent
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.apiresult.ApiError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapEither
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

    /** Voting in a poll failed. */
    data class VoteInPoll(override val error: ApiError) : StatusActionError(error)
}

class StatusRepository @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val mastodonApi: MastodonApi,
    private val statusDao: StatusDao,
    private val eventHub: EventHub,
) {
    suspend fun bookmark(
        pachliAccountId: Long,
        statusId: String,
        bookmarked: Boolean,
    ): Result<Unit, StatusActionError.Bookmark> = externalScope.async {
        statusDao.setBookmarked(pachliAccountId, statusId, bookmarked)
        return@async if (bookmarked) {
            mastodonApi.bookmarkStatus(statusId)
        } else {
            mastodonApi.unbookmarkStatus(statusId)
        }
            .onSuccess { eventHub.dispatch(BookmarkEvent(statusId, bookmarked)) }
            .onFailure { statusDao.setBookmarked(pachliAccountId, statusId, !bookmarked) }
            .mapEither({ }, { StatusActionError.Bookmark(it) })
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
    ): Result<Unit, StatusActionError.Favourite> = externalScope.async {
        statusDao.setFavourited(pachliAccountId, statusId, favourited)
        return@async if (favourited) {
            mastodonApi.favouriteStatus(statusId)
        } else {
            mastodonApi.unfavouriteStatus(statusId)
        }
            .onSuccess { eventHub.dispatch(FavoriteEvent(statusId, favourited)) }
            .onFailure { statusDao.setFavourited(pachliAccountId, statusId, !favourited) }
            .mapEither({}, { StatusActionError.Favourite(it) })
    }.await()

    /**
     * Sets the reblog state of [statusId] to [reblogged].
     *
     * Sends [ReblogEvent] if successful.
     *
     * @param pachliAccountId
     * @param statusId ID of the status to affect.
     * @param reblogged New bookmark state.
     */
    suspend fun reblog(
        pachliAccountId: Long,
        statusId: String,
        reblogged: Boolean,
    ): Result<Unit, StatusActionError.Reblog> = externalScope.async {
        statusDao.setReblogged(pachliAccountId, statusId, reblogged)
        return@async if (reblogged) {
            mastodonApi.reblogStatus(statusId)
        } else {
            mastodonApi.unreblogStatus(statusId)
        }
            .onSuccess { eventHub.dispatch(ReblogEvent(statusId, reblogged)) }
            .onFailure { statusDao.setReblogged(pachliAccountId, statusId, !reblogged) }
            .mapEither({}, { StatusActionError.Reblog(it) })
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
    ): Result<Unit, StatusActionError.VoteInPoll> = externalScope.async {
        mastodonApi.voteInPoll(pollId, choices)
            .onSuccess { poll ->
                statusDao.setVoted(pachliAccountId, statusId, poll.body)
                eventHub.dispatch(PollVoteEvent(statusId, poll.body))
            }
            .onFailure { return@async Err(StatusActionError.VoteInPoll(it)) }

        return@async Ok(Unit)
    }.await()
}
