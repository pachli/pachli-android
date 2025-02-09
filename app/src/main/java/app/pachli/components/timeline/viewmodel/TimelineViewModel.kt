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
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.Loadable
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.data.repository.StatusRepository
import app.pachli.core.database.model.AccountEntity
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
import app.pachli.core.model.ContentFilterVersion
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterContext
import app.pachli.core.model.Timeline
import app.pachli.core.network.model.Poll
import app.pachli.core.network.model.Status
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.preferences.TabTapBehaviour
import app.pachli.network.ContentFilterModel
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
    data object LoadNewest : InfallibleUiAction

    data class TranslateUndo(val statusViewData: StatusViewData) : InfallibleUiAction
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

/** Actions the user can trigger on an individual status */
sealed interface StatusAction : FallibleUiAction {
    // TODO: Include a property for the PachliAccountId the action is being performed as.

    val statusViewData: StatusViewData

    /** Set the bookmark state for a status */
    data class Bookmark(val state: Boolean, override val statusViewData: StatusViewData) :
        StatusAction

    /** Set the favourite state for a status */
    data class Favourite(val state: Boolean, override val statusViewData: StatusViewData) :
        StatusAction

    /** Set the reblog state for a status */
    data class Reblog(val state: Boolean, override val statusViewData: StatusViewData) :
        StatusAction

    /** Vote in a poll */
    data class VoteInPoll(
        val poll: Poll,
        val choices: List<Int>,
        override val statusViewData: StatusViewData,
    ) : StatusAction

    /** Translate a status */
    data class Translate(override val statusViewData: StatusViewData) : StatusAction
}

/** Changes to a status' visible state after API calls */
sealed interface StatusActionSuccess : UiSuccess {
    val action: StatusAction

    data class Bookmark(override val action: StatusAction.Bookmark) : StatusActionSuccess

    data class Favourite(override val action: StatusAction.Favourite) : StatusActionSuccess

    data class Reblog(override val action: StatusAction.Reblog) : StatusActionSuccess

    data class VoteInPoll(override val action: StatusAction.VoteInPoll) : StatusActionSuccess

    data class Translate(override val action: StatusAction.Translate) : StatusActionSuccess

