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

package app.pachli

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.model.Timeline
import app.pachli.core.preferences.MainNavigationPosition
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.preferences.ShowSelfUsername
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Actions the user can take from the UI. */
internal sealed interface UiAction

internal sealed interface InfallibleUiAction : UiAction {
    /** Remove [timeline] from the active account's tabs. */
    data class TabRemoveTimeline(val timeline: Timeline) : InfallibleUiAction
}

/**
 * @param animateAvatars See [SharedPreferencesRepository.animateAvatars].
 * @param animateEmojis See [SharedPreferencesRepository.animateEmojis].
 * @param enableTabSwipe See [SharedPreferencesRepository.enableTabSwipe].
 * @param hideTopToolbar See [SharedPreferencesRepository.hideTopToolbar].
 * @param mainNavigationPosition See [SharedPreferencesRepository.mainNavigationPosition].
 * @param displaySelfUsername See [ShowSelfUsername].
 * @param accounts Unordered list of available accounts.
 */
data class UiState(
    val animateAvatars: Boolean,
    val animateEmojis: Boolean,
    val enableTabSwipe: Boolean,
    val hideTopToolbar: Boolean,
    val mainNavigationPosition: MainNavigationPosition,
    val displaySelfUsername: Boolean,
    val accounts: List<AccountEntity>,
) {
    companion object {
        fun make(prefs: SharedPreferencesRepository, accounts: List<AccountEntity>) = UiState(
            animateAvatars = prefs.animateAvatars,
            animateEmojis = prefs.animateEmojis,
            enableTabSwipe = prefs.enableTabSwipe,
            hideTopToolbar = prefs.hideTopToolbar,
            mainNavigationPosition = prefs.mainNavigationPosition,
            displaySelfUsername = when (prefs.showSelfUsername) {
                ShowSelfUsername.ALWAYS -> true
                ShowSelfUsername.DISAMBIGUATE -> accounts.size > 1
                ShowSelfUsername.NEVER -> false
            },
            accounts = accounts,
        )
    }
}

@HiltViewModel(assistedFactory = MainViewModel.Factory::class)
internal class MainViewModel @AssistedInject constructor(
    private val accountManager: AccountManager,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
    @Assisted val pachliAccountId: Long,
) : ViewModel() {
    val pachliAccountFlow = flow {
        accountManager.getPachliAccountFlow(pachliAccountId)
            .filterNotNull()
            .collect { emit(it) }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    private val uiAction = MutableSharedFlow<UiAction>()

    val accept: (UiAction) -> Unit = { action -> viewModelScope.launch { uiAction.emit(action) } }

    private val watchedPrefs = setOf(
        PrefKeys.ANIMATE_GIF_AVATARS,
        PrefKeys.ANIMATE_CUSTOM_EMOJIS,
        PrefKeys.ENABLE_SWIPE_FOR_TABS,
        PrefKeys.HIDE_TOP_TOOLBAR,
        PrefKeys.MAIN_NAV_POSITION,
        PrefKeys.SHOW_SELF_USERNAME,
    )

    val uiState = sharedPreferencesRepository.changes
        .filter { watchedPrefs.contains(it) }
        .combine(accountManager.accountsFlow) { _, accounts ->
            UiState.make(sharedPreferencesRepository, accounts)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState.make(sharedPreferencesRepository, emptyList()),
        )

    init {
        viewModelScope.launch { uiAction.collect { launch { onUiAction(it) } } }

        viewModelScope.launch {
            // TODO: Emit the error(s), possibly wrapped. Need to explain to the user
            // what happened.
            //
            // Possibly allow the switch if some info is missing (filters, lists), but
            // explain to the user what that will mean. Need's a "refreshAccountInfo"
            // operation.
            accountManager.setActiveAccount(pachliAccountId)
        }
    }

    private suspend fun onUiAction(uiAction: UiAction) {
        if (uiAction is InfallibleUiAction) {
            when (uiAction) {
                is InfallibleUiAction.TabRemoveTimeline -> onTabRemoveTimeline(uiAction.timeline)
            }
        }
    }

    private suspend fun onTabRemoveTimeline(timeline: Timeline) {
        val active = pachliAccountFlow.replayCache.last().entity
        val tabPreferences = active.tabPreferences.filterNot { it == timeline }
        accountManager.setTabPreferences(active.id, tabPreferences)
    }

    @AssistedFactory
    interface Factory {
        /**
         * Creates [MainViewModel] with [pachliAccountId] as the active account.
         */
        fun create(pachliAccountId: Long): MainViewModel
    }
}
