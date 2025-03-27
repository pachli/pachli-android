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
import app.pachli.core.data.repository.StatusRepository
import app.pachli.core.database.dao.TranslatedStatusDao
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
import timber.log.Timber

class TimelineCases @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val eventHub: EventHub,
    private val cachedTimelineRepository: CachedTimelineRepository,
    private val statusRepository: StatusRepository,
    private val translatedStatusDao: TranslatedStatusDao,
    private val translationService: TranslationService,
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
        mastodonApi.muteAccount(statusId, notifications, duration)
            .onSuccess { eventHub.dispatch(MuteEvent(statusId)) }
    }

    suspend fun block(accountId: String) {
        mastodonApi.blockAccount(accountId)
            .onSuccess { eventHub.dispatch(BlockEvent(accountId)) }
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

    suspend fun saveRefreshKey(pachliAccountId: Long, statusId: String?) {
        cachedTimelineRepository.saveRefreshKey(pachliAccountId, statusId)
    }
}
