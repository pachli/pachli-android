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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.components.timeline.CachedTimelineRepository
import app.pachli.core.common.PachliError
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.Loadable
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.data.repository.StatusRepository
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.TranslationState
import app.pachli.core.database.model.toEntity
import app.pachli.core.eventhub.BlockEvent
import app.pachli.core.eventhub.BookmarkEvent
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.FavoriteEvent
import app.pachli.core.eventhub.PinEvent
import app.pachli.core.eventhub.ReblogEvent
import app.pachli.core.eventhub.StatusComposedEvent
import app.pachli.core.eventhub.StatusDeletedEvent
import app.pachli.core.eventhub.StatusEditedEvent
import app.pachli.core.model.ContentFilterVersion
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterContext
import app.pachli.core.network.model.Poll
import app.pachli.core.network.model.Status
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.apiresult.ApiError
import app.pachli.network.ContentFilterModel
import app.pachli.usecase.TimelineCases
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class ViewThreadViewModel @Inject constructor(
    private val api: MastodonApi,
    eventHub: EventHub,
    private val accountManager: AccountManager,
    private val timelineDao: TimelineDao,
    private val repository: CachedTimelineRepository,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
    private val statusRepository: StatusRepository,
    private val timelineCases: TimelineCases,
) : ViewModel() {
    // TODO: For consistency with other fragments the UiState should not include
    // the list of statuses. Look at SuggestionsViewModel for ideas.
    private val _uiResult: MutableStateFlow<Result<ThreadUiState, ThreadError>> =
        MutableStateFlow(Ok(ThreadUiState.Loading))
    val uiResult: Flow<Result<ThreadUiState, ThreadError>>
        get() = _uiResult

    private val _errors = Channel<PachliError>()
    val errors = _errors.receiveAsFlow()

    var isInitialLoad: Boolean = true

    val statusDisplayOptions = statusDisplayOptionsRepository.flow

    val activeAccount: AccountEntity
        get() {
            return accountManager.activeAccount!!
        }

    private var contentFilterModel: ContentFilterModel? = null

    init {
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

        viewModelScope.launch {
            accountManager.activePachliAccountFlow
                .distinctUntilChangedBy { it.contentFilters }
                .collect { account ->
                    contentFilterModel = when (account.contentFilters.version) {
                        ContentFilterVersion.V2 -> ContentFilterModel(FilterContext.CONVERSATIONS)
                        ContentFilterVersion.V1 -> ContentFilterModel(FilterContext.CONVERSATIONS, account.contentFilters.contentFilters)
                    }
                    updateStatuses()
                }
        }
    }

    fun loadThread(id: String) {
        viewModelScope.launch {
            _uiResult.value = Ok(ThreadUiState.Loading)

            val account = accountManager.activeAccountFlow
                .filterIsInstance<Loadable.Loaded<AccountEntity?>>()
                .filter { it.data != null }
                .first().data!!

            Timber.d("Finding status with: %s", id)
            val contextCall = async { api.statusContext(id) }
            val timelineStatusWithAccount = timelineDao.getStatus(id)

            var detailedStatus = if (timelineStatusWithAccount != null) {
                Timber.d("Loaded status from local timeline")
                val status = timelineStatusWithAccount.toStatus()

                // Return the correct status, depending on which one matched. If you do not do
                // this the status IDs will be different between the status that's displayed with
                // ThreadUiState.LoadingThread and ThreadUiState.Success, even though the apparent
                // status content is the same. Then the status flickers as it is drawn twice.
                if (status.actionableId == id) {
                    StatusViewData.from(
                        pachliAccountId = account.id,
                        status = status.actionableStatus,
                        isExpanded = timelineStatusWithAccount.viewData?.expanded ?: account.alwaysOpenSpoiler,
                        isShowingContent = timelineStatusWithAccount.viewData?.contentShowing ?: (account.alwaysShowSensitiveMedia || !status.actionableStatus.sensitive),
                        isCollapsed = timelineStatusWithAccount.viewData?.contentCollapsed ?: true,
                        isDetailed = true,
                        contentFilterAction = contentFilterModel?.filterActionFor(status.actionableStatus)
                            ?: FilterAction.NONE,
                        translationState = timelineStatusWithAccount.viewData?.translationState ?: TranslationState.SHOW_ORIGINAL,
                        translation = timelineStatusWithAccount.translatedStatus,
                    )
                } else {
                    StatusViewData.from(
                        pachliAccountId = account.id,
                        timelineStatusWithAccount,
                        isExpanded = account.alwaysOpenSpoiler,
                        isShowingContent = (account.alwaysShowSensitiveMedia || !status.actionableStatus.sensitive),
                        isDetailed = true,
                        contentFilterAction = contentFilterModel?.filterActionFor(status.actionableStatus)
                            ?: FilterAction.NONE,
                        translationState = TranslationState.SHOW_ORIGINAL,
                    )
                }
            } else {
                Timber.d("Loaded status from network")
                val result = api.status(id).getOrElse { error ->
                    _uiResult.value = Err(ThreadError.Api(error))
                    return@launch
                }
                StatusViewData.fromStatusAndUiState(account, result.body, isDetailed = true)
            }

            _uiResult.value = Ok(
                ThreadUiState.LoadingThread(
                    statusViewDatum = detailedStatus,
                    revealButton = detailedStatus.getRevealButtonState(),
                ),
            )

            // If the detailedStatus was loaded from the database it might be out-of-date
            // compared to the remote one. Now the user has a working UI do a background fetch
            // for the status. Ignore errors, the user still has a functioning UI if the fetch
            // failed.
            if (timelineStatusWithAccount != null) {
                api.status(id).get()?.body?.let {
                    detailedStatus = StatusViewData.from(
                        pachliAccountId = account.id,
                        it,
                        isShowingContent = detailedStatus.isShowingContent,
                        isExpanded = detailedStatus.isExpanded,
                        isCollapsed = detailedStatus.isCollapsed,
                        isDetailed = true,
                        contentFilterAction = contentFilterModel?.filterActionFor(it) ?: FilterAction.NONE,
                        translationState = detailedStatus.translationState,
                        translation = detailedStatus.translation,
                    )
                }
            }

            val contextResult = contextCall.await()

            contextResult.onSuccess {
                val statusContext = it.body
                val ids = statusContext.ancestors.map { it.id } + statusContext.descendants.map { it.id }
                val cachedViewData = repository.getStatusViewData(activeAccount.id, ids)
                val cachedTranslations = repository.getStatusTranslations(activeAccount.id, ids)
                val ancestors = statusContext.ancestors.map { status ->
                    val svd = cachedViewData[status.id]
                    StatusViewData.from(
                        pachliAccountId = account.id,
                        status,
                        isShowingContent = svd?.contentShowing ?: (account.alwaysShowSensitiveMedia || !status.actionableStatus.sensitive),
                        isExpanded = svd?.expanded ?: account.alwaysOpenSpoiler,
                        isCollapsed = svd?.contentCollapsed ?: true,
                        isDetailed = false,
                        contentFilterAction = contentFilterModel?.filterActionFor(status) ?: FilterAction.NONE,
                        translationState = svd?.translationState ?: TranslationState.SHOW_ORIGINAL,
                        translation = cachedTranslations[status.id],
                    )
                }.filterByFilterAction()
                val descendants = statusContext.descendants.map { status ->
                    val svd = cachedViewData[status.id]
                    StatusViewData.from(
                        pachliAccountId = account.id,
                        status,
                        isShowingContent = svd?.contentShowing ?: (account.alwaysShowSensitiveMedia || !status.actionableStatus.sensitive),
                        isExpanded = svd?.expanded ?: account.alwaysOpenSpoiler,
                        isCollapsed = svd?.contentCollapsed ?: true,
                        isDetailed = false,
                        contentFilterAction = contentFilterModel?.filterActionFor(status) ?: FilterAction.NONE,
                        translationState = svd?.translationState ?: TranslationState.SHOW_ORIGINAL,
                        translation = cachedTranslations[status.id],
                    )
                }.filterByFilterAction()
                val statuses = ancestors + detailedStatus + descendants

                _uiResult.value = Ok(
                    ThreadUiState.Loaded(
                        statusViewData = statuses,
                        detailedStatusPosition = ancestors.size,
                        revealButton = statuses.getRevealButtonState(),
                    ),
                )
            }
                .onFailure { error ->
                    _uiResult.value = Ok(
                        ThreadUiState.Loaded(
                            statusViewData = listOf(detailedStatus),
                            detailedStatusPosition = 0,
                            revealButton = RevealButtonState.NO_BUTTON,
                        ),
                    )
                    _errors.send(error)
                }
        }
    }

    fun retry(id: String) {
        _uiResult.value = Ok(ThreadUiState.Loading)
        loadThread(id)
    }

    fun refresh(id: String) {
        _uiResult.value = Ok(ThreadUiState.Refreshing)
        loadThread(id)
    }

    fun detailedStatus(): StatusViewData? {
        return when (val uiState = _uiResult.value.get()) {
            is ThreadUiState.Loaded -> uiState.statusViewData.find { status ->
                status.isDetailed
            }
            is ThreadUiState.LoadingThread -> uiState.statusViewDatum
            else -> null
        }
    }

    fun reblog(reblog: Boolean, status: StatusViewData) = viewModelScope.launch {
        updateStatus(status.id) {
            it.copy(
                reblogged = reblog,
                reblog = it.reblog?.copy(reblogged = reblog),
            )
        }
        statusRepository.reblog(status.pachliAccountId, status.actionableId, reblog).onFailure {
            updateStatus(status.id) { it }
            Timber.d("Failed to reblog status: %s: %s", status.actionableId, it)
        }
    }

    fun favorite(favorite: Boolean, status: StatusViewData) = viewModelScope.launch {
        updateStatus(status.id) {
            it.copy(
                favourited = favorite,
                favouritesCount = it.favouritesCount + 1,
            )
        }
        statusRepository.favourite(status.pachliAccountId, status.actionableId, favorite).onFailure {
            updateStatus(status.id) { it }
            Timber.d("Failed to favourite status: %s: %s", status.actionableId, it)
        }
    }

    fun bookmark(bookmark: Boolean, status: StatusViewData) = viewModelScope.launch {
        updateStatus(status.id) { it.copy(bookmarked = bookmark) }
        statusRepository.bookmark(status.pachliAccountId, status.actionableId, bookmark).onFailure {
            updateStatus(status.id) { it }
            Timber.d("Failed to bookmark status: %s: %s", status.actionableId, it)
        }
    }

    fun voteInPoll(poll: Poll, choices: List<Int>, status: StatusViewData): Job = viewModelScope.launch {
        val votedPoll = poll.votedCopy(choices)
        updateStatus(status.id) { status ->
            status.copy(poll = votedPoll)
        }

        statusRepository.voteInPoll(status.pachliAccountId, status.actionableId, poll.id, choices)
            .onFailure {
                Timber.d("Failed to vote in poll: %s: %s", status.actionableId, it)
                updateStatus(status.id) { it.copy(poll = poll) }
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
            statusRepository.setExpanded(status.pachliAccountId, status.id, expanded)
        }
    }

    fun changeContentShowing(isShowing: Boolean, status: StatusViewData) {
        updateStatusViewData(status.id) { viewData ->
            viewData.copy(isShowingContent = isShowing)
        }
        viewModelScope.launch {
            statusRepository.setContentShowing(status.pachliAccountId, status.id, isShowing)
        }
    }

    fun changeContentCollapsed(isCollapsed: Boolean, status: StatusViewData) {
        updateStatusViewData(status.id) { viewData ->
            viewData.copy(isCollapsed = isCollapsed)
        }
        viewModelScope.launch {
            statusRepository.setContentCollapsed(status.pachliAccountId, status.id, isCollapsed)
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
                    StatusViewData.fromStatusAndUiState(activeAccount, eventStatus) +
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
                        StatusViewData.fromStatusAndUiState(activeAccount, event.status)
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

    fun translate(statusViewData: StatusViewData) {
        viewModelScope.launch {
            timelineCases.translate(statusViewData)
                .onSuccess {
                    val translatedEntity = it.toEntity(statusViewData.pachliAccountId, statusViewData.actionableId)
                    updateStatusViewData(statusViewData.id) { viewData ->
                        viewData.copy(translation = translatedEntity, translationState = TranslationState.SHOW_TRANSLATION)
                    }
                }
                .onFailure { _errors.send(it) }
        }
    }

    fun translateUndo(statusViewData: StatusViewData) {
        updateStatusViewData(statusViewData.id) { viewData ->
            viewData.copy(translationState = TranslationState.SHOW_ORIGINAL)
        }
        viewModelScope.launch {
            statusRepository.setTranslationState(statusViewData.pachliAccountId, statusViewData.id, TranslationState.SHOW_ORIGINAL)
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
                status.contentFilterAction = contentFilterModel?.filterActionFor(status.status) ?: FilterAction.NONE
                status.contentFilterAction != FilterAction.HIDE
            }
        }
    }

    /**
     * Creates a [StatusViewData] from `status`, copying over the viewdata state from the same
     * status in _uiState (if that status exists).
     */
    private fun StatusViewData.Companion.fromStatusAndUiState(account: AccountEntity, status: Status, isDetailed: Boolean = false): StatusViewData {
        val oldStatus =
            (_uiResult.value.get() as? ThreadUiState.Loaded)?.statusViewData?.find { it.id == status.id }
        return from(
            pachliAccountId = account.id,
            status,
            isShowingContent = oldStatus?.isShowingContent ?: (account.alwaysShowSensitiveMedia || !status.actionableStatus.sensitive),
            isExpanded = oldStatus?.isExpanded ?: account.alwaysOpenSpoiler,
            isCollapsed = oldStatus?.isCollapsed ?: !isDetailed,
            isDetailed = oldStatus?.isDetailed ?: isDetailed,
        )
    }

    /**
     * Updates [_uiResult] using [updater] if [_uiResult] is already [ThreadUiState.Loaded].
     */
    private inline fun updateSuccess(updater: (ThreadUiState.Loaded) -> ThreadUiState.Loaded) {
        _uiResult.getAndUpdate { v ->
            if (v.get() is ThreadUiState.Loaded) {
                Ok(updater(v.get() as ThreadUiState.Loaded))
            } else {
                v
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
            viewData.copy(status = updater(viewData.status))
        }
    }

    fun clearWarning(viewData: StatusViewData) {
        updateStatusViewData(viewData.id) { it.copy(contentFilterAction = FilterAction.NONE) }
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

    /** Successfully loaded the full thread */
    data class Loaded(
        val statusViewData: List<StatusViewData>,
        val revealButton: RevealButtonState,
        val detailedStatusPosition: Int,
    ) : ThreadUiState

    /** Refreshing the thread with a swipe */
    data object Refreshing : ThreadUiState
}

sealed interface ThreadError : PachliError {
    @JvmInline
    value class Api(private val error: ApiError) : ThreadError, PachliError by error
}

enum class RevealButtonState {
    NO_BUTTON,
    REVEAL,
    HIDE,
}
