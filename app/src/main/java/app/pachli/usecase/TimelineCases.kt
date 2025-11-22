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
import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.OfflineFirstStatusRepository
import app.pachli.core.data.repository.StatusActionError
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.TranslatedStatusDao
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.RemoteKeyEntity.RemoteKeyKind
import app.pachli.core.database.model.TranslationState
import app.pachli.core.database.model.toEntity
import app.pachli.core.eventhub.BlockEvent
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.MuteEvent
import app.pachli.core.eventhub.StatusDeletedEvent
import app.pachli.core.eventhub.UnfollowEvent
import app.pachli.core.model.Status
import app.pachli.core.model.translation.TranslatedStatus
import app.pachli.core.network.model.DeletedStatus
import app.pachli.core.network.model.Relationship
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.apiresult.ApiError
import app.pachli.core.network.retrofit.apiresult.ApiResponse
import app.pachli.core.network.retrofit.apiresult.ApiResult
import app.pachli.translation.TranslationService
import app.pachli.translation.TranslatorError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

class TimelineCases @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val eventHub: EventHub,
    private val statusRepository: OfflineFirstStatusRepository,
    private val translatedStatusDao: TranslatedStatusDao,
    private val translationService: TranslationService,
    private val remoteKeyDao: RemoteKeyDao,
    private val accountManager: AccountManager,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    suspend fun muteConversation(pachliAccountId: Long, statusId: String, mute: Boolean): Result<Status, StatusActionError.Mute> {
        return statusRepository.mute(pachliAccountId, statusId, mute)
    }

    /**
     * Sends a follow request for [accountId] to the server for [pachliAccountId].
     *
     * On success:
     *
     * - The following relationship is added to [AccountManager].
     *
     * @param pachliAccountId
     * @param accountId ID of account to follow.
     * @param showReblogs If true, show reblogs from this account. Null uses server default.
     * @param notify If true, receive notifications when this account posts. Null uses server default.
     */
    suspend fun followAccount(pachliAccountId: Long, accountId: String, showReblogs: Boolean? = null, notify: Boolean? = null): ApiResult<Relationship> {
        return mastodonApi.followAccount(accountId, showReblogs, notify)
            .onSuccess { accountManager.followAccount(pachliAccountId, accountId) }
    }

    /**
     * Unfollow [accountId].
     *
     * On success:
     *
     * - The following relationship is removed from [AccountManager].
     * - [UnfollowEvent] is dispatched.
     *
     * @param pachliAccountId
     * @param accountID ID of the account to unfollow.
     */
    suspend fun unfollowAccount(pachliAccountId: Long, accountId: String): ApiResult<Relationship> {
        return mastodonApi.unfollowAccount(accountId)
            .onSuccess {
                accountManager.unfollowAccount(pachliAccountId, accountId)
                eventHub.dispatch(UnfollowEvent(pachliAccountId, accountId))
            }
    }

    /**
     * Subscribe to [accountId].
     *
     * @param pachliAccountId
     * @param accountId ID of the account to subscribe to.
     */
    suspend fun subscribeAccount(pachliAccountId: Long, accountId: String): ApiResult<Relationship> {
        return mastodonApi.subscribeAccount(accountId)
    }

    /**
     * Unsubscribe from [accountId].
     *
     * @param pachliAccountId
     * @param accountId ID of the account to unsubscribe from.
     */
    suspend fun unsubscribeAccount(pachliAccountId: Long, accountId: String): ApiResult<Relationship> {
        return mastodonApi.unsubscribeAccount(accountId)
    }

    /**
     * Mute [accountId].
     *
     * On success:
     *
     * - [MuteEvent] is dispatched.
     *
     * @param pachliAccountId
     * @param accountId ID of the account to mute.
     */
    suspend fun muteAccount(pachliAccountId: Long, accountId: String, notifications: Boolean? = null, duration: Int? = null): Result<ApiResponse<Relationship>, ApiError> {
        return mastodonApi.muteAccount(accountId, notifications, duration)
            .onSuccess { eventHub.dispatch(MuteEvent(pachliAccountId, accountId)) }
    }

    /**
     * Unmute [accountId].
     *
     * @param pachliAccountId
     * @param accountId ID of the account to unmute.
     */
    suspend fun unmuteAccount(pachliAccountId: Long, accountId: String): ApiResult<Relationship> {
        return mastodonApi.unmuteAccount(accountId)
    }

    /**
     * Block [accountId].
     *
     * On success:
     *
     * - [BlockEvent] is dispatched.
     *
     * @param pachliAccountId
     * @param accountId ID of the account to block.
     */
    suspend fun blockAccount(pachliAccountId: Long, accountId: String): Result<ApiResponse<Relationship>, ApiError> {
        return mastodonApi.blockAccount(accountId)
            .onSuccess { eventHub.dispatch(BlockEvent(pachliAccountId, accountId)) }
    }

    /**
     * Unblock [accountId].
     *
     * @param pachliAccountId
     * @param accountId ID of the account to unblock.
     */
    suspend fun unblockAccount(pachliAccountId: Long, accountId: String): ApiResult<Relationship> {
        return mastodonApi.unblockAccount(accountId)
    }

    suspend fun delete(statusId: String): ApiResult<DeletedStatus> {
        // Some servers (Pleroma?, see https://github.com/tuskyapp/Tusky/pull/1461) don't
        // return the text of the status when deleting. Work around that by fetching
        // the status source first, and using content from that if necessary.
        val source = mastodonApi.statusSource(statusId)
            .getOrElse { return Err(it) }.body

        return mastodonApi.deleteStatus(statusId)
            .onSuccess { eventHub.dispatch(StatusDeletedEvent(statusId)) }
            .onFailure { Timber.w("Failed to delete status: %s", it) }
            .map {
                if (it.body.isEmpty()) {
                    it.copy(body = it.body.copy(text = source.text, spoilerText = source.spoilerText))
                } else {
                    it
                }
            }
    }

    suspend fun acceptFollowRequest(accountId: String): ApiResult<Relationship> {
        return mastodonApi.authorizeFollowRequest(accountId)
    }

    suspend fun rejectFollowRequest(accountId: String): ApiResult<Relationship> {
        return mastodonApi.rejectFollowRequest(accountId)
    }

    suspend fun translate(statusViewData: IStatusViewData): Result<TranslatedStatus, TranslatorError> {
        statusRepository.setTranslationState(statusViewData.pachliAccountId, statusViewData.actionableId, TranslationState.TRANSLATING)
        return translationService.translate(statusViewData)
            .onSuccess {
                translatedStatusDao.upsert(
                    it.toEntity(statusViewData.pachliAccountId, statusViewData.actionableId),
                )
                statusRepository.setTranslationState(statusViewData.pachliAccountId, statusViewData.actionableId, TranslationState.SHOW_TRANSLATION)
            }.onFailure {
                // Reset the translation state
                statusRepository.setTranslationState(statusViewData.pachliAccountId, statusViewData.actionableId, TranslationState.SHOW_ORIGINAL)
            }
    }

    suspend fun translateUndo(statusViewData: IStatusViewData) {
        statusRepository.setTranslationState(statusViewData.pachliAccountId, statusViewData.actionableId, TranslationState.SHOW_ORIGINAL)
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

    suspend fun detachQuote(pachliAccountId: Long, quoteId: String, parentId: String): Result<Status, StatusActionError.RevokeQuote> {
        return statusRepository.detachQuote(pachliAccountId, quoteId, parentId)
    }
}
