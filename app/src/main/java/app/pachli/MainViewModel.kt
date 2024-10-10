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
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Probably stuff to include in UiState
//
// - Details for all accounts

/** Actions the user can take from the UI. */
internal sealed interface UiAction

internal sealed interface InfallibleUiAction : UiAction {
    data class TabRemoveTimeline(val timeline: Timeline) : InfallibleUiAction
}

// (r) -> changes to this pref restart activities when leaving PreferencesActivity
data class UiState(
    val animateAvatars: Boolean, // (r)
    val animateEmojis: Boolean,
    val enableTabSwipe: Boolean, // (r)
    val hideTopToolbar: Boolean, // onCreate, bindDrawerAvatar (r)
    val mainNavigationPosition: MainNavigationPosition, // (r)
    // mainNavPosition (top, bottom), onCreate, bindTabs, bindDrawerAvatar
    // emoji preference, onCreate, onResume, updateDrawerProfileHeader
    // animate gif avatars (animateAvatars), bindMainDrawer, bindDrawerAvatar (r)
    // fontFamily, bindMainDrawerItems (r)
    // enableSwipeForTabs, bindTabs (r) (probably doesn't need to restart)
) {
    companion object {
        fun make(prefs: SharedPreferencesRepository) = UiState(
            animateAvatars = prefs.animateAvatars,
            animateEmojis = prefs.animateEmojis,
            enableTabSwipe = prefs.enableTabSwipe,
            hideTopToolbar = prefs.hideTopToolbar,
            mainNavigationPosition = prefs.mainNavigationPosition,
        )
    }
}

@HiltViewModel(assistedFactory = MainViewModel.Factory::class)
internal class MainViewModel @AssistedInject constructor(
    private val accountManager: AccountManager,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
    @Assisted val activeAccountId: Long,
) : ViewModel() {
    private val activeAccount: AccountEntity?
        get() {
            return accountManager.activeAccount
        }
    private val accountsFlow = accountManager.accountsFlow
    val accountsOrderedByActiveFlow = accountManager.accountsOrderedByActiveFlow

    val pachliAccountFlow = flow {
        accountManager.getPachliAccountFlow(activeAccountId).collect { emit(it) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val displaySelfUsername: Boolean
        get() {
            val showUsernamePreference = sharedPreferencesRepository.getString(PrefKeys.SHOW_SELF_USERNAME, "disambiguate")
            if (showUsernamePreference == "always") {
                return true
            }
            if (showUsernamePreference == "never") {
                return false
            }

            return accountsFlow.value.size > 1
        }

    private val uiAction = MutableSharedFlow<UiAction>()

    val accept: (UiAction) -> Unit = { action -> viewModelScope.launch { uiAction.emit(action) } }

    private val watchedPrefs = setOf(
        PrefKeys.ANIMATE_GIF_AVATARS,
        PrefKeys.ANIMATE_CUSTOM_EMOJIS,
        PrefKeys.ENABLE_SWIPE_FOR_TABS,
        PrefKeys.HIDE_TOP_TOOLBAR,
        PrefKeys.MAIN_NAV_POSITION,
    )

    val uiState = sharedPreferencesRepository.changes
        .filter { watchedPrefs.contains(it) }
        .map {
            UiState.make(sharedPreferencesRepository)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState.make(sharedPreferencesRepository),
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
            accountManager.setActiveAccount(activeAccountId)
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
        val active = activeAccount ?: return
        val tabPreferences = active.tabPreferences.filterNot { it == timeline }
        accountManager.setTabPreferences(active.id, tabPreferences)
    }

    @AssistedFactory
    interface Factory {
        /**
         * Creates [MainViewModel] with [accountId] as the active account.
         */
        fun create(accountId: Long): MainViewModel
    }
}
