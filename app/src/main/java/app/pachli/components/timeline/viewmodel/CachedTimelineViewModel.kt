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

import android.content.Context
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
import app.pachli.components.timeline.CachedTimelineRepository
import app.pachli.core.accounts.AccountManager
import app.pachli.core.data.repository.ContentFiltersRepository
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.network.model.FilterAction
import app.pachli.core.network.model.Poll
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.usecase.TimelineCases
import app.pachli.viewdata.StatusViewData
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * TimelineViewModel that caches all statuses in a local database
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CachedTimelineViewModel @Inject constructor(
    @ApplicationContext context: Context,
    savedStateHandle: SavedStateHandle,
    private val repository: CachedTimelineRepository,
    timelineCases: TimelineCases,
    eventHub: EventHub,
    contentFiltersRepository: ContentFiltersRepository,
    accountManager: AccountManager,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
    sharedPreferencesRepository: SharedPreferencesRepository,
    private val moshi: Moshi,
) : TimelineViewModel(
    context,
    savedStateHandle,
    timelineCases,
    eventHub,
    contentFiltersRepository,
    accountManager,
    statusDisplayOptionsRepository,
    sharedPreferencesRepository,
) {

    override var statuses: Flow<PagingData<StatusViewData>>

    init {
        readingPositionId = activeAccount.lastVisibleHomeTimelineStatusId

        statuses = reload.flatMapLatest {
            getStatuses(initialKey = getInitialKey())
        }.cachedIn(viewModelScope)
    }

    /** @return Flow of statuses that make up the timeline of [timeline] */
    private fun getStatuses(
        initialKey: String? = null,
    ): Flow<PagingData<StatusViewData>> {
        Timber.d("getStatuses: kind: %s, initialKey: %s", timeline, initialKey)
        return repository.getStatusStream(kind = timeline, initialKey = initialKey)
            .map { pagingData ->
                pagingData
                    .map {
                        StatusViewData.from(
                            it,
                            moshi,
                            isExpanded = activeAccount.alwaysOpenSpoiler,
                            isShowingContent = activeAccount.alwaysShowSensitiveMedia,
                        )
                    }
                    .filter { shouldFilterStatus(it) != FilterAction.HIDE }
            }
    }

    override fun updatePoll(newPoll: Poll, status: StatusViewData) {
        // handled by CacheUpdater
    }

    override fun changeExpanded(expanded: Boolean, status: StatusViewData) {
        viewModelScope.launch {
            repository.saveStatusViewData(status.copy(isExpanded = expanded))
        }
    }

    override fun changeContentShowing(isShowing: Boolean, status: StatusViewData) {
        viewModelScope.launch {
            repository.saveStatusViewData(status.copy(isShowingContent = isShowing))
        }
    }

    override fun changeContentCollapsed(isCollapsed: Boolean, status: StatusViewData) {
        viewModelScope.launch {
            repository.saveStatusViewData(status.copy(isCollapsed = isCollapsed))
        }
    }

    override fun removeAllByAccountId(accountId: String) {
        viewModelScope.launch {
            repository.removeAllByAccountId(accountId)
        }
    }

    override fun removeAllByInstance(instance: String) {
        viewModelScope.launch {
            repository.removeAllByInstance(instance)
        }
    }

    override fun clearWarning(statusViewData: StatusViewData) {
        viewModelScope.launch {
            repository.clearStatusWarning(statusViewData.actionableId)
        }
    }

    override fun removeStatusWithId(id: String) {
        // handled by CacheUpdater
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

    override fun reloadKeepingReadingPosition() {
        super.reloadKeepingReadingPosition()
        viewModelScope.launch {
            repository.clearAndReload()
        }
    }

    override fun reloadFromNewest() {
        super.reloadFromNewest()
        viewModelScope.launch {
            repository.clearAndReloadFromNewest()
        }
    }

    override suspend fun invalidate() {
        repository.invalidate()
    }
}
