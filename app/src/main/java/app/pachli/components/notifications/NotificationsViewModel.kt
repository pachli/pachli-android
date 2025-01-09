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
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.data.notifications.NotificationRepository
import app.pachli.core.data.notifications.from
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.PachliAccount
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.NotificationEntity
import app.pachli.core.model.AccountFilterDecision
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
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapEither
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

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
    data class ApplyFilter(
        val pachliAccountId: Long,
        val filter: Set<Notification.Type>,
    ) : InfallibleUiAction

    /**
     * User is leaving the fragment, save the ID of the visible notification.
     *
     * Infallible because if it fails there's nowhere to show the error, and nothing the user
     * can do.
     */
    data class SaveVisibleId(
        val pachliAccountId: Long,
        val visibleId: String,
    ) : InfallibleUiAction

    /** Ignore the saved reading position, load the page with the newest items */
    // Resets the account's refresh key, which can't fail, which is why this is
    // infallible. Reloading the data may fail, but that's handled by the paging system /
    // adapter refresh logic.
    data object LoadNewest : InfallibleUiAction

    /** Set the "collapsed" state (if the status content > 500 chars) */
    data class SetContentCollapsed(
        val pachliAccountId: Long,
        val statusViewData: StatusViewData,
        val isCollapsed: Boolean,
    ) : InfallibleUiAction

    /** Set whether to show attached media. */
    data class SetShowingContent(
        val pachliAccountId: Long,
        val statusViewData: StatusViewData,
        val isShowingContent: Boolean,
    ) : InfallibleUiAction

    /** Set whether to show just the content warning, or the full content. */
    data class SetExpanded(
        val pachliAccountId: Long,
        val statusViewData: StatusViewData,
        val isExpanded: Boolean,
    ) : InfallibleUiAction

    /** Clear the content filter. */
    data class ClearContentFilter(
        val pachliAccountId: Long,
        val notificationId: String,
    ) : InfallibleUiAction

    /** Override the account filter and show the content. */
    data class OverrideAccountFilter(
        val pachliAccountId: Long,
        val notificationId: String,
        val accountFilterDecision: AccountFilterDecision,
    ) : InfallibleUiAction
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

    /**
     * Resetting the reading position completed, the UI should refresh the adapter
     * to load content at the new position.
     */
    data object LoadNewest : UiSuccess
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
@HiltViewModel(assistedFactory = NotificationsViewModel.Factory::class)
class NotificationsViewModel @AssistedInject constructor(
    private val repository: NotificationRepository,
    private val accountManager: AccountManager,
    private val timelineCases: TimelineCases,
    private val eventHub: EventHub,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
    @Assisted val pachliAccountId: Long,
) : ViewModel() {
    val accountFlow = accountManager.getPachliAccountFlow(pachliAccountId)
        .filterNotNull()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    /** The account to display notifications for */
    val account: AccountEntity
        get() = accountFlow.replayCache.first().entity

    val uiState: StateFlow<UiState>

    /** Flow of changes to statusDisplayOptions, for use by the UI */
    val statusDisplayOptions = statusDisplayOptionsRepository.flow

    val pagingData: Flow<PagingData<NotificationViewData>>

    /** Flow of user actions received from the UI */
    private val uiAction = MutableSharedFlow<UiAction>()

    private val _uiResult = Channel<Result<UiSuccess, UiError>>()
    val uiResult = _uiResult.receiveAsFlow()

    /** Accept UI actions in to actionStateFlow */
    val accept: (UiAction) -> Unit = { action ->
        viewModelScope.launch { uiAction.emit(action) }
    }

    private var contentFilterModel: ContentFilterModel? = null

    init {
        // Handle changes to notification filters
        viewModelScope.launch {
            uiAction
                .filterIsInstance<InfallibleUiAction.ApplyFilter>()
                .distinctUntilChanged()
                .collectLatest(::onApplyFilter)
        }

        viewModelScope.launch {
            uiAction.filterIsInstance<InfallibleUiAction.LoadNewest>()
                .collectLatest { onLoadNewest() }
        }

        viewModelScope.launch {
            uiAction.filterIsInstance<InfallibleUiAction.SetContentCollapsed>()
                .collectLatest(::onContentCollapsed)
        }

        viewModelScope.launch {
            uiAction.filterIsInstance<InfallibleUiAction.SetShowingContent>()
                .collectLatest(::onShowingContent)
        }

        viewModelScope.launch {
            uiAction.filterIsInstance<InfallibleUiAction.SetExpanded>()
                .collectLatest(::onExpanded)
        }

        viewModelScope.launch {
            uiAction.filterIsInstance<InfallibleUiAction.ClearContentFilter>()
                .collectLatest(::onClearContentFilter)
        }

        viewModelScope.launch {
            uiAction.filterIsInstance<InfallibleUiAction.OverrideAccountFilter>()
                .collectLatest(::onOverrideAccountFilter)
        }

        // Save the visible notification ID
        viewModelScope.launch {
            uiAction
                .filterIsInstance<InfallibleUiAction.SaveVisibleId>()
                .distinctUntilChanged()
                .collectLatest { repository.saveRefreshKey(it.pachliAccountId, it.visibleId) }
        }

        // Handle UiAction.ClearNotifications
        viewModelScope.launch {
            uiAction.filterIsInstance<FallibleUiAction.ClearNotifications>()
                .collectLatest(::onClearNotifications)
        }

        // Handle NotificationAction.*
        viewModelScope.launch {
            uiAction.filterIsInstance<NotificationAction>()
                .throttleFirst()
                .collect { action ->
                    val result = try {
                        when (action) {
                            is NotificationAction.AcceptFollowRequest ->
                                timelineCases.acceptFollowRequest(action.accountId)
                            is NotificationAction.RejectFollowRequest ->
                                timelineCases.rejectFollowRequest(action.accountId)
                        }
                        Ok(NotificationActionSuccess.from(action))
                    } catch (e: Exception) {
                        Err(UiError.make(e, action))
                    }
                    _uiResult.send(result)
                }
        }

        // Handle StatusAction.*
        viewModelScope.launch {
            uiAction.filterIsInstance<StatusAction>()
                .throttleFirst() // avoid double-taps
                .collect { action ->
                    val result = when (action) {
                        is StatusAction.Bookmark -> repository.bookmark(
                            pachliAccountId,
                            action.statusViewData.actionableId,
                            action.state,
                        )

                        is StatusAction.Favourite -> repository.favourite(
                            pachliAccountId,
                            action.statusViewData.actionableId,
                            action.state,
                        )

                        is StatusAction.Reblog -> repository.reblog(
                            pachliAccountId,
                            action.statusViewData.actionableId,
                            action.state,
                        )

                        is StatusAction.VoteInPoll -> repository.voteInPoll(
                            pachliAccountId,
                            action.statusViewData.actionableId,
                            action.poll.id,
                            action.choices,
                        )
                    }.mapEither(
                        { StatusActionSuccess.from(action) },
                        { UiError.make(it.throwable, action) },
                    )
                    _uiResult.send(result)
                }
        }

        // Fetch the status filters
        viewModelScope.launch {
            accountFlow
                .distinctUntilChangedBy { it.contentFilters }
                .collect { account ->
                    contentFilterModel = when (account.contentFilters.version) {
                        ContentFilterVersion.V2 -> ContentFilterModel(FilterContext.NOTIFICATIONS)
                        ContentFilterVersion.V1 -> ContentFilterModel(
                            FilterContext.NOTIFICATIONS,
                            account.contentFilters.contentFilters,
                        )
                    }
                }
        }

        // Handle events that should refresh the list
        viewModelScope.launch {
            eventHub.events.collectLatest {
                when (it) {
                    is BlockEvent -> _uiResult.send(Ok(UiSuccess.Block))
                    is MuteEvent -> _uiResult.send(Ok(UiSuccess.Mute))
                    is MuteConversationEvent -> _uiResult.send(Ok(UiSuccess.MuteConversation))
                }
            }
        }

        pagingData = accountFlow
            .distinctUntilChanged { old, new ->
                (old.entity.notificationsFilter == new.entity.notificationsFilter) &&
                    (old.entity.notificationAccountFilterNotFollowed == new.entity.notificationAccountFilterNotFollowed) &&
                    (old.entity.notificationAccountFilterYounger30d == new.entity.notificationAccountFilterYounger30d) &&
                    (
                        old.entity.notificationAccountFilterLimitedByServer ==
                            new.entity.notificationAccountFilterLimitedByServer
                        )
            }
            .flatMapLatest { account ->
                getNotifications(
                    account,
                    filters = deserialize(account.entity.notificationsFilter),
                )
            }.cachedIn(viewModelScope)

        uiState =
            combine(accountFlow.distinctUntilChangedBy { it.entity.notificationsFilter }, getUiPrefs()) { account, _ ->
                UiState(
                    activeFilter = deserialize(account.entity.notificationsFilter),
                    showFabWhileScrolling = !sharedPreferencesRepository.getBoolean(PrefKeys.FAB_HIDE, false),
                    tabTapBehaviour = sharedPreferencesRepository.tabTapBehaviour,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
                initialValue = UiState(),
            )
    }

    private suspend fun onApplyFilter(action: InfallibleUiAction.ApplyFilter) {
        accountManager.setNotificationsFilter(action.pachliAccountId, serialize(action.filter))
    }

    /**
     * Resets the last notification ID to "0" so the next refresh will fetch the
     * newest notifications. The UI must still request the refresh, send
     * [UiSuccess.LoadNewest] so it knows to do that.
     */
    private suspend fun onLoadNewest() {
        repository.saveRefreshKey(account.id, null)
        _uiResult.send(Ok(UiSuccess.LoadNewest))
    }

    private suspend fun onClearNotifications(action: FallibleUiAction.ClearNotifications) {
        try {
            repository.clearNotifications().apply {
                if (this.isSuccessful) {
                    repository.invalidate()
                } else {
                    _uiResult.send(Err(UiError.make(HttpException(this), action)))
                }
            }
        } catch (e: Exception) {
            _uiResult.send(Err(UiError.make(e, action)))
        }
    }

    private suspend fun getNotifications(
        pachliAccount: PachliAccount,
        filters: Set<Notification.Type>,
    ): Flow<PagingData<NotificationViewData>> {
        val activeFilters = filters.map { NotificationEntity.Type.from(it) }
        return repository.notifications(pachliAccountId)
            .map { pagingData ->
                pagingData
                    .filter { !activeFilters.contains(it.notification.type) }
                    .map { notification ->
                        val contentFilterAction =
                            notification.viewData?.contentFilterAction
                                ?: notification.status?.status?.let { contentFilterModel?.filterActionFor(it) }
                                ?: FilterAction.NONE
                        val isAboutSelf = notification.account.serverId == pachliAccount.entity.accountId
                        val accountFilterDecision =
                            notification.viewData?.accountFilterDecision
                                ?: filterNotificationByAccount(pachliAccount, notification)

                        NotificationViewData.make(
                            pachliAccount.entity,
                            notification,
                            isShowingContent = statusDisplayOptions.value.showSensitiveMedia ||
                                !(notification.status?.status?.sensitive ?: false),
                            isExpanded = statusDisplayOptions.value.openSpoiler,
                            contentFilterAction = contentFilterAction,
                            accountFilterDecision = accountFilterDecision,
                            isAboutSelf = isAboutSelf,
                        )
                    }
                    .filter { it.statusViewData?.contentFilterAction != FilterAction.HIDE }
                    .filter { it.accountFilterDecision !is AccountFilterDecision.Hide }
            }
    }

    /** @return Flow of relevant preferences that change the UI. */
    private fun getUiPrefs() = sharedPreferencesRepository.changes
        .filter { UiPrefs.prefKeys.contains(it) }
        .onStart { emit(null) }

    private fun onContentCollapsed(action: InfallibleUiAction.SetContentCollapsed) =
        repository.setContentCollapsed(action.pachliAccountId, action.statusViewData, action.isCollapsed)

    private fun onShowingContent(action: InfallibleUiAction.SetShowingContent) =
        repository.setShowingContent(action.pachliAccountId, action.statusViewData, action.isShowingContent)

    private fun onExpanded(action: InfallibleUiAction.SetExpanded) =
        repository.setExpanded(action.pachliAccountId, action.statusViewData, action.isExpanded)

    private fun onClearContentFilter(action: InfallibleUiAction.ClearContentFilter) {
        repository.clearContentFilter(action.pachliAccountId, action.notificationId)
    }

    private fun onOverrideAccountFilter(action: InfallibleUiAction.OverrideAccountFilter) {
        repository.setAccountFilterDecision(
            action.pachliAccountId,
            action.notificationId,
            AccountFilterDecision.Override(action.accountFilterDecision),
        )
    }

    @AssistedFactory
    interface Factory {
        /** Creates [NotificationsViewModel] with [pachliAccountId] as the active account. */
        fun create(pachliAccountId: Long): NotificationsViewModel
    }
}
