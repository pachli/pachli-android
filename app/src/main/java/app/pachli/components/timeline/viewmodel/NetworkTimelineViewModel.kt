/*
 * Copyright 2023 Pachli Association
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

package app.pachli.components.timeline.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import app.pachli.components.timeline.NetworkTimelineRepository
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.StatusActionError
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.data.repository.StatusRepository
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.TranslationState
import app.pachli.core.database.model.toEntity
import app.pachli.core.eventhub.BookmarkEvent
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.FavoriteEvent
import app.pachli.core.eventhub.PinEvent
import app.pachli.core.eventhub.ReblogEvent
import app.pachli.core.model.FilterAction
import app.pachli.core.model.translation.TranslatedStatus
import app.pachli.core.network.model.Poll
import app.pachli.core.network.model.Status
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.translation.TranslatorError
import app.pachli.usecase.TimelineCases
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * TimelineViewModel that caches all statuses in an in-memory list
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NetworkTimelineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NetworkTimelineRepository,
    timelineCases: TimelineCases,
    eventHub: EventHub,
    accountManager: AccountManager,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
    sharedPreferencesRepository: SharedPreferencesRepository,
    statusRepository: StatusRepository,
) : TimelineViewModel<Status>(
    savedStateHandle,
    timelineCases,
    eventHub,
    accountManager,
    repository,
    statusDisplayOptionsRepository,
    sharedPreferencesRepository,
    statusRepository,
) {
    private val modifiedViewData = mutableMapOf<String, StatusViewData>()

    override var statuses: Flow<PagingData<StatusViewData>>

    init {
        statuses = accountFlow
            .flatMapLatest { getStatuses(it.data!!) }.cachedIn(viewModelScope)
    }

    /** @return Flow of statuses that make up the timeline of [timeline] for [account]. */
    private suspend fun getStatuses(
        account: AccountEntity,
    ): Flow<PagingData<StatusViewData>> {
        Timber.d("getStatuses: kind: %s", timeline)
        return repository.getStatusStream(account, kind = timeline)
            .map { pagingData ->
                pagingData.map {
                    val existingViewData = statusRepository.getStatusViewData(account.id, it.actionableId)
                    val existingTranslation = statusRepository.getTranslation(account.id, it.actionableId)

                    modifiedViewData[it.actionableId] ?: StatusViewData.from(
                        pachliAccountId = account.id,
                        it,
                        isShowingContent = existingViewData?.contentShowing ?: statusDisplayOptions.value.showSensitiveMedia || !it.actionableStatus.sensitive,
                        isExpanded = existingViewData?.expanded ?: statusDisplayOptions.value.openSpoiler,
                        isCollapsed = existingViewData?.contentCollapsed ?: true,
                        contentFilterAction = shouldFilterStatus(it),
                        translationState = existingViewData?.translationState ?: TranslationState.SHOW_ORIGINAL,
                        translation = existingTranslation,
                    )
                }.filter { it.contentFilterAction != FilterAction.HIDE }
            }
    }

    override fun removeAllByAccountId(pachliAccountId: Long, accountId: String) {
        viewModelScope.launch {
            repository.removeAllByAccountId(accountId)
        }
    }

    override fun removeAllByInstance(pachliAccountId: Long, instance: String) {
        viewModelScope.launch {
            repository.removeAllByInstance(instance)
        }
    }

    override fun removeStatusWithId(id: String) {
        viewModelScope.launch {
            repository.removeStatusWithId(id)
        }
    }

    override fun handleReblogEvent(reblogEvent: ReblogEvent) {
        viewModelScope.launch {
            repository.updateStatusById(reblogEvent.statusId) {
                it.copy(reblogged = reblogEvent.reblog)
            }
        }
    }

    override fun handleFavEvent(favEvent: FavoriteEvent) {
        viewModelScope.launch {
            repository.updateActionableStatusById(favEvent.statusId) {
                it.copy(favourited = favEvent.favourite)
            }
        }
    }

    override fun handleBookmarkEvent(bookmarkEvent: BookmarkEvent) {
        viewModelScope.launch {
            repository.updateActionableStatusById(bookmarkEvent.statusId) {
                it.copy(bookmarked = bookmarkEvent.bookmark)
            }
        }
    }

    override fun handlePinEvent(pinEvent: PinEvent) {
        viewModelScope.launch {
            repository.updateActionableStatusById(pinEvent.statusId) {
                it.copy(pinned = pinEvent.pinned)
            }
        }
    }

    override fun clearWarning(statusViewData: StatusViewData) {
        viewModelScope.launch {
            repository.updateActionableStatusById(statusViewData.actionableId) {
                it.copy(filtered = null)
            }
        }
    }

    override fun onChangeExpanded(isExpanded: Boolean, statusViewData: StatusViewData) {
        viewModelScope.launch {
            statusRepository.setExpanded(statusViewData.pachliAccountId, statusViewData.actionableId, isExpanded)
            modifiedViewData[statusViewData.actionableId] = statusViewData.copy(isExpanded = isExpanded)
            repository.invalidate()
        }
    }

    override fun onChangeContentShowing(isShowing: Boolean, statusViewData: StatusViewData) {
        viewModelScope.launch {
            statusRepository.setContentShowing(statusViewData.pachliAccountId, statusViewData.actionableId, isShowing)
            modifiedViewData[statusViewData.id] = statusViewData.copy(isShowingContent = isShowing)
            repository.invalidate()
        }
    }

    override fun onContentCollapsed(isCollapsed: Boolean, statusViewData: StatusViewData) {
        viewModelScope.launch {
            statusRepository.setContentCollapsed(statusViewData.pachliAccountId, statusViewData.actionableId, isCollapsed)
            modifiedViewData[statusViewData.actionableId] = statusViewData.copy(isCollapsed = isCollapsed)
            repository.invalidate()
        }
    }

    override suspend fun onBookmark(action: FallibleStatusAction.Bookmark): Result<Status, StatusActionError.Bookmark> {
        repository.updateActionableStatusById(action.statusViewData.actionableId) {
            it.copy(bookmarked = action.state)
        }

        return statusRepository.bookmark(
            action.statusViewData.pachliAccountId,
            action.statusViewData.actionableId,
            action.state,
        ).onFailure {
            repository.updateActionableStatusById(action.statusViewData.actionableId) {
                it.copy(bookmarked = !action.state)
            }
        }
    }

    override suspend fun onFavourite(action: FallibleStatusAction.Favourite): Result<Status, StatusActionError.Favourite> {
        repository.updateActionableStatusById(action.statusViewData.actionableId) {
            it.copy(favourited = action.state)
        }

        return statusRepository.favourite(
            action.statusViewData.pachliAccountId,
            action.statusViewData.actionableId,
            action.state,
        ).onFailure {
            repository.updateActionableStatusById(action.statusViewData.actionableId) {
                it.copy(favourited = !action.state)
            }
        }
    }

    override suspend fun onReblog(action: FallibleStatusAction.Reblog): Result<Status, StatusActionError.Reblog> {
        repository.updateActionableStatusById(action.statusViewData.actionableId) {
            it.copy(reblogged = action.state)
        }

        return statusRepository.reblog(
            action.statusViewData.pachliAccountId,
            action.statusViewData.actionableId,
            action.state,
        ).onFailure {
            repository.updateActionableStatusById(action.statusViewData.actionableId) {
                it.copy(reblogged = !action.state)
            }
        }
    }

    override suspend fun onVoteInPoll(action: FallibleStatusAction.VoteInPoll): Result<Poll, StatusActionError.VoteInPoll> {
        repository.updateActionableStatusById(action.statusViewData.actionableId) {
            it.copy(poll = action.poll.votedCopy(action.choices))
        }

        return statusRepository.voteInPoll(
            action.statusViewData.pachliAccountId,
            action.statusViewData.actionableId,
            action.poll.id,
            action.choices,
        ).onFailure {
            repository.updateActionableStatusById(action.statusViewData.actionableId) {
                it.copy(poll = action.poll)
            }
        }
    }

    override suspend fun onTranslate(action: FallibleStatusAction.Translate): Result<TranslatedStatus, TranslatorError> {
        modifiedViewData[action.statusViewData.actionableId] = action.statusViewData.copy(
            translationState = TranslationState.TRANSLATING,
        )
        repository.invalidate()

        return timelineCases.translate(action.statusViewData)
            .onSuccess {
                modifiedViewData[action.statusViewData.actionableId] = action.statusViewData.copy(
                    translation = it.toEntity(action.statusViewData.pachliAccountId, action.statusViewData.actionableId),
                    translationState = TranslationState.SHOW_TRANSLATION,
                )
                repository.invalidate()
            }
            .onFailure {
                modifiedViewData[action.statusViewData.actionableId] = action.statusViewData.copy(
                    translationState = TranslationState.SHOW_ORIGINAL,
                )
                repository.invalidate()
            }
    }

    override suspend fun onUndoTranslate(action: InfallibleStatusAction.TranslateUndo) {
        modifiedViewData[action.statusViewData.actionableId] = action.statusViewData.copy(
            translationState = TranslationState.SHOW_ORIGINAL,
        )
        repository.invalidate()
    }

    override suspend fun invalidate(pachliAccountId: Long) {
        repository.invalidate()
    }
}
