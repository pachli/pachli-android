/*
 * Copyright 2025 Pachli Association
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

package app.pachli.feature.manageaccounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.common.extensions.stateFlow
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.PachliAccount
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map

/**
 * High-level UI state, derived from [StatusDisplayOptions].
 *
 * @property animateEmojis True if emojis should be animated
 * @property animateAvatars True if avatars should be animated
 * @property showBotOverlay True if avatars for bot accounts should show an overlay
 */
internal data class UiState(
    val animateEmojis: Boolean = false,
    val animateAvatars: Boolean = false,
    val showBotOverlay: Boolean = false,
) {
    companion object {
        fun from(statusDisplayOptions: StatusDisplayOptions) = UiState(
            animateEmojis = statusDisplayOptions.animateEmojis,
            animateAvatars = statusDisplayOptions.animateAvatars,
            showBotOverlay = statusDisplayOptions.showBotOverlay,
        )
    }
}

/** Actions that can be taken from the UI. */
sealed interface UiAction {
    /** Add a new account. */
    data object AddAccount : UiAction

    /** Logout [pachliAccount]. */
    data class Logout(val pachliAccount: PachliAccount) : UiAction

    /** Switch to [pachliAccount]. */
    data class Switch(val pachliAccount: PachliAccount) : UiAction
}

@HiltViewModel
class ManageAccountsViewModel @Inject constructor(
    accountManager: AccountManager,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
) : ViewModel() {
    internal val pachliAccounts = stateFlow(viewModelScope, emptyList()) {
        accountManager.pachliAccountsFlow
            .flowWhileShared(SharingStarted.WhileSubscribed(5000))
    }

    internal val uiState = stateFlow(viewModelScope, UiState.from(statusDisplayOptionsRepository.flow.value)) {
        statusDisplayOptionsRepository.flow.map { UiState.from(it) }
            .flowWhileShared(SharingStarted.WhileSubscribed(5000))
    }
}
