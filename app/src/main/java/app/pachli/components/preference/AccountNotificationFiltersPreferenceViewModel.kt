/*
 * Copyright 2024 Pachli Association
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

package app.pachli.components.preference

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.model.FilterAction
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * Describe's the current account notification filter settings for the
 * Pachli account ID passed to
 * [create][AccountNotificationFiltersPreferenceViewModel.Factory.create].
 */
data class UiState(
    val filterNotFollowing: FilterAction,
    val filterYounger30d: FilterAction,
    val filterLimitedByServer: FilterAction,
)

@HiltViewModel(assistedFactory = AccountNotificationFiltersPreferenceViewModel.Factory::class)
class AccountNotificationFiltersPreferenceViewModel @AssistedInject constructor(
    @Assisted private val pachliAccountId: Long,
    private val accountManager: AccountManager,
) : ViewModel() {
    val uiState = accountManager.getPachliAccountFlow(pachliAccountId)
        .filterNotNull()
        .map {
            UiState(
                filterNotFollowing = it.entity.notificationAccountFilterNotFollowed,
                filterYounger30d = it.entity.notificationAccountFilterYounger30d,
                filterLimitedByServer = it.entity.notificationAccountFilterLimitedByServer,
            )
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    fun setNotificationAccountFilterNotFollowing(action: FilterAction) = viewModelScope.launch {
        accountManager.setNotificationAccountFilterNotFollowed(pachliAccountId, action)
    }

    fun setNotificationAccountFilterYounger30d(action: FilterAction) = viewModelScope.launch {
        accountManager.setNotificationAccountFilterYounger30d(pachliAccountId, action)
    }

    fun setNotificationAccountFilterLimitedByServer(action: FilterAction) = viewModelScope.launch {
        accountManager.setNotificationAccountFilterLimitedByServer(pachliAccountId, action)
    }

    @AssistedFactory
    interface Factory {
        /**
         * Creates [AccountNotificationFiltersPreferenceViewModel] with [pachliAccountId] as
         * the active account.
         */
        fun create(
            pachliAccountId: Long,
        ): AccountNotificationFiltersPreferenceViewModel
    }
}