    companion object {
        fun from(action: StatusAction) = when (action) {
            is StatusAction.Bookmark -> Bookmark(action)
            is StatusAction.Favourite -> Favourite(action)
            is StatusAction.Reblog -> Reblog(action)
            is StatusAction.VoteInPoll -> VoteInPoll(action)
            is StatusAction.Translate -> Translate(action)
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
        override val action: StatusAction.Bookmark,
        override val message: Int = R.string.ui_error_bookmark_fmt,
    ) : UiError

    data class Favourite(
        override val error: PachliError,
        override val action: StatusAction.Favourite,
        override val message: Int = R.string.ui_error_favourite_fmt,
    ) : UiError

    data class Reblog(
        override val error: PachliError,
        override val action: StatusAction.Reblog,
        override val message: Int = R.string.ui_error_reblog_fmt,
    ) : UiError

    data class VoteInPoll(
        override val error: PachliError,
        override val action: StatusAction.VoteInPoll,
        override val message: Int = R.string.ui_error_vote_fmt,
    ) : UiError

    data class TranslateStatus(
        override val error: PachliError,
        override val action: StatusAction.Translate,
        override val message: Int = R.string.ui_error_translate_status_fmt,
    ) : UiError

    data class GetFilters(
        override val error: PachliError,
        override val action: UiAction? = null,
        override val message: Int = R.string.ui_error_filter_v1_load_fmt,
    ) : UiError

    companion object {
        fun make(error: PachliError, action: FallibleUiAction) = when (action) {
            is StatusAction.Bookmark -> Bookmark(error, action)
            is StatusAction.Favourite -> Favourite(error, action)
            is StatusAction.Reblog -> Reblog(error, action)
            is StatusAction.VoteInPoll -> VoteInPoll(error, action)
            is StatusAction.Translate -> TranslateStatus(error, action)
        }
    }
}

abstract class TimelineViewModel<T : Any>(
    savedStateHandle: SavedStateHandle,
    private val timelineCases: TimelineCases,
    private val eventHub: EventHub,
    protected val accountManager: AccountManager,
    private val repository: TimelineRepository<T>,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
    private val statusRepository: StatusRepository,
) : ViewModel() {
    val uiState: StateFlow<UiState>

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

    private var filterRemoveReplies = false
    private var filterRemoveReblogs = false
    private var filterRemoveSelfReblogs = false

    protected val activeAccount: AccountEntity
        get() {
            return accountManager.activeAccount!!
        }

    protected val accountFlow = accountManager.activeAccountFlow
        .filterIsInstance<Loadable.Loaded<AccountEntity?>>()
        .filter { it.data != null }
        .distinctUntilChangedBy { it.data?.id!! }

    private var contentFilterModel: ContentFilterModel? = null

    init {
        viewModelScope.launch {
            FilterContext.from(timeline)?.let { filterContext ->
                accountManager.activePachliAccountFlow
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
            uiAction.filterIsInstance<StatusAction>()
                .throttleFirst() // avoid double-taps
                .collect { action ->
                    val result = when (action) {
                        is StatusAction.Bookmark ->
                            statusRepository.bookmark(
                                action.statusViewData.pachliAccountId,
                                action.statusViewData.actionableId,
                                action.state,
                            )

                        is StatusAction.Favourite ->
                            statusRepository.favourite(
                                action.statusViewData.pachliAccountId,
                                action.statusViewData.actionableId,
                                action.state,
                            )

                        is StatusAction.Reblog ->
                            statusRepository.reblog(
                                action.statusViewData.pachliAccountId,
                                action.statusViewData.actionableId,
                                action.state,
                            )

                        is StatusAction.VoteInPoll ->
                            timelineCases.voteInPoll(
                                action.statusViewData.actionableId,
                                action.poll.id,
                                action.choices,
                            )

                        is StatusAction.Translate -> {
                            timelineCases.translate(action.statusViewData)
                        }
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
                    showFabWhileScrolling = !sharedPreferencesRepository.getBoolean(PrefKeys.FAB_HIDE, false),
                    reverseTimeline = sharedPreferencesRepository.getBoolean(PrefKeys.LAB_REVERSE_TIMELINE, false),
                    tabTapBehaviour = sharedPreferencesRepository.tabTapBehaviour,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
                initialValue = UiState(
                    showFabWhileScrolling = !sharedPreferencesRepository.getBoolean(PrefKeys.FAB_HIDE, false),
                    reverseTimeline = sharedPreferencesRepository.getBoolean(PrefKeys.LAB_REVERSE_TIMELINE, false),
                    tabTapBehaviour = sharedPreferencesRepository.tabTapBehaviour,
                ),
            )

        if (timeline is Timeline.Home) {
            // Note the variable is "true if filter" but the underlying preference/settings text is "true if show"
            filterRemoveReplies =
                !sharedPreferencesRepository.getBoolean(PrefKeys.TAB_FILTER_HOME_REPLIES, true)
            filterRemoveReblogs =
                !sharedPreferencesRepository.getBoolean(PrefKeys.TAB_FILTER_HOME_BOOSTS, true)
            filterRemoveSelfReblogs =
                !sharedPreferencesRepository.getBoolean(PrefKeys.TAB_SHOW_HOME_SELF_BOOSTS, true)
        }

        // Save the visible status ID (if it's the home timeline)
        if (timeline == Timeline.Home) {
            viewModelScope.launch {
                uiAction
                    .filterIsInstance<InfallibleUiAction.SaveVisibleId>()
                    .distinctUntilChanged()
                    .collectLatest { action ->
                        Timber.d("setLastVisibleHomeTimelineStatusId: %d, %s", activeAccount.id, action.visibleId)
                        timelineCases.saveRefreshKey(activeAccount.id, action.visibleId)
                    }
            }
        }

        // Clear the saved visible ID (if necessary), and reload from the newest status.
        viewModelScope.launch {
            uiAction
                .filterIsInstance<InfallibleUiAction.LoadNewest>()
                .collectLatest {
                    if (timeline == Timeline.Home) {
                        timelineCases.saveRefreshKey(activeAccount.id, null)
                    }
                    Timber.d("Reload because InfallibleUiAction.LoadNewest")
                    _uiResult.send(Ok(UiSuccess.LoadNewest))
                }
        }

        // Undo status translations
        viewModelScope.launch {
            uiAction.filterIsInstance<InfallibleUiAction.TranslateUndo>().collectLatest {
                timelineCases.translateUndo(activeAccount.id, it.statusViewData)
            }
        }

        viewModelScope.launch { eventHub.events.collect { handleEvent(it) } }
    }

    abstract fun updatePoll(newPoll: Poll, status: StatusViewData)

    abstract fun changeExpanded(expanded: Boolean, status: StatusViewData)

    abstract fun changeContentShowing(isShowing: Boolean, status: StatusViewData)

    abstract fun changeContentCollapsed(isCollapsed: Boolean, status: StatusViewData)

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

    protected fun shouldFilterStatus(status: Status): FilterAction {
        return if (
            (status.inReplyToId != null && filterRemoveReplies) ||
            (status.reblog != null && filterRemoveReblogs) ||
            // To determine if the boost is boosting your own toot
            ((status.account.id == status.reblog?.account?.id) && filterRemoveSelfReblogs)
        ) {
            FilterAction.HIDE
        } else {
            contentFilterModel?.filterActionFor(status) ?: FilterAction.NONE
        }
    }

    // TODO: Update this so that the list of UIPrefs is correct
    private suspend fun onPreferenceChanged(key: String) {
        when (key) {
            PrefKeys.TAB_FILTER_HOME_REPLIES -> {
                val filter = sharedPreferencesRepository.getBoolean(PrefKeys.TAB_FILTER_HOME_REPLIES, true)
                val oldRemoveReplies = filterRemoveReplies
                filterRemoveReplies = timeline is Timeline.Home && !filter
                if (oldRemoveReplies != filterRemoveReplies) {
                    Timber.d("Reload because TAB_FILTER_HOME_REPLIES changed")
                    repository.invalidate(activeAccount.id)
                }
            }
            PrefKeys.TAB_FILTER_HOME_BOOSTS -> {
                val filter = sharedPreferencesRepository.getBoolean(PrefKeys.TAB_FILTER_HOME_BOOSTS, true)
                val oldRemoveReblogs = filterRemoveReblogs
                filterRemoveReblogs = timeline is Timeline.Home && !filter
                if (oldRemoveReblogs != filterRemoveReblogs) {
                    Timber.d("Reload because TAB_FILTER_HOME_BOOSTS changed")
                    repository.invalidate(activeAccount.id)
                }
            }
            PrefKeys.TAB_SHOW_HOME_SELF_BOOSTS -> {
                val filter = sharedPreferencesRepository.getBoolean(PrefKeys.TAB_SHOW_HOME_SELF_BOOSTS, true)
                val oldRemoveSelfReblogs = filterRemoveSelfReblogs
                filterRemoveSelfReblogs = timeline is Timeline.Home && !filter
                if (oldRemoveSelfReblogs != filterRemoveSelfReblogs) {
                    Timber.d("Reload because TAB_SHOW_SOME_SELF_BOOSTS changed")
                    repository.invalidate(activeAccount.id)
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
                repository.invalidate(activeAccount.id)
            }
            is UnfollowEvent -> {
                if (timeline is Timeline.Home) {
                    val id = event.accountId
                    removeAllByAccountId(activeAccount.id, id)
                }
            }
            is BlockEvent -> {
                if (timeline !is Timeline.User) {
                    val id = event.accountId
                    removeAllByAccountId(activeAccount.id, id)
                }
            }
            is MuteEvent -> {
                if (timeline !is Timeline.User) {
                    val id = event.accountId
                    removeAllByAccountId(activeAccount.id, id)
                }
            }
            is DomainMuteEvent -> {
                if (timeline !is Timeline.User) {
                    val instance = event.instance
                    removeAllByInstance(activeAccount.id, instance)
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

        fun filterContextMatchesKind(
            timeline: Timeline,
            filterContext: List<FilterContext>,
        ): Boolean {
            return filterContext.contains(FilterContext.from(timeline))
        }
    }
}
