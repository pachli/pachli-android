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

package app.pachli.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.accounts.AccountManager
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapEither
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
internal class LoginViewModel @Inject constructor(
    private val accountManager: AccountManager,
) : ViewModel() {
    private val uiAction = MutableSharedFlow<UiAction>()
    val accept: (UiAction) -> Unit = { action -> viewModelScope.launch { uiAction.emit(action) } }

    private val _uiResult = Channel<Result<UiSuccess, UiError>>()
    val uiResult = _uiResult.receiveAsFlow()

    init {
        viewModelScope.launch { uiAction.collect { launch { onUiAction(it) } } }
    }

    /** Processes actions received from the UI. */
    private suspend fun onUiAction(uiAction: UiAction) {
        val result = when (uiAction) {
            is FallibleUiAction.VerifyAndAddAccount -> verifyAndAddAccount(uiAction)
        }

        _uiResult.send(result)
    }

    private suspend fun verifyAndAddAccount(uiAction: FallibleUiAction.VerifyAndAddAccount): Result<UiSuccess.VerifyAndAddAccount, UiError.VerifyAndAddAccount> {
        return accountManager.verifyAndAddAccount(
            uiAction.accessToken.accessToken,
            uiAction.domain,
            uiAction.clientId,
            uiAction.clientSecret,
            uiAction.oAuthScopes,
        ).mapEither(
            { UiSuccess.VerifyAndAddAccount(uiAction, it) },
            { UiError.VerifyAndAddAccount(uiAction, it) },
        )
    }
}
