/*
 * Copyright (c) 2025 Pachli Association
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

package app.pachli.components.preference.accountfilters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.components.preference.accountfilters.AccountFilterTimeline.CONVERSATIONS
import app.pachli.components.preference.accountfilters.AccountFilterTimeline.NOTIFICATIONS
import app.pachli.components.preference.accountfilters.InfallibleUiAction.SetAccountFilter
import app.pachli.core.common.extensions.throttleFirst
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.model.AccountFilterReason
import app.pachli.core.model.FilterAction
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/** The timeline these account filters will be applied to. */
enum class AccountFilterTimeline {
    CONVERSATIONS,
    NOTIFICATIONS,
}

/**
 * Describe's the current account filter settings for the
 * Pachli account ID passed to
 * [create][AccountFiltersPreferenceViewModel.Factory.create].
 */
data class UiState(
    val filterNotFollowing: FilterAction,
    val filterYounger30d: FilterAction,
    val filterLimitedByServer: FilterAction,
)

sealed interface UiAction

sealed interface InfallibleUiAction : UiAction {
    data class SetAccountFilter(
        val pachliAccountId: Long,
        val reason: AccountFilterReason,
        val action: FilterAction,
    ) : InfallibleUiAction
}

/**
 * Represents account filter preferences for [pachliAccountId] for the given
 * [accountFilterTimeline].
 */
@HiltViewModel(assistedFactory = AccountFiltersPreferenceViewModel.Factory::class)
class AccountFiltersPreferenceViewModel @AssistedInject constructor(
    @Assisted private val pachliAccountId: Long,
    @Assisted private val accountFilterTimeline: AccountFilterTimeline,
    private val accountManager: AccountManager,
) : ViewModel() {
    val uiState = accountManager.getPachliAccountFlow(pachliAccountId)
        .filterNotNull()
        .map {
            when (accountFilterTimeline) {
                CONVERSATIONS -> UiState(
                    filterNotFollowing = it.entity.conversationAccountFilterNotFollowed,
                    filterYounger30d = it.entity.conversationAccountFilterYounger30d,
                    filterLimitedByServer = it.entity.conversationAccountFilterLimitedByServer,
                )
                NOTIFICATIONS -> UiState(
                    filterNotFollowing = it.entity.notificationAccountFilterNotFollowed,
                    filterYounger30d = it.entity.notificationAccountFilterYounger30d,
                    filterLimitedByServer = it.entity.notificationAccountFilterLimitedByServer,
                )
            }
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    /** Flow of user actions received from the UI */
    private val uiAction = MutableSharedFlow<UiAction>()

    /** Accept UI actions in to uiAction */
    val accept: (UiAction) -> Unit = { viewModelScope.launch { uiAction.emit(it) } }

    init {
        viewModelScope.launch {
            uiAction
                .filterIsInstance<SetAccountFilter>()
                .throttleFirst()
                .distinctUntilChanged().collectLatest(::onSetAccountFilter)
        }
    }

    private suspend fun onSetAccountFilter(action: SetAccountFilter) {
        when (accountFilterTimeline) {
            CONVERSATIONS -> onSetConversationAccountFilter(action)
            NOTIFICATIONS -> onSetNotificationAccountFilter(action)
        }
    }

    /**
     * Sets the account's conversation account filters from [action].
     */
    private suspend fun onSetConversationAccountFilter(action: SetAccountFilter) {
        when (action.reason) {
            AccountFilterReason.NOT_FOLLOWING ->
                accountManager.setConversationAccountFilterNotFollowed(
                    action.pachliAccountId,
                    action.action,
                )
            AccountFilterReason.YOUNGER_30D ->
                accountManager.setConversationAccountFilterYounger30d(
                    action.pachliAccountId,
                    action.action,
                )
            AccountFilterReason.LIMITED_BY_SERVER ->
                accountManager.setConversationAccountFilterLimitedByServer(
                    action.pachliAccountId,
                    action.action,
                )
        }
    }

    /**
     * Sets the account's notification account filters from [action].
     */
    private suspend fun onSetNotificationAccountFilter(action: SetAccountFilter) {
        when (action.reason) {
            AccountFilterReason.NOT_FOLLOWING ->
                accountManager.setNotificationAccountFilterNotFollowed(
                    action.pachliAccountId,
                    action.action,
                )
            AccountFilterReason.YOUNGER_30D ->
                accountManager.setNotificationAccountFilterYounger30d(
                    action.pachliAccountId,
                    action.action,
                )
            AccountFilterReason.LIMITED_BY_SERVER ->
                accountManager.setNotificationAccountFilterLimitedByServer(
                    action.pachliAccountId,
                    action.action,
                )
        }
    }

    @AssistedFactory
    interface Factory {
        /**
         * Creates [AccountFiltersPreferenceViewModel] with [pachliAccountId] as
         * the active account.
         */
        fun create(
            pachliAccountId: Long,
            accountFilterTimeline: AccountFilterTimeline,
        ): AccountFiltersPreferenceViewModel
    }
}
