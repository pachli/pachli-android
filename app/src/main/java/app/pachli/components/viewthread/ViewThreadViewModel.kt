/* Copyright 2022 Tusky Contributors
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

package app.pachli.components.viewthread

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.appstore.BlockEvent
import app.pachli.appstore.BookmarkEvent
import app.pachli.appstore.EventHub
import app.pachli.appstore.FavoriteEvent
import app.pachli.appstore.PinEvent
import app.pachli.appstore.ReblogEvent
import app.pachli.appstore.StatusComposedEvent
import app.pachli.appstore.StatusDeletedEvent
import app.pachli.appstore.StatusEditedEvent
import app.pachli.components.timeline.CachedTimelineRepository
import app.pachli.components.timeline.FilterKind
import app.pachli.components.timeline.FiltersRepository
import app.pachli.components.timeline.util.ifExpected
import app.pachli.core.accounts.AccountManager
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.network.model.Filter
import app.pachli.core.network.model.FilterV1
import app.pachli.core.network.model.Status
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.network.FilterModel
import app.pachli.usecase.TimelineCases
import app.pachli.util.StatusDisplayOptionsRepository
import app.pachli.viewdata.StatusViewData
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.getOrElse
import at.connyduck.calladapter.networkresult.getOrThrow
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ViewThreadViewModel @Inject constructor(
    private val api: MastodonApi,
    private val filterModel: FilterModel,
    private val timelineCases: TimelineCases,
    eventHub: EventHub,
    accountManager: AccountManager,
    private val timelineDao: TimelineDao,
    private val gson: Gson,
    private val repository: CachedTimelineRepository,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
    private val filtersRepository: FiltersRepository,
) : ViewModel() {

    private val _uiState: MutableStateFlow<ThreadUiState> = MutableStateFlow(ThreadUiState.Loading)
    val uiState: Flow<ThreadUiState>
        get() = _uiState

    private val _errors = MutableSharedFlow<Throwable>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val errors: Flow<Throwable>
        get() = _errors

    var isInitialLoad: Boolean = true

    val statusDisplayOptions = statusDisplayOptionsRepository.flow

    private val alwaysShowSensitiveMedia: Boolean
    private val alwaysOpenSpoiler: Boolean

    val activeAccount: AccountEntity

    init {
        activeAccount = accountManager.activeAccount!!
        alwaysShowSensitiveMedia = activeAccount.alwaysShowSensitiveMedia
        alwaysOpenSpoiler = activeAccount.alwaysOpenSpoiler

        viewModelScope.launch {
            eventHub.events
                .collect { event ->
                    when (event) {
                        is FavoriteEvent -> handleFavEvent(event)
                        is ReblogEvent -> handleReblogEvent(event)
                        is BookmarkEvent -> handleBookmarkEvent(event)
                        is PinEvent -> handlePinEvent(event)
                        is BlockEvent -> removeAllByAccountId(event.accountId)
                        is StatusComposedEvent -> handleStatusComposedEvent(event)
                        is StatusDeletedEvent -> handleStatusDeletedEvent(event)
                        is StatusEditedEvent -> handleStatusEditedEvent(event)
                    }
                }
        }

        loadFilters()
    }

    fun loadThread(id: String) {
        _uiState.value = ThreadUiState.Loading

        viewModelScope.launch {
            Log.d(TAG, "Finding status with: $id")
            val contextCall = async { api.statusContext(id) }
            val timelineStatusWithAccount = timelineDao.getStatus(id)

            var detailedStatus = if (timelineStatusWithAccount != null) {
                Log.d(TAG, "Loaded status from local timeline")
                val status = timelineStatusWithAccount.toStatus(gson)

                // Return the correct status, depending on which one matched. If you do not do
                // this the status IDs will be different between the status that's displayed with
                // ThreadUiState.LoadingThread and ThreadUiState.Success, even though the apparent
                // status content is the same. Then the status flickers as it is drawn twice.
                if (status.actionableId == id) {
                    StatusViewData.from(
                        status = status.actionableStatus,
                        isExpanded = timelineStatusWithAccount.viewData?.expanded ?: alwaysOpenSpoiler,
                        isShowingContent = timelineStatusWithAccount.viewData?.contentShowing ?: (alwaysShowSensitiveMedia || !status.actionableStatus.sensitive),
                        isCollapsed = timelineStatusWithAccount.viewData?.contentCollapsed ?: true,
                        isDetailed = true,
                    )
                } else {
                    StatusViewData.from(
                        timelineStatusWithAccount,
                        gson,
                        isExpanded = alwaysOpenSpoiler,
                        isShowingContent = (alwaysShowSensitiveMedia || !status.actionableStatus.sensitive),
                        isDetailed = true,
                    )
                }
            } else {
                Log.d(TAG, "Loaded status from network")
                val result = api.status(id).getOrElse { exception ->
                    _uiState.value = ThreadUiState.Error(exception)
                    return@launch
                }
                StatusViewData.fromStatusAndUiState(result, isDetailed = true)
            }

            _uiState.value = ThreadUiState.LoadingThread(
                statusViewDatum = detailedStatus,
                revealButton = detailedStatus.getRevealButtonState(),
            )

            // If the detailedStatus was loaded from the database it might be out-of-date
            // compared to the remote one. Now the user has a working UI do a background fetch
            // for the status. Ignore errors, the user still has a functioning UI if the fetch
            // failed.
            if (timelineStatusWithAccount != null) {
                api.status(id).getOrNull()?.let {
                    detailedStatus = StatusViewData.from(
                        it,
                        isShowingContent = detailedStatus.isShowingContent,
                        isExpanded = detailedStatus.isExpanded,
                        isCollapsed = detailedStatus.isCollapsed,
                        isDetailed = true,
                    )
                }
            }

            val contextResult = contextCall.await()

            contextResult.fold({ statusContext ->
                val ids = statusContext.ancestors.map { it.id } + statusContext.descendants.map { it.id }
                val cachedViewData = repository.getStatusViewData(ids)
                val ancestors = statusContext.ancestors.map {
                        status ->
                    val svd = cachedViewData[status.id]
                    StatusViewData.from(
                        status,
                        isShowingContent = svd?.contentShowing ?: (alwaysShowSensitiveMedia || !status.actionableStatus.sensitive),
                        isExpanded = svd?.expanded ?: alwaysOpenSpoiler,
                        isCollapsed = svd?.contentCollapsed ?: true,
                        isDetailed = false,
                    )
                }.filterByFilterAction()
                val descendants = statusContext.descendants.map {
                        status ->
                    val svd = cachedViewData[status.id]
                    StatusViewData.from(
                        status,
                        isShowingContent = svd?.contentShowing ?: (alwaysShowSensitiveMedia || !status.actionableStatus.sensitive),
                        isExpanded = svd?.expanded ?: alwaysOpenSpoiler,
                        isCollapsed = svd?.contentCollapsed ?: true,
                        isDetailed = false,
                    )
                }.filterByFilterAction()
                val statuses = ancestors + detailedStatus + descendants

                _uiState.value = ThreadUiState.Success(
                    statusViewData = statuses,
                    detailedStatusPosition = ancestors.size,
                    revealButton = statuses.getRevealButtonState(),
                )
            }, { throwable ->
                _errors.emit(throwable)
                _uiState.value = ThreadUiState.Success(
                    statusViewData = listOf(detailedStatus),
                    detailedStatusPosition = 0,
                    revealButton = RevealButtonState.NO_BUTTON,
                )
            },)
        }
    }

    fun retry(id: String) {
        _uiState.value = ThreadUiState.Loading
        loadThread(id)
    }

    fun refresh(id: String) {
        _uiState.value = ThreadUiState.Refreshing
        loadThread(id)
    }

    fun detailedStatus(): StatusViewData? {
        return when (val uiState = _uiState.value) {
            is ThreadUiState.Success -> uiState.statusViewData.find { status ->
                status.isDetailed
            }
            is ThreadUiState.LoadingThread -> uiState.statusViewDatum
            else -> null
        }
    }

    fun reblog(reblog: Boolean, status: StatusViewData): Job = viewModelScope.launch {
        try {
            timelineCases.reblog(status.actionableId, reblog).getOrThrow()
        } catch (t: Exception) {
            ifExpected(t) {
                Log.d(TAG, "Failed to reblog status " + status.actionableId, t)
            }
        }
    }

    fun favorite(favorite: Boolean, status: StatusViewData): Job = viewModelScope.launch {
        try {
            timelineCases.favourite(status.actionableId, favorite).getOrThrow()
        } catch (t: Exception) {
            ifExpected(t) {
                Log.d(TAG, "Failed to favourite status " + status.actionableId, t)
            }
        }
    }

    fun bookmark(bookmark: Boolean, status: StatusViewData): Job = viewModelScope.launch {
        try {
            timelineCases.bookmark(status.actionableId, bookmark).getOrThrow()
        } catch (t: Exception) {
            ifExpected(t) {
                Log.d(TAG, "Failed to bookmark status " + status.actionableId, t)
            }
        }
    }

    fun voteInPoll(choices: List<Int>, status: StatusViewData): Job = viewModelScope.launch {
        val poll = status.status.actionableStatus.poll ?: run {
            Log.w(TAG, "No poll on status ${status.id}")
            return@launch
        }

        val votedPoll = poll.votedCopy(choices)
        updateStatus(status.id) { status ->
            status.copy(poll = votedPoll)
        }

        try {
            timelineCases.voteInPoll(status.actionableId, poll.id, choices).getOrThrow()
        } catch (t: Exception) {
            ifExpected(t) {
                Log.d(TAG, "Failed to vote in poll: " + status.actionableId, t)
            }
        }
    }

    fun removeStatus(statusToRemove: StatusViewData) {
        updateSuccess { uiState ->
            uiState.copy(
                statusViewData = uiState.statusViewData.filterNot { status -> status == statusToRemove },
            )
        }
    }

    fun changeExpanded(expanded: Boolean, status: StatusViewData) {
        updateSuccess { uiState ->
            val statuses = uiState.statusViewData.map { viewData ->
                if (viewData.id == status.id) {
                    viewData.copy(isExpanded = expanded)
                } else {
                    viewData
                }
            }
            uiState.copy(
                statusViewData = statuses,
                revealButton = statuses.getRevealButtonState(),
            )
        }
        viewModelScope.launch {
            repository.saveStatusViewData(status.copy(isExpanded = expanded))
        }
    }

    fun changeContentShowing(isShowing: Boolean, status: StatusViewData) {
        updateStatusViewData(status.id) { viewData ->
            viewData.copy(isShowingContent = isShowing)
        }
        viewModelScope.launch {
            repository.saveStatusViewData(status.copy(isShowingContent = isShowing))
        }
    }

    fun changeContentCollapsed(isCollapsed: Boolean, status: StatusViewData) {
        updateStatusViewData(status.id) { viewData ->
            viewData.copy(isCollapsed = isCollapsed)
        }
        viewModelScope.launch {
            repository.saveStatusViewData(status.copy(isCollapsed = isCollapsed))
        }
    }

    private fun handleFavEvent(event: FavoriteEvent) {
        updateStatus(event.statusId) { status ->
            status.copy(favourited = event.favourite)
        }
    }

    private fun handleReblogEvent(event: ReblogEvent) {
        updateStatus(event.statusId) { status ->
            status.copy(reblogged = event.reblog)
        }
    }

    private fun handleBookmarkEvent(event: BookmarkEvent) {
        updateStatus(event.statusId) { status ->
            status.copy(bookmarked = event.bookmark)
        }
    }

    private fun handlePinEvent(event: PinEvent) {
        updateStatus(event.statusId) { status ->
            status.copy(pinned = event.pinned)
        }
    }

    private fun removeAllByAccountId(accountId: String) {
        updateSuccess { uiState ->
            uiState.copy(
                statusViewData = uiState.statusViewData.filter { viewData ->
                    viewData.status.account.id != accountId
                },
            )
        }
    }

    private fun handleStatusComposedEvent(event: StatusComposedEvent) {
        val eventStatus = event.status
        updateSuccess { uiState ->
            val statuses = uiState.statusViewData
            val detailedIndex = statuses.indexOfFirst { status -> status.isDetailed }
            val repliedIndex = statuses.indexOfFirst { status -> eventStatus.inReplyToId == status.id }
            if (detailedIndex != -1 && repliedIndex >= detailedIndex) {
                // there is a new reply to the detailed status or below -> display it
                val newStatuses = statuses.subList(0, repliedIndex + 1) +
                    StatusViewData.fromStatusAndUiState(eventStatus) +
                    statuses.subList(repliedIndex + 1, statuses.size)
                uiState.copy(statusViewData = newStatuses)
            } else {
                uiState
            }
        }
    }

    private fun handleStatusEditedEvent(event: StatusEditedEvent) {
        updateSuccess { uiState ->
            uiState.copy(
                statusViewData = uiState.statusViewData.map { status ->
                    if (status.actionableId == event.originalId) {
                        StatusViewData.fromStatusAndUiState(event.status)
                    } else {
                        status
                    }
                },
            )
        }
    }

    private fun handleStatusDeletedEvent(event: StatusDeletedEvent) {
        updateSuccess { uiState ->
            uiState.copy(
                statusViewData = uiState.statusViewData.filter { status ->
                    status.id != event.statusId
                },
            )
        }
    }

    fun toggleRevealButton() {
        updateSuccess { uiState ->
            when (uiState.revealButton) {
                RevealButtonState.HIDE -> uiState.copy(
                    statusViewData = uiState.statusViewData.map { viewData ->
                        viewData.copy(isExpanded = false)
                    },
                    revealButton = RevealButtonState.REVEAL,
                )
                RevealButtonState.REVEAL -> uiState.copy(
                    statusViewData = uiState.statusViewData.map { viewData ->
                        viewData.copy(isExpanded = true)
                    },
                    revealButton = RevealButtonState.HIDE,
                )
                else -> uiState
            }
        }
    }

    private fun StatusViewData.getRevealButtonState(): RevealButtonState {
        val hasWarnings = status.spoilerText.isNotEmpty()

        return if (hasWarnings) {
            if (isExpanded) {
                RevealButtonState.HIDE
            } else {
                RevealButtonState.REVEAL
            }
        } else {
            RevealButtonState.NO_BUTTON
        }
    }

    /**
     * Get the reveal button state based on the state of all the statuses in the list.
     *
     * - If any status sets it to REVEAL, use REVEAL
     * - If no status sets it to REVEAL, but at least one uses HIDE, use HIDE
     * - Otherwise use NO_BUTTON
     */
    private fun List<StatusViewData>.getRevealButtonState(): RevealButtonState {
        var seenHide = false

        forEach {
            when (val state = it.getRevealButtonState()) {
                RevealButtonState.NO_BUTTON -> return@forEach
                RevealButtonState.REVEAL -> return state
                RevealButtonState.HIDE -> seenHide = true
            }
        }

        if (seenHide) {
            return RevealButtonState.HIDE
        }

        return RevealButtonState.NO_BUTTON
    }

    private fun loadFilters() {
        viewModelScope.launch {
            try {
                when (val filters = filtersRepository.getFilters()) {
                    is FilterKind.V1 -> {
                        filterModel.initWithFilters(
                            filters.filters.filter { filter ->
                                filter.context.contains(FilterV1.THREAD)
                            },
                        )
                    }

                    is FilterKind.V2 -> filterModel.kind = Filter.Kind.THREAD
                }
                updateStatuses()
            } catch (_: Exception) {
                // TODO: Deliberately don't emit to _errors here -- at the moment
                // ViewThreadFragment shows a generic error to the user, and that
                // would confuse them when the rest of the thread is loading OK.
            }
        }
    }

    private fun updateStatuses() {
        updateSuccess { uiState ->
            val statuses = uiState.statusViewData.filterByFilterAction()
            uiState.copy(
                statusViewData = statuses,
                revealButton = statuses.getRevealButtonState(),
            )
        }
    }

    private fun List<StatusViewData>.filterByFilterAction(): List<StatusViewData> {
        return filter { status ->
            if (status.isDetailed) {
                true
            } else {
                status.filterAction = filterModel.shouldFilterStatus(status.status)
                status.filterAction != Filter.Action.HIDE
            }
        }
    }

    /**
     * Creates a [StatusViewData] from `status`, copying over the viewdata state from the same
     * status in _uiState (if that status exists).
     */
    private fun StatusViewData.Companion.fromStatusAndUiState(status: Status, isDetailed: Boolean = false): StatusViewData {
        val oldStatus = (_uiState.value as? ThreadUiState.Success)?.statusViewData?.find { it.id == status.id }
        return from(
            status,
            isShowingContent = oldStatus?.isShowingContent ?: (alwaysShowSensitiveMedia || !status.actionableStatus.sensitive),
            isExpanded = oldStatus?.isExpanded ?: alwaysOpenSpoiler,
            isCollapsed = oldStatus?.isCollapsed ?: !isDetailed,
            isDetailed = oldStatus?.isDetailed ?: isDetailed,
        )
    }

    private inline fun updateSuccess(updater: (ThreadUiState.Success) -> ThreadUiState.Success) {
        _uiState.update { uiState ->
            if (uiState is ThreadUiState.Success) {
                updater(uiState)
            } else {
                uiState
            }
        }
    }

    private fun updateStatusViewData(statusId: String, updater: (StatusViewData) -> StatusViewData) {
        updateSuccess { uiState ->
            uiState.copy(
                statusViewData = uiState.statusViewData.map { viewData ->
                    if (viewData.id == statusId) {
                        updater(viewData)
                    } else {
                        viewData
                    }
                },
            )
        }
    }

    private fun updateStatus(statusId: String, updater: (Status) -> Status) {
        updateStatusViewData(statusId) { viewData ->
            viewData.copy(
                status = updater(viewData.status),
            )
        }
    }

    fun clearWarning(viewData: StatusViewData) {
        updateStatus(viewData.id) { status ->
            status.copy(filtered = null)
        }
    }

    companion object {
        private const val TAG = "ViewThreadViewModel"
    }
}

sealed interface ThreadUiState {
    /** The initial load of the detailed status for this thread */
    data object Loading : ThreadUiState

    /** Loading the detailed status has completed, now loading ancestors/descendants */
    data class LoadingThread(
        val statusViewDatum: StatusViewData?,
        val revealButton: RevealButtonState,
    ) : ThreadUiState

    /** An error occurred at any point */
    class Error(val throwable: Throwable) : ThreadUiState

    /** Successfully loaded the full thread */
    data class Success(
        val statusViewData: List<StatusViewData>,
        val revealButton: RevealButtonState,
        val detailedStatusPosition: Int,
    ) : ThreadUiState

    /** Refreshing the thread with a swipe */
    data object Refreshing : ThreadUiState
}

enum class RevealButtonState {
    NO_BUTTON, REVEAL, HIDE
}
