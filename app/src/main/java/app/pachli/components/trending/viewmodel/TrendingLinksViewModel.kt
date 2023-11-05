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

package app.pachli.components.trending.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.components.trending.TrendingLinksRepository
import app.pachli.db.AccountManager
import app.pachli.entity.TrendsLink
import app.pachli.util.StatusDisplayOptionsRepository
import app.pachli.util.throttleFirst
import at.connyduck.calladapter.networkresult.fold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

sealed interface UiAction

sealed interface InfallibleUiAction : UiAction {
    data object Reload : InfallibleUiAction
}

sealed interface LoadState {
    data object Initial : LoadState
    data object Loading : LoadState
    data class Success(val data: List<TrendsLink>) : LoadState
    data class Error(val throwable: Throwable) : LoadState
}

@HiltViewModel
class TrendingLinksViewModel @Inject constructor(
    private val repository: TrendingLinksRepository,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
    accountManager: AccountManager,
) : ViewModel() {
    val activeAccount = accountManager.activeAccount!!

    private val _loadState = MutableStateFlow<LoadState>(LoadState.Initial)
    val loadState = _loadState.asStateFlow()

    val statusDisplayOptions = statusDisplayOptionsRepository.flow

    private val uiAction = MutableSharedFlow<UiAction>()

    val accept: (UiAction) -> Unit = { viewModelScope.launch { uiAction.emit(it) } }

    init {
        viewModelScope.launch {
            uiAction
                .throttleFirst(THROTTLE_TIMEOUT)
                .filterIsInstance<InfallibleUiAction.Reload>()
                .onEach { invalidate() }
                .collect()
        }
    }

    private fun invalidate() = viewModelScope.launch {
        _loadState.update { LoadState.Loading }
        val response = repository.getTrendingLinks()
        response.fold(
            { list -> _loadState.update { LoadState.Success(list) } },
            { throwable -> _loadState.update { LoadState.Error(throwable) } },
        )
    }

    companion object {
        private val THROTTLE_TIMEOUT = 500.milliseconds
    }
}
