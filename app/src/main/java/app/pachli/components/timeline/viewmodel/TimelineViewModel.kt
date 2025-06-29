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

import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import app.pachli.R
import app.pachli.components.timeline.TimelineRepository
import app.pachli.core.common.PachliError
import app.pachli.core.common.extensions.throttleFirst
import app.pachli.core.data.model.ContentFilterModel
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.OfflineFirstStatusRepository
import app.pachli.core.data.repository.StatusActionError
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.database.model.TimelineStatusWithAccount
import app.pachli.core.eventhub.BlockEvent
import app.pachli.core.eventhub.BookmarkEvent
import app.pachli.core.eventhub.DomainMuteEvent
import app.pachli.core.eventhub.Event
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.FavoriteEvent
import app.pachli.core.eventhub.MuteConversationEvent
import app.pachli.core.eventhub.MuteEvent
import app.pachli.core.eventhub.PinEvent
import app.pachli.core.eventhub.ReblogEvent
import app.pachli.core.eventhub.StatusComposedEvent
import app.pachli.core.eventhub.StatusDeletedEvent
import app.pachli.core.eventhub.StatusEditedEvent
import app.pachli.core.eventhub.UnfollowEvent
import app.pachli.core.model.AttachmentBlurDecision
import app.pachli.core.model.ContentFilterVersion
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterContext
import app.pachli.core.model.Poll
import app.pachli.core.model.Status
import app.pachli.core.model.Timeline
import app.pachli.core.model.translation.TranslatedStatus
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.preferences.TabTapBehaviour
import app.pachli.translation.TranslatorError
import app.pachli.usecase.TimelineCases
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapEither
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

data class UiState(
    /** True if the FAB should be shown while scrolling */
    val showFabWhileScrolling: Boolean,

    /** True if the timeline should be shown in reverse order (oldest first) */
    val reverseTimeline: Boolean,

    /** User's preference for behaviour when tapping a tab. */
    val tabTapBehaviour: TabTapBehaviour = TabTapBehaviour.JUMP_TO_NEXT_PAGE,
)

// TODO: Ui* classes are copied from NotificationsViewModel. Not yet sure whether these actions
// are "global" across all timelines (including notifications) or whether notifications are
// sufficiently different to warrant having a duplicate set. Keeping them duplicated for the
// moment.

/** Parent class for all UI actions, fallible or infallible. */
sealed interface UiAction

/** Actions the user can trigger from the UI. These actions may fail. */
sealed interface FallibleUiAction : UiAction {
    /* none at the moment */
}

/**
 * Actions the user can trigger from the UI that either cannot fail, or if they do fail,
 * do not show an error.
 */
sealed interface InfallibleUiAction : UiAction {
    /**
     * Directs the ViewModel to load the account identified by [pachliAccountId].
     * This will trigger fetching statuses for that account, and emit into the
     * [TimelineViewModel.statuses] flow.
     */
    // Do not replace this with a function that returns the flow. The UI would call it
    // on every restart (e.g., configuration change) causing the flow to be recreated.
    // This way the flow is cached in the viewmodel, and the UI can recollect it on
    // restart with very little delay.
    data class LoadPachliAccount(val pachliAccountId: Long) : InfallibleUiAction

    /**
     * User is leaving the fragment, save the ID of the visible status.
     *
     * Infallible because if it fails there's nowhere to show the error, and nothing the user
     * can do.
     */
    data class SaveVisibleId(
        val pachliAccountId: Long,
        val visibleId: String,
    ) : InfallibleUiAction

    /** Ignore the saved reading position, load the page with the newest items */
    // Resets the account's reading position, which can't fail, which is why this is
    // infallible. Reloading the data may fail, but that's handled by the paging system /
    // adapter refresh logic.
    data class LoadNewest(val pachliAccountId: Long) : InfallibleUiAction
}

sealed interface UiSuccess {
    // These three are from menu items on the status. Currently they don't come to the
    // viewModel as actions, they're noticed when events are posted. That will change,
    // but for the moment we can still report them to the UI. Typically, receiving any
    // of these three should trigger the UI to refresh.

    /** A user was blocked */
    data object Block : UiSuccess

    /** A user was muted */
    data object Mute : UiSuccess

    /** A conversation was muted */
    data object MuteConversation : UiSuccess

