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
import app.pachli.core.database.model.StatusViewDataEntity
import app.pachli.core.database.model.TranslatedStatusEntity
import app.pachli.core.database.model.TranslationState
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.Poll
import app.pachli.core.model.Status
import app.pachli.core.network.retrofit.apiresult.ApiError
import com.github.michaelbull.result.Result

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

/**
 * Interface for repositories that provide actions on statuses.
 */
interface StatusRepository {
    /**
     * Sets the bookmarked state of [statusId] to [bookmarked].
     *
     * Sends [app.pachli.core.eventhub.BookmarkEvent] if successful.
     *
     * @param pachliAccountId
     * @param statusId ID of the status to affect.
     * @param bookmarked New bookmarked state.
     */
    suspend fun bookmark(
        pachliAccountId: Long,
        statusId: String,
        bookmarked: Boolean,
    ): Result<Status, StatusActionError.Bookmark>

    /**
     * Sets the favourite state of [statusId] to [favourited].
     *
     * Sends [app.pachli.core.eventhub.FavoriteEvent] if successful.
     *
     * @param pachliAccountId
     * @param statusId ID of the status to affect.
     * @param favourited New favourite state.
     */
    suspend fun favourite(
        pachliAccountId: Long,
        statusId: String,
        favourited: Boolean,
    ): Result<Status, StatusActionError.Favourite>

    /**
     * Sets the reblog state of [statusId] to [reblogged].
     *
     * Sends [app.pachli.core.eventhub.ReblogEvent] if successful.
     *
     * @param pachliAccountId
     * @param statusId ID of the status to affect.
     * @param reblogged New reblog state.
     */
    suspend fun reblog(
        pachliAccountId: Long,
        statusId: String,
        reblogged: Boolean,
    ): Result<Status, StatusActionError.Reblog>

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
    ): Result<Status, StatusActionError.Mute>

    /**
     * Sets the pinned state of [statusId] to [pinned].
     *
     * Sends [app.pachli.core.eventhub.PinEvent] if successful.
     *
     * @param pachliAccountId
     * @param statusId ID of the status to affect.
     * @param pinned New pinned state.
     */
    suspend fun pin(
        pachliAccountId: Long,
        statusId: String,
        pinned: Boolean,
    ): Result<Status, StatusActionError.Pin>

    /**
     * Votes [choices] in [pollId] in [statusId],
     *
     * Sends [app.pachli.core.eventhub.PollVoteEvent] if successful.
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
    ): Result<Poll, StatusActionError.VoteInPoll>

    /**
     * Sets the expanded state of [statusId] to [expanded].
     */
    suspend fun setExpanded(pachliAccountId: Long, statusId: String, expanded: Boolean)

    /**
     * Sets the showing state of [statusId] to [contentShowing].
     */
    suspend fun setContentShowing(pachliAccountId: Long, statusId: String, contentShowing: Boolean)

    /**
     * Sets the attachment display decision of [statusId] to [attachmentDisplayAction].
     */
    suspend fun setAttachmentDisplayAction(pachliAccountId: Long, statusId: String, attachmentDisplayAction: AttachmentDisplayAction)

    /**
     * Sets the content-collapsed state of [statusId] to [contentCollapsed].
     */
    suspend fun setContentCollapsed(pachliAccountId: Long, statusId: String, contentCollapsed: Boolean)

    /**
     * Sets the translation state of [statusId] to [translationState].
     */
    suspend fun setTranslationState(pachliAccountId: Long, statusId: String, translationState: TranslationState)

    /**
     * @return Cached view data (null if not present) for [statusId].
     */
    suspend fun getStatusViewData(pachliAccountId: Long, statusId: String): StatusViewDataEntity?

    /**
     * @return Translation (null if not present) for [statusId].
     */
    suspend fun getTranslation(pachliAccountId: Long, statusId: String): TranslatedStatusEntity?
}
