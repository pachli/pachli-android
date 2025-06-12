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

import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.data.repository.StatusRepository
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.TranslatedStatusDao
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.RemoteKeyEntity.RemoteKeyKind
import app.pachli.core.database.model.TranslationState
import app.pachli.core.database.model.toEntity
import app.pachli.core.eventhub.BlockEvent
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.MuteConversationEvent
import app.pachli.core.eventhub.MuteEvent
import app.pachli.core.eventhub.StatusDeletedEvent
import app.pachli.core.model.translation.TranslatedStatus
import app.pachli.core.network.model.DeletedStatus
import app.pachli.core.network.model.Relationship
import app.pachli.core.network.model.Status
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.apiresult.ApiResult
import app.pachli.translation.TranslationService
import app.pachli.translation.TranslatorError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

class TimelineCases @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val eventHub: EventHub,
    private val statusRepository: StatusRepository,
    private val translatedStatusDao: TranslatedStatusDao,
    private val translationService: TranslationService,
    private val remoteKeyDao: RemoteKeyDao,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    suspend fun muteConversation(pachliAccountId: Long, statusId: String, mute: Boolean): ApiResult<Status> {
        return if (mute) {
            mastodonApi.muteConversation(statusId)
        } else {
            mastodonApi.unmuteConversation(statusId)
        }.onSuccess {
            eventHub.dispatch(MuteConversationEvent(pachliAccountId, statusId, mute))
        }
    }

    suspend fun mute(pachliAccountId: Long, statusId: String, notifications: Boolean, duration: Int?) {
        mastodonApi.muteAccount(statusId, notifications, duration)
            .onSuccess { eventHub.dispatch(MuteEvent(pachliAccountId, statusId)) }
    }

    suspend fun block(pachliAccountId: Long, accountId: String) {
        mastodonApi.blockAccount(accountId)
            .onSuccess { eventHub.dispatch(BlockEvent(pachliAccountId, accountId)) }
    }

    suspend fun delete(statusId: String): ApiResult<DeletedStatus> {
        return mastodonApi.deleteStatus(statusId)
            .onSuccess { eventHub.dispatch(StatusDeletedEvent(statusId)) }
            .onFailure { Timber.w("Failed to delete status: %s", it) }
    }

    suspend fun acceptFollowRequest(accountId: String): ApiResult<Relationship> {
        return mastodonApi.authorizeFollowRequest(accountId)
    }

    suspend fun rejectFollowRequest(accountId: String): ApiResult<Relationship> {
        return mastodonApi.rejectFollowRequest(accountId)
    }

    suspend fun translate(statusViewData: StatusViewData): Result<TranslatedStatus, TranslatorError> {
        statusRepository.setTranslationState(statusViewData.pachliAccountId, statusViewData.id, TranslationState.TRANSLATING)
        val translation = translationService.translate(statusViewData)
        translation.onSuccess {
            translatedStatusDao.upsert(
                it.toEntity(statusViewData.pachliAccountId, statusViewData.actionableId),
            )
            statusRepository.setTranslationState(statusViewData.pachliAccountId, statusViewData.id, TranslationState.SHOW_TRANSLATION)
        }.onFailure {
            // Reset the translation state
            statusRepository.setTranslationState(statusViewData.pachliAccountId, statusViewData.id, TranslationState.SHOW_ORIGINAL)
        }

        return translation
    }

    suspend fun translateUndo(statusViewData: StatusViewData) {
        statusRepository.setTranslationState(statusViewData.pachliAccountId, statusViewData.id, TranslationState.SHOW_ORIGINAL)
    }

    /**
     * @param pachliAccountId
     * @param remoteKeyTimelineId The timeline's [Timeline.remoteKeyTimelineId][app.pachli.core.model.Timeline.remoteKeyTimelineId].
     * @return The most recent saved status ID to use in a refresh. Null if not set, or the refresh
     * should fetch the latest statuses.
     * @see saveRefreshStatusId
     */
    suspend fun getRefreshStatusId(pachliAccountId: Long, remoteKeyTimelineId: String): String? {
        return remoteKeyDao.getRefreshKey(pachliAccountId, remoteKeyTimelineId)
    }

    /**
     * Saves the ID of the status that future refreshes will try and restore
     * from.
     *
     * @param pachliAccountId
     * @param remoteKeyTimelineId The timeline's [Timeline.remoteKeyTimelineId][app.pachli.core.model.Timeline.remoteKeyTimelineId].
     * @param statusId Status ID to restore from. Null indicates the refresh should
     * refresh the newest statuses.
     * @see getRefreshStatusId
     */
    fun saveRefreshStatusId(pachliAccountId: Long, remoteKeyTimelineId: String, statusId: String?) = externalScope.launch {
        remoteKeyDao.upsert(
            RemoteKeyEntity(pachliAccountId, remoteKeyTimelineId, RemoteKeyKind.REFRESH, statusId),
        )
    }
}