    /** A status the user wrote was successfully posted */
    data class StatusSent(val status: Status) : UiSuccess

    /** A status the user wrote was successfully edited */
    data class StatusEdited(val status: Status) : UiSuccess

    /**
     * Resetting the reading position completed, the UI should refresh the adapter
     * to load content at the new position.
     */
    data object LoadNewest : UiSuccess
}

sealed interface StatusAction : UiAction

sealed interface InfallibleStatusAction : InfallibleUiAction, StatusAction {
    val statusViewData: StatusViewData

    data class TranslateUndo(override val statusViewData: StatusViewData) : InfallibleStatusAction
}

/** Actions the user can trigger on an individual status */
sealed interface FallibleStatusAction : FallibleUiAction, StatusAction {
    // TODO: Include a property for the PachliAccountId the action is being performed as.

    val statusViewData: StatusViewData

    /** Set the bookmark state for a status */
    data class Bookmark(val state: Boolean, override val statusViewData: StatusViewData) :
        FallibleStatusAction

    /** Set the favourite state for a status */
    data class Favourite(val state: Boolean, override val statusViewData: StatusViewData) :
        FallibleStatusAction

    /** Set the reblog state for a status */
    data class Reblog(val state: Boolean, override val statusViewData: StatusViewData) :
        FallibleStatusAction

    /**
     * Vote in a poll.
     *
     * @param poll Poll the user is voting in.
     * @param choices Indices of the choices the user is voting for.
     * @param statusViewData
     */
    data class VoteInPoll(
        val poll: Poll,
        val choices: List<Int>,
        override val statusViewData: StatusViewData,
    ) : FallibleStatusAction

    /** Translate a status */
    data class Translate(override val statusViewData: StatusViewData) : FallibleStatusAction
}

/** Changes to a status' visible state after API calls */
sealed interface StatusActionSuccess : UiSuccess {
    val action: FallibleStatusAction

    data class Bookmark(override val action: FallibleStatusAction.Bookmark) : StatusActionSuccess

    data class Favourite(override val action: FallibleStatusAction.Favourite) : StatusActionSuccess

    data class Reblog(override val action: FallibleStatusAction.Reblog) : StatusActionSuccess

    data class VoteInPoll(override val action: FallibleStatusAction.VoteInPoll) : StatusActionSuccess

    data class Translate(override val action: FallibleStatusAction.Translate) : StatusActionSuccess

    companion object {
        fun from(action: FallibleStatusAction) = when (action) {
            is FallibleStatusAction.Bookmark -> Bookmark(action)
            is FallibleStatusAction.Favourite -> Favourite(action)
            is FallibleStatusAction.Reblog -> Reblog(action)
            is FallibleStatusAction.VoteInPoll -> VoteInPoll(action)
            is FallibleStatusAction.Translate -> Translate(action)
        }
    }
}

// TODO: Similar to UiError in NotificationsViewModel, but without ClearNotifications,
// AcceptFollowRequest, and RejectFollowRequest.
//
// Possibly indicates the need for UiStatusError and UiNotificationError subclasses so these
// can be shared.
//
// Need to think about how that would work with sealed classes.

/** Errors from fallible view model actions that the UI will need to show */
sealed interface UiError {
    /** The throwable associated with the error */
    val error: PachliError

    /** The action that failed. Can be resent to retry the action */
    val action: UiAction?

    /** String resource with an error message to show the user */
    @get:StringRes
    val message: Int

    data class Bookmark(
        override val error: PachliError,
        override val action: FallibleStatusAction.Bookmark,
        override val message: Int = R.string.ui_error_bookmark_fmt,
    ) : UiError

    data class Favourite(
        override val error: PachliError,
        override val action: FallibleStatusAction.Favourite,
        override val message: Int = R.string.ui_error_favourite_fmt,
    ) : UiError

    data class Reblog(
        override val error: PachliError,
        override val action: FallibleStatusAction.Reblog,
        override val message: Int = R.string.ui_error_reblog_fmt,
    ) : UiError

    data class VoteInPoll(
        override val error: PachliError,
        override val action: FallibleStatusAction.VoteInPoll,
        override val message: Int = R.string.ui_error_vote_fmt,
    ) : UiError

