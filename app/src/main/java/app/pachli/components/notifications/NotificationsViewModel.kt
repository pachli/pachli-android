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

package app.pachli.components.notifications

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import app.pachli.R
import app.pachli.appstore.BlockEvent
import app.pachli.appstore.EventHub
import app.pachli.appstore.MuteConversationEvent
import app.pachli.appstore.MuteEvent
import app.pachli.core.common.extensions.throttleFirst
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.ContentFiltersRepository
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.model.ContentFilterVersion
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterContext
import app.pachli.core.network.model.Notification
import app.pachli.core.network.model.Poll
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.preferences.TabTapBehaviour
import app.pachli.network.ContentFilterModel
import app.pachli.usecase.TimelineCases
import app.pachli.util.deserialize
import app.pachli.util.serialize
import app.pachli.viewdata.NotificationViewData
import app.pachli.viewdata.StatusViewData
import at.connyduck.calladapter.networkresult.getOrThrow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import timber.log.Timber

data class UiState(
    /** Filtered notification types */
    val activeFilter: Set<Notification.Type> = emptySet(),

    /** True if the FAB should be shown while scrolling */
    val showFabWhileScrolling: Boolean = true,

    /** User's preference for behaviour when tapping a tab. */
    val tabTapBehaviour: TabTapBehaviour = TabTapBehaviour.JUMP_TO_NEXT_PAGE,
)

/** Preferences the UI reacts to */
data class UiPrefs(
    val showFabWhileScrolling: Boolean,
) {
    companion object {
        /** Relevant preference keys. Changes to any of these trigger a display update */
        val prefKeys = setOf(
            PrefKeys.FAB_HIDE,
            PrefKeys.TAB_TAP_BEHAVIOUR,
        )
    }
}

/** Parent class for all UI actions, fallible or infallible. */
sealed interface UiAction

/** Actions the user can trigger from the UI. These actions may fail. */
sealed interface FallibleUiAction : UiAction {
    /** Clear all notifications */
    data object ClearNotifications : FallibleUiAction
}

/**
 * Actions the user can trigger from the UI that either cannot fail, or if they do fail,
 * do not show an error.
 */
sealed interface InfallibleUiAction : UiAction {
    /** Apply a new filter to the notification list */
    // This saves the list to the local database, which triggers a refresh of the data.
    // Saving the data can't fail, which is why this is infallible. Refreshing the
    // data may fail, but that's handled by the paging system / adapter refresh logic.
    data class ApplyFilter(val filter: Set<Notification.Type>) : InfallibleUiAction

    /**
     * User is leaving the fragment, save the ID of the visible notification.
     *
     * Infallible because if it fails there's nowhere to show the error, and nothing the user
     * can do.
     */
    data class SaveVisibleId(val visibleId: String) : InfallibleUiAction

    /** Ignore the saved reading position, load the page with the newest items */
    // Resets the account's `lastNotificationId`, which can't fail, which is why this is
    // infallible. Reloading the data may fail, but that's handled by the paging system /
    // adapter refresh logic.
    data object LoadNewest : InfallibleUiAction
}

/** Actions the user can trigger on an individual notification. These may fail. */
sealed interface NotificationAction : FallibleUiAction {
    data class AcceptFollowRequest(val accountId: String) : NotificationAction

    data class RejectFollowRequest(val accountId: String) : NotificationAction
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
}

/** The result of a successful action on a notification */
sealed interface NotificationActionSuccess : UiSuccess {
    /** String resource with an error message to show the user */
    @get:StringRes
    val msg: Int

    /**
     * The original action, in case additional information is required from it to display the
     * message.
     */
    val action: NotificationAction

    data class AcceptFollowRequest(
        override val action: NotificationAction,
        override val msg: Int = R.string.ui_success_accepted_follow_request,
    ) : NotificationActionSuccess

    data class RejectFollowRequest(
        override val action: NotificationAction,
        override val msg: Int = R.string.ui_success_rejected_follow_request,
    ) : NotificationActionSuccess

    companion object {
        fun from(action: NotificationAction) = when (action) {
            is NotificationAction.AcceptFollowRequest -> AcceptFollowRequest(action)
            is NotificationAction.RejectFollowRequest -> RejectFollowRequest(action)
        }
    }
}

