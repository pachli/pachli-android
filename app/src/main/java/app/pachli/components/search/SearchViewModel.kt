/* Copyright 2021 Tusky Contributors
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

package app.pachli.components.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import app.pachli.components.search.adapter.SearchPagingSourceFactory
import app.pachli.core.accounts.AccountManager
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.network.model.DeletedStatus
import app.pachli.core.network.model.Poll
import app.pachli.core.network.model.Status
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.usecase.TimelineCases
import app.pachli.viewdata.StatusViewData
import at.connyduck.calladapter.networkresult.NetworkResult
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.onFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class SearchViewModel @Inject constructor(
    mastodonApi: MastodonApi,
    private val timelineCases: TimelineCases,
    private val accountManager: AccountManager,
) : ViewModel() {

    var currentQuery: String = ""
    var currentSearchFieldContent: String? = null

    val activeAccount: AccountEntity?
        get() = accountManager.activeAccount

    val mediaPreviewEnabled = activeAccount?.mediaPreviewEnabled ?: false
    val alwaysShowSensitiveMedia = activeAccount?.alwaysShowSensitiveMedia ?: false
    val alwaysOpenSpoiler = activeAccount?.alwaysOpenSpoiler ?: false

    private val loadedStatuses: MutableList<StatusViewData> = mutableListOf()

    private val statusesPagingSourceFactory = SearchPagingSourceFactory(mastodonApi, SearchType.Status, loadedStatuses) {
        it.statuses.map { status ->
            StatusViewData.from(
                status,
                isShowingContent = alwaysShowSensitiveMedia || !status.actionableStatus.sensitive,
                isExpanded = alwaysOpenSpoiler,
                isCollapsed = true,
            )
        }.apply {
            loadedStatuses.addAll(this)
        }
    }
    private val accountsPagingSourceFactory = SearchPagingSourceFactory(mastodonApi, SearchType.Account) {
        it.accounts
    }
    private val hashtagsPagingSourceFactory = SearchPagingSourceFactory(mastodonApi, SearchType.Hashtag) {
        it.hashtags
    }

    val statusesFlow = Pager(
        config = PagingConfig(pageSize = DEFAULT_LOAD_SIZE, initialLoadSize = DEFAULT_LOAD_SIZE),
        pagingSourceFactory = statusesPagingSourceFactory,
    ).flow
        .cachedIn(viewModelScope)

    val accountsFlow = Pager(
        config = PagingConfig(pageSize = DEFAULT_LOAD_SIZE, initialLoadSize = DEFAULT_LOAD_SIZE),
        pagingSourceFactory = accountsPagingSourceFactory,
    ).flow
        .cachedIn(viewModelScope)

    val hashtagsFlow = Pager(
        config = PagingConfig(pageSize = DEFAULT_LOAD_SIZE, initialLoadSize = DEFAULT_LOAD_SIZE),
        pagingSourceFactory = hashtagsPagingSourceFactory,
    ).flow
        .cachedIn(viewModelScope)

    fun search(query: String) {
        loadedStatuses.clear()
        statusesPagingSourceFactory.newSearch(query)
        accountsPagingSourceFactory.newSearch(query)
        hashtagsPagingSourceFactory.newSearch(query)
    }

    fun removeItem(statusViewData: StatusViewData) {
        viewModelScope.launch {
            if (timelineCases.delete(statusViewData.id).isSuccess) {
                if (loadedStatuses.remove(statusViewData)) {
                    statusesPagingSourceFactory.invalidate()
                }
            }
        }
    }

    fun expandedChange(statusViewData: StatusViewData, expanded: Boolean) {
        updateStatusViewData(statusViewData.copy(isExpanded = expanded))
    }

    fun reblog(statusViewData: StatusViewData, reblog: Boolean) {
        viewModelScope.launch {
            timelineCases.reblog(statusViewData.id, reblog).fold({
                updateStatus(
                    statusViewData.status.copy(
                        reblogged = reblog,
                        reblog = statusViewData.status.reblog?.copy(reblogged = reblog),
                    ),
                )
            }, { t ->
                Timber.d("Failed to reblog status ${statusViewData.id}", t)
            })
        }
    }

    fun contentHiddenChange(statusViewData: StatusViewData, isShowing: Boolean) {
        updateStatusViewData(statusViewData.copy(isShowingContent = isShowing))
    }

    fun collapsedChange(statusViewData: StatusViewData, collapsed: Boolean) {
        updateStatusViewData(statusViewData.copy(isCollapsed = collapsed))
    }

    fun voteInPoll(statusViewData: StatusViewData, poll: Poll, choices: List<Int>) {
        val votedPoll = poll.votedCopy(choices)
        updateStatus(statusViewData.status.copy(poll = votedPoll))
        viewModelScope.launch {
            timelineCases.voteInPoll(statusViewData.id, votedPoll.id, choices)
                .onFailure { t -> Timber.d("Failed to vote in poll: ${statusViewData.id}", t) }
        }
    }

    fun favorite(statusViewData: StatusViewData, isFavorited: Boolean) {
        updateStatus(statusViewData.status.copy(favourited = isFavorited))
        viewModelScope.launch {
            timelineCases.favourite(statusViewData.id, isFavorited)
        }
    }

    fun bookmark(statusViewData: StatusViewData, isBookmarked: Boolean) {
        updateStatus(statusViewData.status.copy(bookmarked = isBookmarked))
        viewModelScope.launch {
            timelineCases.bookmark(statusViewData.id, isBookmarked)
        }
    }

    fun muteAccount(accountId: String, notifications: Boolean, duration: Int?) {
        viewModelScope.launch {
            timelineCases.mute(accountId, notifications, duration)
        }
    }

    fun pinAccount(status: Status, isPin: Boolean) {
        viewModelScope.launch {
            timelineCases.pin(status.id, isPin)
        }
    }

    fun blockAccount(accountId: String) {
        viewModelScope.launch {
            timelineCases.block(accountId)
        }
    }

    fun deleteStatusAsync(id: String): Deferred<NetworkResult<DeletedStatus>> {
        return viewModelScope.async {
            timelineCases.delete(id)
        }
    }

    fun muteConversation(statusViewData: StatusViewData, mute: Boolean) {
        updateStatus(statusViewData.status.copy(muted = mute))
        viewModelScope.launch {
            timelineCases.muteConversation(statusViewData.id, mute)
        }
    }

    private fun updateStatusViewData(newStatusViewData: StatusViewData) {
        val idx = loadedStatuses.indexOfFirst { it.id == newStatusViewData.id }
        if (idx >= 0) {
            loadedStatuses[idx] = newStatusViewData
            statusesPagingSourceFactory.invalidate()
        }
    }

    private fun updateStatus(newStatus: Status) {
        val statusViewData = loadedStatuses.find { it.id == newStatus.id }
        if (statusViewData != null) {
            updateStatusViewData(statusViewData.copy(status = newStatus))
        }
    }

    companion object {
        private const val DEFAULT_LOAD_SIZE = 20
    }
}
