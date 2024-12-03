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
import app.pachli.components.account.AccountViewModel
import app.pachli.components.trending.TrendingLinksRepository
import app.pachli.core.common.extensions.stateFlow
import app.pachli.core.common.extensions.throttleFirst
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.network.model.TrendsLink
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import at.connyduck.calladapter.networkresult.fold
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

sealed interface UiAction

sealed interface InfallibleUiAction : UiAction {
    data object Reload : InfallibleUiAction
}

sealed interface LoadState {
    data object Loading : LoadState
    data class Success(val data: List<TrendsLink>) : LoadState
    data class Error(val throwable: Throwable) : LoadState
}

@HiltViewModel(assistedFactory = TrendingLinksViewModel.Factory::class)
class TrendingLinksViewModel @AssistedInject constructor(
    @Assisted private val pachliAccountId: Long,
    private val repository: TrendingLinksRepository,
    sharedPreferencesRepository: SharedPreferencesRepository,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
    accountManager: AccountManager,
) : ViewModel() {
    val pachliAccountFlow = accountManager.getPachliAccountFlow(pachliAccountId)
        .filterNotNull()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    private val reload = MutableSharedFlow<Unit>(replay = 1)

    val loadState = stateFlow(viewModelScope, LoadState.Loading) {
        reload.flatMapLatest {
            flow {
                emit(LoadState.Loading)
                emit(
                    repository.getTrendingLinks().fold(
                        { list -> LoadState.Success(list) },
                        { throwable -> LoadState.Error(throwable) },
                    ),
                )
            }
        }.flowWhileShared(SharingStarted.WhileSubscribed(5000))
    }

    val showFabWhileScrolling = sharedPreferencesRepository.changes
        .filter { it == null || it == PrefKeys.FAB_HIDE }
        .map { !sharedPreferencesRepository.getBoolean(PrefKeys.FAB_HIDE, false) }
        .onStart { emit(!sharedPreferencesRepository.getBoolean(PrefKeys.FAB_HIDE, false)) }
        .shareIn(viewModelScope, replay = 1, started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000))

    val statusDisplayOptions = statusDisplayOptionsRepository.flow

    private val uiAction = MutableSharedFlow<UiAction>()

    val accept: (UiAction) -> Unit = { viewModelScope.launch { uiAction.emit(it) } }

    init {
        viewModelScope.launch {
            uiAction
                .throttleFirst()
                .filterIsInstance<InfallibleUiAction.Reload>()
                .onEach { reload.emit(Unit) }
                .collect()
        }
    }

    @AssistedFactory
    interface Factory {
        /** Creates [AccountViewModel] with [pachliAccountId] as the active account. */
        fun create(pachliAccountId: Long): TrendingLinksViewModel
    }
}
