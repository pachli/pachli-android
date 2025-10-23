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

import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import app.pachli.components.timeline.NetworkTimelineRepository
import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.data.model.StatusViewDataQ
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.StatusActionError
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.database.dao.TimelineStatusWithAccount
import app.pachli.core.database.model.TranslationState
import app.pachli.core.eventhub.BookmarkEvent
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.FavoriteEvent
import app.pachli.core.eventhub.PinEvent
import app.pachli.core.eventhub.ReblogEvent
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.FilterAction
import app.pachli.core.model.Poll
import app.pachli.core.model.Status
import app.pachli.core.model.Timeline
import app.pachli.core.model.translation.TranslatedStatus
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.translation.TranslatorError
import app.pachli.usecase.TimelineCases
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * TimelineViewModel that caches all statuses in an in-memory list
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = NetworkTimelineViewModel.Factory::class)
open class NetworkTimelineViewModel @AssistedInject constructor(
    @Assisted("timeline") timeline: Timeline,
    repository: NetworkTimelineRepository,
    timelineCases: TimelineCases,
    eventHub: EventHub,
    accountManager: AccountManager,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
    sharedPreferencesRepository: SharedPreferencesRepository,
) : TimelineViewModel<TimelineStatusWithAccount, NetworkTimelineRepository>(
    timeline,
    timelineCases,
    eventHub,
    accountManager,
    repository,
    statusDisplayOptionsRepository,
    sharedPreferencesRepository,
) {
    override val statuses = pachliAccountFlow.distinctUntilChangedBy { it.id }.flatMapLatest { pachliAccount ->
        repository.getStatusStream(pachliAccount.id, timeline = timeline).map { pagingData ->
            pagingData
                .map { Pair(it, shouldFilterStatus(it.timelineStatus)) }
                .filter { it.second != FilterAction.HIDE }
                .map { (tsq, contentFilterAction) ->
                    StatusViewDataQ.from(
                        pachliAccount.id,
                        tsq,
                        isExpanded = statusDisplayOptions.value.openSpoiler,
                        contentFilterAction = contentFilterAction,
                        showSensitiveMedia = pachliAccount.entity.alwaysShowSensitiveMedia,
                        filterContext = filterContext,
                    )
                }
        }
    }.cachedIn(viewModelScope)

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

    override fun clearWarning(statusViewData: IStatusViewData) {
        viewModelScope.launch {
            repository.updateActionableStatusById(statusViewData.statusId) {
                it.copy(filtered = null)
            }
        }
    }

    override fun onChangeExpanded(isExpanded: Boolean, statusViewData: IStatusViewData) {
        viewModelScope.launch {
            repository.setExpanded(statusViewData.pachliAccountId, statusViewData.actionableId, isExpanded)
        }
    }

    override fun onChangeAttachmentDisplayAction(viewData: IStatusViewData, newAction: AttachmentDisplayAction) {
        viewModelScope.launch {
            repository.setAttachmentDisplayAction(viewData.pachliAccountId, viewData.actionableId, newAction)
            repository.invalidate()
        }
    }

    override fun onContentCollapsed(isCollapsed: Boolean, statusViewData: IStatusViewData) {
        viewModelScope.launch {
            repository.setContentCollapsed(statusViewData.pachliAccountId, statusViewData.actionableId, isCollapsed)
        }
    }

    override suspend fun onBookmark(action: FallibleStatusAction.Bookmark): Result<Status, StatusActionError.Bookmark> = repository.bookmark(
        action.statusViewData.pachliAccountId,
        action.statusViewData.actionableId,
        action.state,
    )

    override suspend fun onFavourite(action: FallibleStatusAction.Favourite): Result<Status, StatusActionError.Favourite> = repository.favourite(
        action.statusViewData.pachliAccountId,
        action.statusViewData.actionableId,
        action.state,
    )

    override suspend fun onReblog(action: FallibleStatusAction.Reblog): Result<Status, StatusActionError.Reblog> = repository.reblog(
        action.statusViewData.pachliAccountId,
        action.statusViewData.actionableId,
        action.state,
    )

    override suspend fun onVoteInPoll(action: FallibleStatusAction.VoteInPoll): Result<Poll, StatusActionError.VoteInPoll> {
        repository.updateActionableStatusById(action.statusViewData.actionableId) {
            it.copy(poll = action.poll.votedCopy(action.choices))
        }

        return repository.voteInPoll(
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
        repository.setTranslationState(
            action.statusViewData.pachliAccountId,
            action.statusViewData.actionableId,
            TranslationState.TRANSLATING,
        )
        repository.invalidate()

        return timelineCases.translate(action.statusViewData)
            .also { repository.invalidate() }
    }

    override suspend fun onUndoTranslate(action: InfallibleStatusAction.TranslateUndo) {
        repository.setTranslationState(
            action.statusViewData.pachliAccountId,
            action.statusViewData.actionableId,
            TranslationState.SHOW_ORIGINAL,
        )
        repository.invalidate()
    }

    override suspend fun invalidate(pachliAccountId: Long) {
        repository.invalidate()
    }

    @AssistedFactory
    interface Factory {
        /** Creates [NetworkTimelineViewModel] for [timeline]. */
        fun create(
            @Assisted("timeline") timeline: Timeline,
        ): NetworkTimelineViewModel
    }
}
