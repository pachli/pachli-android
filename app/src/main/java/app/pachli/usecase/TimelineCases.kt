/* Copyright 2018 charlag
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

package app.pachli.usecase

import app.pachli.components.timeline.CachedTimelineRepository
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.eventhub.BlockEvent
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.MuteConversationEvent
import app.pachli.core.eventhub.MuteEvent
import app.pachli.core.eventhub.PinEvent
import app.pachli.core.eventhub.PollVoteEvent
import app.pachli.core.eventhub.StatusDeletedEvent
import app.pachli.core.network.model.DeletedStatus
import app.pachli.core.network.model.Poll
import app.pachli.core.network.model.Relationship
import app.pachli.core.network.model.Status
import app.pachli.core.network.model.Translation
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.apiresult.ApiResult
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import javax.inject.Inject
import timber.log.Timber

class TimelineCases @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val eventHub: EventHub,
    private val cachedTimelineRepository: CachedTimelineRepository,
) {
    suspend fun muteConversation(statusId: String, mute: Boolean): ApiResult<Status> {
        return if (mute) {
            mastodonApi.muteConversation(statusId)
        } else {
            mastodonApi.unmuteConversation(statusId)
        }.onSuccess {
            eventHub.dispatch(MuteConversationEvent(statusId, mute))
        }
    }

    suspend fun mute(statusId: String, notifications: Boolean, duration: Int?) {
        try {
            mastodonApi.muteAccount(statusId, notifications, duration)
            eventHub.dispatch(MuteEvent(statusId))
        } catch (t: Throwable) {
            Timber.w(t, "Failed to mute account")
        }
    }

    suspend fun block(statusId: String) {
        try {
            mastodonApi.blockAccount(statusId)
            eventHub.dispatch(BlockEvent(statusId))
        } catch (t: Throwable) {
            Timber.w(t, "Failed to block account")
        }
    }

    suspend fun delete(statusId: String): ApiResult<DeletedStatus> {
        return mastodonApi.deleteStatus(statusId)
            .onSuccess { eventHub.dispatch(StatusDeletedEvent(statusId)) }
            .onFailure { Timber.w("Failed to delete status: %s", it) }
    }

    suspend fun pin(statusId: String, pin: Boolean): ApiResult<Status> {
        return if (pin) {
            mastodonApi.pinStatus(statusId)
        } else {
            mastodonApi.unpinStatus(statusId)
        }.onSuccess {
            eventHub.dispatch(PinEvent(statusId, pin))
        }
    }

    suspend fun voteInPoll(statusId: String, pollId: String, choices: List<Int>): ApiResult<Poll> {
        return mastodonApi.voteInPoll(pollId, choices).onSuccess {
            eventHub.dispatch(PollVoteEvent(statusId, it.body))
        }
    }

    suspend fun acceptFollowRequest(accountId: String): ApiResult<Relationship> {
        return mastodonApi.authorizeFollowRequest(accountId)
    }

    suspend fun rejectFollowRequest(accountId: String): ApiResult<Relationship> {
        return mastodonApi.rejectFollowRequest(accountId)
    }

    suspend fun translate(statusViewData: StatusViewData): ApiResult<Translation> {
        return cachedTimelineRepository.translate(statusViewData)
    }

    suspend fun translateUndo(pachliAccountId: Long, statusViewData: StatusViewData) {
        cachedTimelineRepository.translateUndo(statusViewData)
    }

    suspend fun saveRefreshKey(pachliAccountId: Long, statusId: String?) {
        cachedTimelineRepository.saveRefreshKey(pachliAccountId, statusId)
    }
}