/** Actions the user can trigger on an individual status */
sealed interface StatusAction : FallibleUiAction {
    val statusViewData: StatusViewData

    /** Set the bookmark state for a status */
    data class Bookmark(val state: Boolean, override val statusViewData: StatusViewData) : StatusAction

    /** Set the favourite state for a status */
    data class Favourite(val state: Boolean, override val statusViewData: StatusViewData) : StatusAction

    /** Set the reblog state for a status */
    data class Reblog(val state: Boolean, override val statusViewData: StatusViewData) : StatusAction

    /** Vote in a poll */
    data class VoteInPoll(
        val poll: Poll,
        val choices: List<Int>,
        override val statusViewData: StatusViewData,
    ) : StatusAction
}

/** Changes to a status' visible state after API calls */
sealed interface StatusActionSuccess : UiSuccess {
    val action: StatusAction

    data class Bookmark(override val action: StatusAction.Bookmark) : StatusActionSuccess

    data class Favourite(override val action: StatusAction.Favourite) : StatusActionSuccess

    data class Reblog(override val action: StatusAction.Reblog) : StatusActionSuccess

    data class VoteInPoll(override val action: StatusAction.VoteInPoll) : StatusActionSuccess

    companion object {
        fun from(action: StatusAction) = when (action) {
            is StatusAction.Bookmark -> Bookmark(action)
            is StatusAction.Favourite -> Favourite(action)
            is StatusAction.Reblog -> Reblog(action)
            is StatusAction.VoteInPoll -> VoteInPoll(action)
        }
    }
}

/** Errors from fallible view model actions that the UI will need to show */
sealed interface UiError {
    /** The exception associated with the error */
    val throwable: Throwable

    /** The action that failed. Can be resent to retry the action */
    val action: UiAction?

    /** String resource with an error message to show the user */
    @get:StringRes
    val message: Int

    data class ClearNotifications(
        override val throwable: Throwable,
        override val action: FallibleUiAction.ClearNotifications = FallibleUiAction.ClearNotifications,
        override val message: Int = R.string.ui_error_clear_notifications,
    ) : UiError

    data class Bookmark(
        override val throwable: Throwable,
        override val action: StatusAction.Bookmark,
        override val message: Int = R.string.ui_error_bookmark_fmt,
    ) : UiError

    data class Favourite(
        override val throwable: Throwable,
        override val action: StatusAction.Favourite,
        override val message: Int = R.string.ui_error_favourite_fmt,
    ) : UiError

    data class Reblog(
        override val throwable: Throwable,
        override val action: StatusAction.Reblog,
        override val message: Int = R.string.ui_error_reblog_fmt,
    ) : UiError

    data class VoteInPoll(
        override val throwable: Throwable,
        override val action: StatusAction.VoteInPoll,
        override val message: Int = R.string.ui_error_vote_fmt,
    ) : UiError

    data class AcceptFollowRequest(
        override val throwable: Throwable,
        override val action: NotificationAction.AcceptFollowRequest,
        override val message: Int = R.string.ui_error_accept_follow_request,
    ) : UiError

    data class RejectFollowRequest(
        override val throwable: Throwable,
        override val action: NotificationAction.RejectFollowRequest,
        override val message: Int = R.string.ui_error_reject_follow_request,
    ) : UiError

    data class GetFilters(
        override val throwable: Throwable,
        override val action: UiAction? = null,
        override val message: Int = R.string.ui_error_filter_v1_load_fmt,
    ) : UiError

