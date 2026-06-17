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

package app.pachli.feature.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.preferences.LinksToUnderline
import app.pachli.core.preferences.SharedPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AboutFragmentViewModel @Inject constructor(
    private val accountManager: AccountManager,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
) : ViewModel() {
    /**
     * @property username Active account's username.
     * @property domain Active account's domain.
     * @property serverVersion Server's version string, as reported by the server.
     */
    data class UiState(
        val username: String,
        val domain: String,
        val serverVersion: String,
    )

    val linksToUnderline: Set<LinksToUnderline>
        get() = sharedPreferencesRepository.linksToUnderline

    private val pachliAccountId = MutableSharedFlow<Long>(replay = 1)

    val uiState = pachliAccountId.flatMapLatest {
        accountManager.getPachliAccountFlow(it).filterNotNull().map { pachliAccount ->
            UiState(
                username = pachliAccount.username,
                domain = pachliAccount.domain,
                serverVersion = pachliAccount.server.rawVersion,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null,
    )

    internal fun setPachliAccountId(pachliAccountId: Long) {
        viewModelScope.launch { this@AboutFragmentViewModel.pachliAccountId.emit(pachliAccountId) }
    }
}
