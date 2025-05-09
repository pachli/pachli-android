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
import app.pachli.components.timeline.viewmodel.TimelineViewModel
import app.pachli.core.data.model.Server
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.model.ServerOperation
import app.pachli.core.model.Timeline
import app.pachli.core.preferences.MainNavigationPosition
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.preferences.ShowSelfUsername
import app.pachli.core.preferences.TabAlignment
import app.pachli.core.preferences.TabContents
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.z4kn4fein.semver.constraints.toConstraint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Actions the user can take from the UI. */
internal sealed interface UiAction

internal sealed interface InfallibleUiAction : UiAction {
    /**
     * Directs the ViewModel to load the account identified by [pachliAccountId].
     * This will trigger fetching statuses for that account, and emit into the
     * [TimelineViewModel.statuses] flow.
     */
    // Do not replace this with a function that returns the flow. The UI would call it
    // on every restart (e.g., configuration change) causing the flow to be recreated.
    // This way the flow is cached in the viewmodel, and the UI can recollect it on
    // restart with very little delay.
    data class LoadPachliAccount(val pachliAccountId: Long) : InfallibleUiAction

    /** Remove [timeline] from the [pachliAccountId]'s tabs tabs. */
    data class TabRemoveTimeline(val pachliAccountId: Long, val timeline: Timeline) : InfallibleUiAction
}

/**
 * @param animateAvatars See [SharedPreferencesRepository.animateAvatars].
 * @param animateEmojis See [SharedPreferencesRepository.animateEmojis].
 * @param enableTabSwipe See [SharedPreferencesRepository.enableTabSwipe].
 * @param hideTopToolbar See [SharedPreferencesRepository.hideTopToolbar].
 * @param mainNavigationPosition See [SharedPreferencesRepository.mainNavigationPosition].
 * @param displaySelfUsername See [ShowSelfUsername].
 * @param accounts Unordered list of available accounts.
 * @param canSchedulePost True if the account can schedule posts
 */
data class UiState(
    val animateAvatars: Boolean,
    val animateEmojis: Boolean,
    val enableTabSwipe: Boolean,
    val hideTopToolbar: Boolean,
    val mainNavigationPosition: MainNavigationPosition,
    val displaySelfUsername: Boolean,
    val accounts: List<AccountEntity>,
    val canSchedulePost: Boolean,
    val tabAlignment: TabAlignment,
    val tabContents: TabContents,
) {
    companion object {
        fun make(prefs: SharedPreferencesRepository, accounts: List<AccountEntity>, server: Server?) = UiState(
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
            canSchedulePost = server?.can(ServerOperation.ORG_JOINMASTODON_STATUSES_SCHEDULED, ">= 1.0.0".toConstraint()) == true,
            tabAlignment = prefs.tabAlignment,
            tabContents = prefs.tabContents,
        )
    }
}

@HiltViewModel
internal class MainViewModel @Inject constructor(
    private val accountManager: AccountManager,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
) : ViewModel() {
    val pachliAccountId = MutableSharedFlow<Long>(replay = 1)

    val pachliAccountFlow = pachliAccountId.distinctUntilChanged().flatMapLatest {
        accountManager.getPachliAccountFlow(it).filterNotNull()
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
        PrefKeys.TAB_ALIGNMENT,
        PrefKeys.TAB_CONTENTS,
    )

    val uiState =
        combine(
            sharedPreferencesRepository.changes.filter { watchedPrefs.contains(it) }.onStart { emit(null) },
            accountManager.accountsFlow,
            pachliAccountFlow,
        ) { _, accounts, pachliAccount ->
            UiState.make(
                sharedPreferencesRepository,
                accounts,
                pachliAccount.server,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = UiState.make(sharedPreferencesRepository, accountManager.accounts, null),
            )

    init {
        viewModelScope.launch { uiAction.collect { launch { onUiAction(it) } } }
    }

    private suspend fun onUiAction(uiAction: UiAction) {
        if (uiAction is InfallibleUiAction) {
            when (uiAction) {
                is InfallibleUiAction.LoadPachliAccount -> pachliAccountId.emit(uiAction.pachliAccountId)
                is InfallibleUiAction.TabRemoveTimeline -> onTabRemoveTimeline(uiAction)
            }
        }
    }

    private suspend fun onTabRemoveTimeline(action: InfallibleUiAction.TabRemoveTimeline) {
        accountManager.getAccountById(action.pachliAccountId)
            ?.tabPreferences
            ?.filter { it != action.timeline }
            ?.let { accountManager.setTabPreferences(action.pachliAccountId, it) }
    }
}