    companion object {
        fun make(throwable: Throwable, action: FallibleUiAction) = when (action) {
            is StatusAction.Bookmark -> Bookmark(throwable, action)
            is StatusAction.Favourite -> Favourite(throwable, action)
            is StatusAction.Reblog -> Reblog(throwable, action)
            is StatusAction.VoteInPoll -> VoteInPoll(throwable, action)
            is NotificationAction.AcceptFollowRequest -> AcceptFollowRequest(throwable, action)
            is NotificationAction.RejectFollowRequest -> RejectFollowRequest(throwable, action)
            FallibleUiAction.ClearNotifications -> ClearNotifications(throwable)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    // TODO: Context is required because handling filter errors needs to
    // format a resource string. As soon as that is removed this can be removed.
    @ApplicationContext private val context: Context,
    private val repository: NotificationsRepository,
    private val accountManager: AccountManager,
    private val timelineCases: TimelineCases,
    private val eventHub: EventHub,
    private val contentFiltersRepository: ContentFiltersRepository,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
) : ViewModel() {
    /** The account to display notifications for */
    val account = accountManager.activeAccount!!

    val uiState: StateFlow<UiState>

    /** Flow of changes to statusDisplayOptions, for use by the UI */
    val statusDisplayOptions = statusDisplayOptionsRepository.flow

    val pagingData: Flow<PagingData<NotificationViewData>>

    /** Flow of user actions received from the UI */
    private val uiAction = MutableSharedFlow<UiAction>()

    /** Flow that can be used to trigger a full reload */
    private val reload = MutableStateFlow(0)

    /** Flow of successful action results */
    // Note: This is a SharedFlow instead of a StateFlow because success state does not need to be
    // retained. A message is shown once to a user and then dismissed. Re-collecting the flow
    // (e.g., after a device orientation change) should not re-show the most recent success
    // message, as it will be confusing to the user.
    val uiSuccess = MutableSharedFlow<UiSuccess>()

    @Suppress("ktlint:standard:property-naming")
    /** Channel for error results */
    // Errors are sent to a channel to ensure that any errors that occur *before* there are any
    // subscribers are retained. If this was a SharedFlow any errors would be dropped, and if it
    // was a StateFlow any errors would be retained, and there would need to be an explicit
    // mechanism to dismiss them.
    private val _uiErrorChannel = Channel<UiError>()

    /** Expose UI errors as a flow */
    val uiError = _uiErrorChannel.receiveAsFlow()

    /** Accept UI actions in to actionStateFlow */
    val accept: (UiAction) -> Unit = { action ->
        viewModelScope.launch { uiAction.emit(action) }
    }

    private var contentFilterModel: ContentFilterModel? = null

    init {
        // Handle changes to notification filters
        val notificationFilter = uiAction
            .filterIsInstance<InfallibleUiAction.ApplyFilter>()
            .distinctUntilChanged()
            // Save each change back to the active account
            .onEach { action ->
                Timber.d("notificationFilter: %s", action)
                accountManager.setNotificationsFilter(account.id, serialize(action.filter))
            }
            // Load the initial filter from the active account
            .onStart {
                emit(
                    InfallibleUiAction.ApplyFilter(
                        filter = deserialize(account.notificationsFilter),
                    ),
                )
            }

        // Reset the last notification ID to "0" to fetch the newest notifications, and
        // increment `reload` to trigger creation of a new PagingSource.
        viewModelScope.launch {
            uiAction
                .filterIsInstance<InfallibleUiAction.LoadNewest>()
                .collectLatest {
                    accountManager.setLastNotificationId(account.id, "0")
                    reload.getAndUpdate { it + 1 }
                    repository.invalidate()
                }
        }

        // Save the visible notification ID
        viewModelScope.launch {
            uiAction
                .filterIsInstance<InfallibleUiAction.SaveVisibleId>()
                .distinctUntilChanged()
                .collectLatest { action ->
                    Timber.d("Saving visible ID: %s, active account = %d", action.visibleId, account.id)
                    accountManager.setLastNotificationId(account.id, action.visibleId)
                }
        }

        // Handle UiAction.ClearNotifications
        viewModelScope.launch {
            uiAction.filterIsInstance<FallibleUiAction.ClearNotifications>()
                .collectLatest {
                    try {
                        repository.clearNotifications().apply {
                            if (this.isSuccessful) {
                                repository.invalidate()
                            } else {
                                _uiErrorChannel.send(UiError.make(HttpException(this), it))
                            }
                        }
                    } catch (e: Exception) {
                        _uiErrorChannel.send(UiError.make(e, it))
                    }
                }
        }

        // Handle NotificationAction.*
        viewModelScope.launch {
            uiAction.filterIsInstance<NotificationAction>()
                .throttleFirst()
                .collect { action ->
                    try {
                        when (action) {
                            is NotificationAction.AcceptFollowRequest ->
                                timelineCases.acceptFollowRequest(action.accountId)
                            is NotificationAction.RejectFollowRequest ->
                                timelineCases.rejectFollowRequest(action.accountId)
                        }
                        uiSuccess.emit(NotificationActionSuccess.from(action))
                    } catch (e: Exception) {
                        _uiErrorChannel.send(UiError.make(e, action))
                    }
                }
        }

        // Handle StatusAction.*
        viewModelScope.launch {
            uiAction.filterIsInstance<StatusAction>()
                .throttleFirst() // avoid double-taps
                .collect { action ->
                    try {
                        when (action) {
                            is StatusAction.Bookmark ->
                                timelineCases.bookmark(
                                    action.statusViewData.actionableId,
                                    action.state,
                                )
                            is StatusAction.Favourite ->
                                timelineCases.favourite(
                                    action.statusViewData.actionableId,
                                    action.state,
                                )
                            is StatusAction.Reblog ->
                                timelineCases.reblog(
                                    action.statusViewData.actionableId,
                                    action.state,
                                )
                            is StatusAction.VoteInPoll ->
                                timelineCases.voteInPoll(
                                    action.statusViewData.actionableId,
                                    action.poll.id,
                                    action.choices,
                                )
                        }.getOrThrow()
                        uiSuccess.emit(StatusActionSuccess.from(action))
                    } catch (t: Throwable) {
                        _uiErrorChannel.send(UiError.make(t, action))
                    }
                }
        }

        // Fetch the status filters
        viewModelScope.launch {
            accountManager.activePachliAccountFlow
                .distinctUntilChangedBy { it.contentFilters }
                .collect { account ->
                    contentFilterModel = when (account.contentFilters.version) {
                        ContentFilterVersion.V2 -> ContentFilterModel(FilterContext.NOTIFICATIONS)
                        ContentFilterVersion.V1 -> ContentFilterModel(FilterContext.NOTIFICATIONS, account.contentFilters.contentFilters)
                    }
                }
        }

        // Handle events that should refresh the list
        viewModelScope.launch {
            eventHub.events.collectLatest {
                when (it) {
                    is BlockEvent -> uiSuccess.emit(UiSuccess.Block)
                    is MuteEvent -> uiSuccess.emit(UiSuccess.Mute)
                    is MuteConversationEvent -> uiSuccess.emit(UiSuccess.MuteConversation)
                }
            }
        }

        // Re-fetch notifications if either of `notificationFilter` or `reload` flows have
        // new items.
        pagingData = combine(notificationFilter, reload) { action, _ -> action }
            .flatMapLatest { action ->
                getNotifications(filters = action.filter, initialKey = getInitialKey())
            }.cachedIn(viewModelScope)

        uiState = combine(notificationFilter, getUiPrefs()) { filter, _ ->
            UiState(
                activeFilter = filter.filter,
                showFabWhileScrolling = !sharedPreferencesRepository.getBoolean(PrefKeys.FAB_HIDE, false),
                tabTapBehaviour = sharedPreferencesRepository.tabTapBehaviour,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
            initialValue = UiState(),
        )
    }

    private fun getNotifications(
        filters: Set<Notification.Type>,
        initialKey: String? = null,
    ): Flow<PagingData<NotificationViewData>> {
        Timber.d("getNotifications: %s", initialKey)
        return repository.getNotificationsStream(filter = filters, initialKey = initialKey)
            .map { pagingData ->
                pagingData.map { notification ->
                    val filterAction = notification.status?.actionableStatus?.let { contentFilterModel?.filterActionFor(it) } ?: FilterAction.NONE
                    NotificationViewData.from(
                        notification,
                        isShowingContent = statusDisplayOptions.value.showSensitiveMedia ||
                            !(notification.status?.actionableStatus?.sensitive ?: false),
                        isExpanded = statusDisplayOptions.value.openSpoiler,
                        isCollapsed = true,
                        filterAction = filterAction,
                    )
                }.filter {
                    it.statusViewData?.filterAction != FilterAction.HIDE
                }
            }
    }

    // The database stores "0" as the last notification ID if notifications have not been
    // fetched. Convert to null to ensure a full fetch in this case
    private fun getInitialKey(): String? {
        val initialKey = when (val id = account.lastNotificationId) {
            "0" -> null
            else -> id
        }
        Timber.d("Restoring at %s", initialKey)
        return initialKey
    }

    /**
     * @return Flow of relevant preferences that change the UI
     */
    private fun getUiPrefs() = sharedPreferencesRepository.changes
        .filter { UiPrefs.prefKeys.contains(it) }
        .onStart { emit(null) }
}
