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
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import app.pachli.components.timeline.CachedTimelineRepository
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.database.model.TimelineStatusWithAccount
import app.pachli.core.eventhub.BookmarkEvent
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.FavoriteEvent
import app.pachli.core.eventhub.PinEvent
import app.pachli.core.eventhub.ReblogEvent
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.FilterAction
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.usecase.TimelineCases
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * TimelineViewModel that caches all statuses in a local database
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CachedTimelineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: CachedTimelineRepository,
    timelineCases: TimelineCases,
    eventHub: EventHub,
    accountManager: AccountManager,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
    sharedPreferencesRepository: SharedPreferencesRepository,
) : TimelineViewModel<TimelineStatusWithAccount, CachedTimelineRepository>(
    savedStateHandle,
    timelineCases,
    eventHub,
    accountManager,
    repository,
    statusDisplayOptionsRepository,
    sharedPreferencesRepository,
) {
    override val statuses = pachliAccountFlow.distinctUntilChangedBy { it.id }.flatMapLatest { pachliAccount ->
        repository.getStatusStream(pachliAccount.id, timeline).map { pagingData ->
            pagingData
                .map { Pair(it, shouldFilterStatus(it)) }
                .filter { it.second != FilterAction.HIDE }
                .map { (timelineStatusWithAccount, contentFilterAction) ->
                    StatusViewData.from(
                        pachliAccountId = pachliAccount.id,
                        timelineStatusWithAccount,
                        isExpanded = pachliAccount.entity.alwaysOpenSpoiler,
                        contentFilterAction = contentFilterAction,
                        attachmentDisplayAction = getAttachmentDisplayAction(
                            timelineStatusWithAccount,
                            pachliAccount.entity.alwaysShowSensitiveMedia,
                            timelineStatusWithAccount.viewData?.attachmentDisplayAction,
                        ),
                    )
                }
        }
    }.cachedIn(viewModelScope)

    override fun removeAllByAccountId(pachliAccountId: Long, accountId: String) {
        viewModelScope.launch {
            repository.removeAllByAccountId(pachliAccountId, accountId)
        }
    }

    override fun removeAllByInstance(pachliAccountId: Long, instance: String) {
        viewModelScope.launch {
            repository.removeAllByInstance(pachliAccountId, instance)
        }
    }

    override fun clearWarning(statusViewData: StatusViewData) {
        viewModelScope.launch {
            repository.clearStatusWarning(statusViewData.pachliAccountId, statusViewData.actionableId)
        }
    }

    override fun removeStatusWithId(statusId: String) {
        viewModelScope.launch {
            repository.removeStatusWithId(statusId)
        }
    }

    override fun handleReblogEvent(reblogEvent: ReblogEvent) {
        // handled by CacheUpdater
    }

    override fun handleFavEvent(favEvent: FavoriteEvent) {
        // handled by CacheUpdater
    }

    override fun handleBookmarkEvent(bookmarkEvent: BookmarkEvent) {
        // handled by CacheUpdater
    }

    override fun handlePinEvent(pinEvent: PinEvent) {
        // handled by CacheUpdater
    }

    override fun onChangeExpanded(isExpanded: Boolean, statusViewData: StatusViewData) {
        viewModelScope.launch {
            repository.setExpanded(statusViewData.pachliAccountId, statusViewData.id, isExpanded)
        }
    }

    override fun onChangeAttachmentDisplayAction(viewData: StatusViewData, newAction: AttachmentDisplayAction) {
        viewModelScope.launch {
            repository.setAttachmentDisplayAction(viewData.pachliAccountId, viewData.actionableId, newAction)
        }
    }

    override fun onContentCollapsed(isCollapsed: Boolean, statusViewData: StatusViewData) {
        viewModelScope.launch {
            repository.setContentCollapsed(statusViewData.pachliAccountId, statusViewData.id, isCollapsed)
        }
    }

    override suspend fun onBookmark(action: FallibleStatusAction.Bookmark) = repository.bookmark(
        action.statusViewData.pachliAccountId,
        action.statusViewData.actionableId,
        action.state,
    )

    override suspend fun onFavourite(action: FallibleStatusAction.Favourite) = repository.favourite(
        action.statusViewData.pachliAccountId,
        action.statusViewData.actionableId,
        action.state,
    )

    override suspend fun onReblog(action: FallibleStatusAction.Reblog) = repository.reblog(
        action.statusViewData.pachliAccountId,
        action.statusViewData.actionableId,
        action.state,
    )

    override suspend fun onVoteInPoll(action: FallibleStatusAction.VoteInPoll) = repository.voteInPoll(
        action.statusViewData.pachliAccountId,
        action.statusViewData.actionableId,
        action.poll.id,
        action.choices,
    )

    override suspend fun onTranslate(action: FallibleStatusAction.Translate) = timelineCases.translate(action.statusViewData)

    override suspend fun onUndoTranslate(action: InfallibleStatusAction.TranslateUndo) = timelineCases.translateUndo(action.statusViewData)

    override suspend fun invalidate(pachliAccountId: Long) {
        repository.invalidate(pachliAccountId)
    }
}
