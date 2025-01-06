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
import app.pachli.appstore.BookmarkEvent
import app.pachli.appstore.EventHub
import app.pachli.appstore.FavoriteEvent
import app.pachli.appstore.PinEvent
import app.pachli.appstore.ReblogEvent
import app.pachli.components.timeline.NetworkTimelineRepository
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.model.FilterAction
import app.pachli.core.network.model.Poll
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.usecase.TimelineCases
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
) : TimelineViewModel(
    savedStateHandle,
    timelineCases,
    eventHub,
    accountManager,
    statusDisplayOptionsRepository,
    sharedPreferencesRepository,
) {
    private val modifiedViewData = mutableMapOf<String, StatusViewData>()

    override var statuses: Flow<PagingData<StatusViewData>>

    init {
        statuses = refreshFlow
            .flatMapLatest {
                getStatuses(it.second, initialKey = getInitialKey())
            }.cachedIn(viewModelScope)
    }

    /** @return Flow of statuses that make up the timeline of [timeline] for [account]. */
    private fun getStatuses(
        account: AccountEntity,
        initialKey: String? = null,
    ): Flow<PagingData<StatusViewData>> {
        Timber.d("getStatuses: kind: %s, initialKey: %s", timeline, initialKey)
        return repository.getStatusStream(account, kind = timeline, initialKey = initialKey)
            .map { pagingData ->
                pagingData.map {
                    modifiedViewData[it.id] ?: StatusViewData.from(
                        it,
                        isShowingContent = statusDisplayOptions.value.showSensitiveMedia || !it.actionableStatus.sensitive,
                        isExpanded = statusDisplayOptions.value.openSpoiler,
                        isCollapsed = true,
                    )
                }.filter {
                    shouldFilterStatus(it) != FilterAction.HIDE
                }
            }
    }

    override fun updatePoll(newPoll: Poll, status: StatusViewData) {
        modifiedViewData[status.id] = status.copy(
            status = status.status.copy(poll = newPoll),
        )
        repository.invalidate()
    }

    override fun changeExpanded(pachliAccountId: Long, expanded: Boolean, status: StatusViewData) {
        modifiedViewData[status.id] = status.copy(
            isExpanded = expanded,
        )
        repository.invalidate()
    }

    override fun changeContentShowing(pachliAccountId: Long, isShowing: Boolean, status: StatusViewData) {
        modifiedViewData[status.id] = status.copy(
            isShowingContent = isShowing,
        )
        repository.invalidate()
    }

    override fun changeContentCollapsed(pachliAccountId: Long, isCollapsed: Boolean, status: StatusViewData) {
        Timber.d("changeContentCollapsed: %s", isCollapsed)
        Timber.d("   %s", status.content)
        modifiedViewData[status.id] = status.copy(
            isCollapsed = isCollapsed,
        )
        repository.invalidate()
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
        repository.invalidate()
    }

    override fun handleBookmarkEvent(bookmarkEvent: BookmarkEvent) {
        viewModelScope.launch {
            repository.updateActionableStatusById(bookmarkEvent.statusId) {
                it.copy(bookmarked = bookmarkEvent.bookmark)
            }
        }
        repository.invalidate()
    }

    override fun handlePinEvent(pinEvent: PinEvent) {
        viewModelScope.launch {
            repository.updateActionableStatusById(pinEvent.statusId) {
                it.copy(pinned = pinEvent.pinned)
            }
        }
        repository.invalidate()
    }

    override fun reloadKeepingReadingPosition(pachliAccountId: Long) {
        super.reloadKeepingReadingPosition(pachliAccountId)
        viewModelScope.launch {
            repository.reload()
        }
    }

    override fun reloadFromNewest(pachliAccountId: Long) {
        super.reloadFromNewest(pachliAccountId)
        reloadKeepingReadingPosition(pachliAccountId)
    }

    override fun clearWarning(pachliAccountId: Long, statusViewData: StatusViewData) {
        viewModelScope.launch {
            repository.updateActionableStatusById(statusViewData.actionableId) {
                it.copy(filtered = null)
            }
        }
    }

    override suspend fun invalidate(pachliAccountId: Long) {
        repository.invalidate()
    }
}
