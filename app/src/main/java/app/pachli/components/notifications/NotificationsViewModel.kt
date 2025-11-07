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
import app.pachli.core.common.PachliError
import app.pachli.core.common.extensions.throttleFirst
import app.pachli.core.data.model.ContentFilterModel
import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.data.model.NotificationViewData
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.OfflineFirstStatusRepository
import app.pachli.core.data.repository.PachliAccount
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.data.repository.notifications.NotificationsRepository
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.eventhub.BlockEvent
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.MuteConversationEvent
import app.pachli.core.eventhub.MuteEvent
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.ContentFilterVersion
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterContext
import app.pachli.core.model.Notification
import app.pachli.core.model.Poll
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.preferences.TabTapBehaviour
import app.pachli.core.ui.extensions.make
import app.pachli.usecase.TimelineCases
import app.pachli.util.deserialize
import app.pachli.util.serialize
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    data class ClearNotifications(val pachliAccountId: Long) : FallibleUiAction
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
        val statusViewData: IStatusViewData,
        val isCollapsed: Boolean,
    ) : InfallibleUiAction

    /** Set how to show attached media. */
    data class SetAttachmentDisplayAction(
        val pachliAccountId: Long,
        val statusViewData: IStatusViewData,
        val attachmentDisplayAction: AttachmentDisplayAction,
    ) : InfallibleUiAction

    /** Set whether to show just the content warning, or the full content. */
    data class SetExpanded(
        val pachliAccountId: Long,
        val statusViewData: IStatusViewData,
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

sealed interface UiActionSuccess : UiSuccess {
    /** Clearing remote notifications (and the local cache) succeeded. */
    data object ClearNotifications : UiActionSuccess
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

sealed interface StatusAction

sealed interface InfallibleStatusAction : InfallibleUiAction, StatusAction {
    val statusViewData: IStatusViewData

    data class TranslateUndo(override val statusViewData: IStatusViewData) : InfallibleStatusAction
}

/** Actions the user can trigger on an individual status */
sealed interface FallibleStatusAction : FallibleUiAction, StatusAction {
    val statusViewData: IStatusViewData

    /** Set the bookmark state for a status */
    data class Bookmark(val state: Boolean, override val statusViewData: IStatusViewData) : FallibleStatusAction

    /** Set the favourite state for a status */
    data class Favourite(val state: Boolean, override val statusViewData: IStatusViewData) : FallibleStatusAction

    /** Set the reblog state for a status */
    data class Reblog(val state: Boolean, override val statusViewData: IStatusViewData) : FallibleStatusAction

    /** Vote in a poll */
    data class VoteInPoll(
        val poll: Poll,
        val choices: List<Int>,
        override val statusViewData: IStatusViewData,
    ) : FallibleStatusAction

    /** Translate a status */
    data class Translate(override val statusViewData: IStatusViewData) : FallibleStatusAction
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

/** Errors from fallible view model actions that the UI will need to show */
sealed interface UiError {
    /** The error associated with the error */
    val error: PachliError

    /** The action that failed. Can be resent to retry the action */
    val action: UiAction?

    /** String resource with an error message to show the user */
    @get:StringRes
    val message: Int

    data class ClearNotifications(
        override val error: PachliError,
        override val action: FallibleUiAction.ClearNotifications,
        override val message: Int = R.string.ui_error_clear_notifications,
    ) : UiError

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

    data class AcceptFollowRequest(
        override val error: PachliError,
        override val action: NotificationAction.AcceptFollowRequest,
        override val message: Int = R.string.ui_error_accept_follow_request,
    ) : UiError

    data class RejectFollowRequest(
        override val error: PachliError,
        override val action: NotificationAction.RejectFollowRequest,
        override val message: Int = R.string.ui_error_reject_follow_request,
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
            is NotificationAction.AcceptFollowRequest -> AcceptFollowRequest(error, action)
            is NotificationAction.RejectFollowRequest -> RejectFollowRequest(error, action)
            is FallibleUiAction.ClearNotifications -> ClearNotifications(error, action)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = NotificationsViewModel.Factory::class)
class NotificationsViewModel @AssistedInject constructor(
    private val repository: NotificationsRepository,
    private val accountManager: AccountManager,
    private val timelineCases: TimelineCases,
    private val eventHub: EventHub,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
    private val statusRepository: OfflineFirstStatusRepository,
    @Assisted val pachliAccountId: Long,
) : ViewModel() {
    private val accountFlow = accountManager.getPachliAccountFlow(pachliAccountId)
        .filterNotNull()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val initialRefreshKey = accountFlow.flatMapLatest {
        flow { emit(repository.getRefreshKey(it.id)) }
    }

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
                .collectLatest { ::onLoadNewest }
        }

        viewModelScope.launch {
            uiAction.filterIsInstance<InfallibleUiAction.SetContentCollapsed>()
                .collectLatest(::onContentCollapsed)
        }

        viewModelScope.launch {
            uiAction.filterIsInstance<InfallibleUiAction.SetAttachmentDisplayAction>()
                .collectLatest(::onAttachmentDisplayAction)
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
                    val result = when (action) {
                        is NotificationAction.AcceptFollowRequest ->
                            timelineCases.acceptFollowRequest(action.accountId)
                        is NotificationAction.RejectFollowRequest ->
                            timelineCases.rejectFollowRequest(action.accountId)
                    }.mapEither(
                        { NotificationActionSuccess.from(action) },
                        { UiError.make(it, action) },
                    )
                    _uiResult.send(result)
                }
        }

        // Handle StatusAction.*
        viewModelScope.launch {
            uiAction.filterIsInstance<FallibleStatusAction>()
                .throttleFirst() // avoid double-taps
                .collect { action ->
                    val result = when (action) {
                        is FallibleStatusAction.Bookmark -> statusRepository.bookmark(
                            pachliAccountId,
                            action.statusViewData.actionableId,
                            action.state,
                        )

                        is FallibleStatusAction.Favourite -> statusRepository.favourite(
                            pachliAccountId,
                            action.statusViewData.actionableId,
                            action.state,
                        )

                        is FallibleStatusAction.Reblog -> statusRepository.reblog(
                            pachliAccountId,
                            action.statusViewData.actionableId,
                            action.state,
                        )

                        is FallibleStatusAction.VoteInPoll -> statusRepository.voteInPoll(
                            pachliAccountId,
                            action.statusViewData.actionableId,
                            action.poll.id,
                            action.choices,
                        )

                        is FallibleStatusAction.Translate -> timelineCases.translate(action.statusViewData)
                    }.mapEither(
                        { StatusActionSuccess.from(action) },
                        { UiError.make(it, action) },
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

        // Undo status translations
        viewModelScope.launch {
            uiAction.filterIsInstance<InfallibleStatusAction.TranslateUndo>().collectLatest {
                timelineCases.translateUndo(it.statusViewData)
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
                    excludeTypes = deserialize(account.entity.notificationsFilter),
                )
            }.cachedIn(viewModelScope)

        uiState =
            combine(accountFlow.distinctUntilChangedBy { it.entity.notificationsFilter }, getUiPrefs()) { account, _ ->
                UiState(
                    activeFilter = deserialize(account.entity.notificationsFilter),
                    showFabWhileScrolling = !sharedPreferencesRepository.hideFabWhenScrolling,
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
        repository.clearNotifications(action.pachliAccountId)
            .onSuccess { _uiResult.send(Ok(UiActionSuccess.ClearNotifications)) }
            .onFailure { _uiResult.send(Err(UiError.make(it, action))) }
    }

    /**
     * Gets notifications for [pachliAccount], excluding types of notifications in
     * [excludeTypes], and applies content and account filters.
     *
     * @param pachliAccount
     * @param excludeTypes 0 or more [Notification.Type] to exclude from the results.
     */
    private suspend fun getNotifications(
        pachliAccount: PachliAccount,
        excludeTypes: Set<Notification.Type>,
    ): Flow<PagingData<NotificationViewData>> {
        return repository.notifications(pachliAccountId, excludeTypes)
            .map { pagingData ->
                pagingData
                    .map { notification ->
                        val contentFilterAction =
                            notification.viewData?.contentFilterAction
                                ?: notification.status?.timelineStatus?.let { contentFilterModel?.filterActionFor(it.status) }
                                ?: FilterAction.NONE
                        val quoteContentFilterAction =
                            notification.status?.quotedStatus?.let { contentFilterModel?.filterActionFor(it.status) }
                        val isAboutSelf = notification.account.serverId == pachliAccount.entity.accountId
                        val accountFilterDecision =
                            notification.viewData?.accountFilterDecision
                                ?: filterNotificationByAccount(pachliAccount, notification)

                        NotificationViewData.make(
                            pachliAccount.entity,
                            notification,
                            showSensitiveMedia = statusDisplayOptions.value.showSensitiveMedia,
                            isExpanded = statusDisplayOptions.value.openSpoiler,
                            contentFilterAction = contentFilterAction,
                            quoteContentFilterAction = quoteContentFilterAction,
                            accountFilterDecision = accountFilterDecision,
                            isAboutSelf = isAboutSelf,
                        )
                    }
                    .filter { it !is NotificationViewData.WithStatus || it.statusViewDataQ.contentFilterAction != FilterAction.HIDE }
                    .filter { it.accountFilterDecision !is AccountFilterDecision.Hide }
            }
    }

    /** @return Flow of relevant preferences that change the UI. */
    private fun getUiPrefs() = sharedPreferencesRepository.changes
        .filter { UiPrefs.prefKeys.contains(it) }
        .onStart { emit(null) }

    private fun onContentCollapsed(action: InfallibleUiAction.SetContentCollapsed) {
        viewModelScope.launch {
            repository.setContentCollapsed(action.pachliAccountId, action.statusViewData.actionableId, action.isCollapsed)
        }
    }

    private fun onAttachmentDisplayAction(action: InfallibleUiAction.SetAttachmentDisplayAction) {
        viewModelScope.launch {
            repository.setAttachmentDisplayAction(action.pachliAccountId, action.statusViewData.actionableId, action.attachmentDisplayAction)
        }
    }

    private fun onExpanded(action: InfallibleUiAction.SetExpanded) {
        viewModelScope.launch {
            repository.setExpanded(action.pachliAccountId, action.statusViewData.actionableId, action.isExpanded)
        }
    }

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