    data class TranslateStatus(
        override val error: PachliError,
        override val action: FallibleStatusAction.Translate,
        override val message: Int = R.string.ui_error_translate_status_fmt,
    ) : UiError

    data class GetFilters(
        override val error: PachliError,
        override val action: UiAction? = null,
        override val message: Int = R.string.ui_error_filter_v1_load_fmt,
    ) : UiError

    companion object {
        fun make(error: PachliError, action: FallibleUiAction) = when (action) {
            is FallibleStatusAction.Bookmark -> Bookmark(error, action)
            is FallibleStatusAction.Favourite -> Favourite(error, action)
            is FallibleStatusAction.Reblog -> Reblog(error, action)
            is FallibleStatusAction.VoteInPoll -> VoteInPoll(error, action)
            is FallibleStatusAction.Translate -> TranslateStatus(error, action)
        }
    }
}

abstract class TimelineViewModel<T : Any, R : TimelineRepository<T>>(
    savedStateHandle: SavedStateHandle,
    protected val timelineCases: TimelineCases,
    private val eventHub: EventHub,
    protected val accountManager: AccountManager,
    protected val repository: R,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
) : ViewModel() {
    /**
     * The account to load statuses for. Receiving [InfallibleUiAction.LoadPachliAccount]
     * emits in to this.
     */
    val pachliAccountId = MutableSharedFlow<Long>(replay = 1)

    val uiState: StateFlow<UiState>

    /** Flow of statuses that make up the timeline of [timeline] for [pachliAccountId]. */
    abstract val statuses: Flow<PagingData<StatusViewData>>

    /** Flow of changes to statusDisplayOptions, for use by the UI */
    val statusDisplayOptions = statusDisplayOptionsRepository.flow

    /** Flow of user actions received from the UI */
    private val uiAction = MutableSharedFlow<UiAction>()

    private val _uiResult = Channel<Result<UiSuccess, UiError>>()
    val uiResult = _uiResult.receiveAsFlow()

    /** Accept UI actions in to actionStateFlow */
    val accept: (UiAction) -> Unit = { action ->
        viewModelScope.launch { uiAction.emit(action) }
    }

    val timeline: Timeline = savedStateHandle.get<Timeline>(TIMELINE_TAG)!!

    /**
     * Flow of the status ID to use when initially refreshing the list, and where
     * the user's reading position should be restored to. Null if the user's
     * reading position should not be restored, or the reading position was
     * explicitly cleared.
     */
    val initialRefreshStatusId = pachliAccountId.distinctUntilChanged().map { pachliAccountId ->
        timeline.remoteKeyTimelineId?.let {
            timelineCases.getRefreshStatusId(pachliAccountId, it)
        }
    }

    private var filterRemoveReplies = timeline is Timeline.Home && !sharedPreferencesRepository.tabHomeShowReplies
    private var filterRemoveReblogs = timeline is Timeline.Home && !sharedPreferencesRepository.tabHomeShowReblogs
    private var filterRemoveSelfReblogs = timeline is Timeline.Home && !sharedPreferencesRepository.tabHomeShowSelfReblogs

    /**
     * Flow of PachliAccount that updates whenever the underlying account changes, or
     * [pachliAccountId] changes.
     */
    protected val pachliAccountFlow = pachliAccountId.distinctUntilChanged().flatMapLatest { pachliAccountId ->
        accountManager.getPachliAccountFlow(pachliAccountId)
    }.filterNotNull()

    private var contentFilterModel: ContentFilterModel? = null

    init {
        // Handle LoadPachliAcccount. Emit the received account ID to pachliAccountFlow.
        viewModelScope.launch {
            uiAction.filterIsInstance<InfallibleUiAction.LoadPachliAccount>()
                .collectLatest { pachliAccountId.emit(it.pachliAccountId) }
        }

        viewModelScope.launch {
            FilterContext.from(timeline)?.let { filterContext ->
                pachliAccountFlow
                    .distinctUntilChangedBy { it.contentFilters }
                    .fold(false) { reload, account ->
                        contentFilterModel = when (account.contentFilters.version) {
                            ContentFilterVersion.V2 -> ContentFilterModel(filterContext)
                            ContentFilterVersion.V1 -> ContentFilterModel(filterContext, account.contentFilters.contentFilters)
                        }
                        if (reload) repository.invalidate(account.id)
                        true
                    }
            }
        }

        // Handle StatusAction.*
        viewModelScope.launch {
            uiAction.filterIsInstance<FallibleStatusAction>()
                .throttleFirst() // avoid double-taps
                .collect { action ->
                    val result = when (action) {
                        is FallibleStatusAction.Bookmark -> onBookmark(action)
                        is FallibleStatusAction.Favourite -> onFavourite(action)
                        is FallibleStatusAction.Reblog -> onReblog(action)
                        is FallibleStatusAction.VoteInPoll -> onVoteInPoll(action)
                        is FallibleStatusAction.Translate -> onTranslate(action)
                    }.mapEither(
                        { StatusActionSuccess.from(action) },
                        { UiError.make(it, action) },
                    )
                    _uiResult.send(result)
                }
        }

        // Handle events that should refresh the list
        viewModelScope.launch {
            eventHub.events.collectLatest {
                when (it) {
                    is BlockEvent -> _uiResult.send(Ok(UiSuccess.Block))
                    is MuteEvent -> _uiResult.send(Ok(UiSuccess.Mute))
                    is MuteConversationEvent -> _uiResult.send(Ok(UiSuccess.MuteConversation))
                    is StatusComposedEvent -> _uiResult.send(Ok(UiSuccess.StatusSent(it.status)))
                    is StatusEditedEvent -> _uiResult.send(Ok(UiSuccess.StatusEdited(it.status)))
                }
            }
        }

        viewModelScope.launch {
            sharedPreferencesRepository.changes.filterNotNull().collect {
                onPreferenceChanged(it)
            }
        }

        val watchedPrefs = setOf(
            PrefKeys.FAB_HIDE,
            PrefKeys.LAB_REVERSE_TIMELINE,
            PrefKeys.TAB_TAP_BEHAVIOUR,
        )
        uiState = sharedPreferencesRepository.changes
            .filter { watchedPrefs.contains(it) }
            .map {
                UiState(
                    showFabWhileScrolling = !sharedPreferencesRepository.hideFabWhenScrolling,
                    reverseTimeline = sharedPreferencesRepository.getBoolean(PrefKeys.LAB_REVERSE_TIMELINE, false),
                    tabTapBehaviour = sharedPreferencesRepository.tabTapBehaviour,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
                initialValue = UiState(
                    showFabWhileScrolling = !sharedPreferencesRepository.hideFabWhenScrolling,
                    reverseTimeline = sharedPreferencesRepository.reverseTimeline,
                    tabTapBehaviour = sharedPreferencesRepository.tabTapBehaviour,
                ),
            )

        // Save the visible status ID.
        timeline.remoteKeyTimelineId?.let { refreshKeyPrimaryKey ->
            viewModelScope.launch {
                uiAction
                    .filterIsInstance<InfallibleUiAction.SaveVisibleId>()
                    .distinctUntilChanged()
                    .collectLatest { action ->
                        Timber.d("timeline: $timeline, saveRefreshStatusId: %d, %s, %s", action.pachliAccountId, refreshKeyPrimaryKey, action.visibleId)
                        timelineCases.saveRefreshStatusId(action.pachliAccountId, refreshKeyPrimaryKey, action.visibleId)
                    }
            }
        }

        // Clear the saved visible ID (if necessary), and reload from the newest status.
        viewModelScope.launch {
            uiAction
                .filterIsInstance<InfallibleUiAction.LoadNewest>()
                .collectLatest { action ->
                    timeline.remoteKeyTimelineId?.let { refreshKeyPrimaryKey ->
                        Timber.d("timeline: $timeline, saveRefreshStatusId: %d, %s, %s", action.pachliAccountId, refreshKeyPrimaryKey, null)
                        timelineCases.saveRefreshStatusId(action.pachliAccountId, refreshKeyPrimaryKey, null)
                    }
                    Timber.d("Reload because InfallibleUiAction.LoadNewest")
                    _uiResult.send(Ok(UiSuccess.LoadNewest))
                }
        }

        // Undo status translations
        viewModelScope.launch {
            uiAction.filterIsInstance<InfallibleStatusAction.TranslateUndo>().collectLatest(::onUndoTranslate)
        }

        viewModelScope.launch { eventHub.events.collect { handleEvent(it) } }
    }

    /**
     * Bookmark a status.
     *
     * Subclasses should:
     *
     * 1. Opportunistically update the status state.
     * 2. Perform the operation.
     * 3. On failure, revert the status state change from (1).
     */
    abstract suspend fun onBookmark(action: FallibleStatusAction.Bookmark): Result<Status, StatusActionError.Bookmark>

    /**
     * Favourite a status.
     *
     * Subclasses should:
     *
     * 1. Opportunistically update the status state call [invalidate].
     * 2. Perform the operation.
     * 3. On failure, revert the status state change from (1).
     */
    abstract suspend fun onFavourite(action: FallibleStatusAction.Favourite): Result<Status, StatusActionError.Favourite>

    /**
     * Reblog a status.
     *
     * Subclasses should:
     *
     * 1. Opportunistically update the status state.
     * 2. Perform the operation.
     * 3. On failure, revert the status state change from (1).
     */
    abstract suspend fun onReblog(action: FallibleStatusAction.Reblog): Result<Status, StatusActionError.Reblog>

    /**
     * Vote in a poll.
     *
     * Subclasses should:
     *
     * 1. Opportunistically update the status state.
     * 2. Perform the operation.
     * 3. On failure, revert the status state change from (1).
     */
    abstract suspend fun onVoteInPoll(action: FallibleStatusAction.VoteInPoll): Result<Poll, StatusActionError.VoteInPoll>

    /**
     * Translate a status.
     *
     * Subclasses should:
     *
     * 1. Opportunistically update [translationState][StatusViewData.translationState]
     * to [TRANSLATING][app.pachli.core.database.model.TranslationState.TRANSLATING].
     * 2. Perform the operation.
     * 3. On success, update [translationState][StatusViewData.translationState]
     * to [TranslationState.SHOW_TRANSLATION][app.pachli.core.database.model.TranslationState.SHOW_TRANSLATION]
     * and update [translation][StatusViewData.translation].     .
     * 3. Revert the status state change from (1) if the operation failed.
     */
    abstract suspend fun onTranslate(action: FallibleStatusAction.Translate): Result<TranslatedStatus, TranslatorError> // = timelineCases.translate(action.statusViewData)

    /**
     * Undo translating a status.
     *
     * Subclasses should:
     *
     * 1. Update [translationState][StatusViewData.translationState]
     * to [SHOW_ORIGINAL][app.pachli.core.database.model.TranslationState.SHOW_ORIGINAL].
     */
    abstract suspend fun onUndoTranslate(action: InfallibleStatusAction.TranslateUndo)

    /**
     * Sets the expanded state of [statusViewData] in [OfflineFirstStatusRepository] to [isExpanded] and
     * invalidates the repository.
     */
    abstract fun onChangeExpanded(isExpanded: Boolean, statusViewData: StatusViewData)

    /**
     * Sets the content-showing state of [statusViewData] in [OfflineFirstStatusRepository] to
     * [isShowing] and invalidates the repository.
     */
    abstract fun onChangeContentShowing(isShowing: Boolean, statusViewData: StatusViewData)

    abstract fun onChangeAttachmentBlurDecision(viewData: StatusViewData, newDecision: AttachmentBlurDecision)

    /**
     * Sets the collapsed state of [statusViewData] in [OfflineFirstStatusRepository] to [isCollapsed] and
     * invalidates the repository.
     */
    abstract fun onContentCollapsed(isCollapsed: Boolean, statusViewData: StatusViewData)

    abstract fun removeAllByAccountId(pachliAccountId: Long, accountId: String)

    abstract fun removeAllByInstance(pachliAccountId: Long, instance: String)

    abstract fun removeStatusWithId(id: String)

    abstract fun handleReblogEvent(reblogEvent: ReblogEvent)

    abstract fun handleFavEvent(favEvent: FavoriteEvent)

    abstract fun handleBookmarkEvent(bookmarkEvent: BookmarkEvent)

    abstract fun handlePinEvent(pinEvent: PinEvent)

    abstract fun clearWarning(statusViewData: StatusViewData)

    /** Triggered when currently displayed data must be reloaded. */
    protected abstract suspend fun invalidate(pachliAccountId: Long)

    /**
     * @return The correct [FilterAction] for [timelineStatus] given the user's
     * preferences and any filters attached to the status.
     */
    protected fun shouldFilterStatus(timelineStatus: TimelineStatusWithAccount): FilterAction {
        // Local user preferences first

        // Remove self-boosts.
        if (timelineStatus.account.serverId == timelineStatus.reblogAccount?.serverId && filterRemoveSelfReblogs) {
            return FilterAction.HIDE
        }

        val status = timelineStatus.status
        // Remove replies
        if (status.inReplyToId != null && filterRemoveReplies) return FilterAction.HIDE

        // Remove boosts
        if (status.reblogged && filterRemoveReblogs) return FilterAction.HIDE

        // Apply content filters.
        return contentFilterModel?.filterActionFor(status) ?: FilterAction.NONE
    }

    // TODO: Update this so that the list of UIPrefs is correct
    private suspend fun onPreferenceChanged(key: String) {
        val pachliAccountId = pachliAccountId.replayCache.firstOrNull() ?: return

        when (key) {
            PrefKeys.TAB_FILTER_HOME_REPLIES -> {
                val filter = sharedPreferencesRepository.getBoolean(PrefKeys.TAB_FILTER_HOME_REPLIES, true)
                val oldRemoveReplies = filterRemoveReplies
                filterRemoveReplies = timeline is Timeline.Home && !filter
                if (oldRemoveReplies != filterRemoveReplies) {
                    Timber.d("Reload because TAB_FILTER_HOME_REPLIES changed")
                    repository.invalidate(pachliAccountId)
                }
            }
            PrefKeys.TAB_FILTER_HOME_BOOSTS -> {
                val filter = sharedPreferencesRepository.getBoolean(PrefKeys.TAB_FILTER_HOME_BOOSTS, true)
                val oldRemoveReblogs = filterRemoveReblogs
                filterRemoveReblogs = timeline is Timeline.Home && !filter
                if (oldRemoveReblogs != filterRemoveReblogs) {
                    Timber.d("Reload because TAB_FILTER_HOME_BOOSTS changed")
                    repository.invalidate(pachliAccountId)
                }
            }
            PrefKeys.TAB_SHOW_HOME_SELF_BOOSTS -> {
                val filter = sharedPreferencesRepository.getBoolean(PrefKeys.TAB_SHOW_HOME_SELF_BOOSTS, true)
                val oldRemoveSelfReblogs = filterRemoveSelfReblogs
                filterRemoveSelfReblogs = timeline is Timeline.Home && !filter
                if (oldRemoveSelfReblogs != filterRemoveSelfReblogs) {
                    Timber.d("Reload because TAB_SHOW_SOME_SELF_BOOSTS changed")
                    repository.invalidate(pachliAccountId)
                }
            }
        }
    }

    private suspend fun handleEvent(event: Event) {
        when (event) {
            is FavoriteEvent -> handleFavEvent(event)
            is ReblogEvent -> handleReblogEvent(event)
            is BookmarkEvent -> handleBookmarkEvent(event)
            is PinEvent -> handlePinEvent(event)
            is MuteConversationEvent -> {
                Timber.d("Reload because MuteConversationEvent")
                repository.invalidate(event.pachliAccountId)
            }
            is UnfollowEvent -> {
                if (timeline is Timeline.Home) {
                    removeAllByAccountId(event.pachliAccountId, event.accountId)
                }
            }
            is BlockEvent -> {
                if (timeline !is Timeline.User) {
                    removeAllByAccountId(event.pachliAccountId, event.accountId)
                }
            }
            is MuteEvent -> {
                if (timeline !is Timeline.User && timeline !is Timeline.Bookmarks && timeline !is Timeline.Favourites) {
                    removeAllByAccountId(event.pachliAccountId, event.accountId)
                }
            }
            is DomainMuteEvent -> {
                if (timeline !is Timeline.User) {
                    removeAllByInstance(event.pachliAccountId, event.instance)
                }
            }
            is StatusDeletedEvent -> {
                if (timeline !is Timeline.User) {
                    removeStatusWithId(event.statusId)
                }
            }
        }
    }

    companion object {
        /** Tag for the timelineKind in `savedStateHandle` */
        @VisibleForTesting(VisibleForTesting.PRIVATE)
        const val TIMELINE_TAG = "timeline"

        /** Create extras for this view model */
        fun creationExtras(timeline: Timeline) = bundleOf(
            TIMELINE_TAG to timeline,
        )
    }
}
