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

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.common.PachliError
import app.pachli.core.data.model.Server
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.RefreshAccountError
import app.pachli.core.data.repository.SetActiveAccountError
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.model.ServerOperation
import app.pachli.core.model.Timeline
import app.pachli.core.preferences.MainNavigationPosition
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.preferences.ShowSelfUsername
import app.pachli.core.preferences.TabAlignment
import app.pachli.core.preferences.TabContents
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.z4kn4fein.semver.constraints.toConstraint
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Actions the user can take from the UI. */
internal sealed interface UiAction

internal sealed interface FallibleUiAction : UiAction {
    data class SetActiveAccount(val pachliAccountId: Long) : FallibleUiAction
    data class RefreshAccount(val accountEntity: AccountEntity) : FallibleUiAction
}

internal sealed interface InfallibleUiAction : UiAction {
    /** Remove [timeline] from the active account's tabs. */
    data class TabRemoveTimeline(val timeline: Timeline) : InfallibleUiAction
}

/** Actions that succeeded. */
internal sealed interface UiSuccess {
    val action: FallibleUiAction

    data class SetActiveAccount(
        override val action: FallibleUiAction.SetActiveAccount,
        val accountEntity: AccountEntity,
    ) : UiSuccess

    data class RefreshAccount(
        override val action: FallibleUiAction.RefreshAccount,
    ) : UiSuccess
}

/** Actions that failed. */
internal sealed class UiError(
    @StringRes override val resourceId: Int,
    open val action: UiAction,
    override val cause: PachliError,
    override val formatArgs: Array<out String>? = null,
) : PachliError {
    data class SetActiveAccount(
        override val action: FallibleUiAction.SetActiveAccount,
        override val cause: SetActiveAccountError,
    ) : UiError(R.string.main_viewmodel_error_set_active_account, action, cause)

    data class RefreshAccount(
        override val action: FallibleUiAction.RefreshAccount,
        override val cause: RefreshAccountError,
    ) : UiError(R.string.main_viewmodel_error_refresh_account, action, cause)
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

@HiltViewModel()
internal class MainViewModel @Inject constructor(
    private val accountManager: AccountManager,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
) : ViewModel() {
    /**
     * Flow of Pachli Account IDs, the most recent entry in the flow is the active account.
     *
     * Initially null, the activity sets this by sending [FallibleUiAction.SetActiveAccount].
     */
    private val pachliAccountIdFlow = MutableStateFlow<Long?>(null)

    val pachliAccountFlow = pachliAccountIdFlow.filterNotNull().flatMapLatest { accountId ->
        accountManager.getPachliAccountFlow(accountId)
            .filterNotNull()
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    private val uiAction = MutableSharedFlow<UiAction>()

    val accept: (UiAction) -> Unit = { action -> viewModelScope.launch { uiAction.emit(action) } }

    private val _uiResult = Channel<Result<UiSuccess, UiError>>()
    val uiResult = _uiResult.receiveAsFlow()

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
                is InfallibleUiAction.TabRemoveTimeline -> onTabRemoveTimeline(uiAction.timeline)
            }
        }

        if (uiAction is FallibleUiAction) {
            val result = when (uiAction) {
                is FallibleUiAction.SetActiveAccount -> onSetActiveAccount(uiAction)
                is FallibleUiAction.RefreshAccount -> onRefreshAccount(uiAction)
            }
            _uiResult.send(result)
        }
    }

    private suspend fun onSetActiveAccount(action: FallibleUiAction.SetActiveAccount): Result<UiSuccess.SetActiveAccount, UiError.SetActiveAccount> {
        return accountManager.setActiveAccount(action.pachliAccountId)
            .mapEither(
                { UiSuccess.SetActiveAccount(action, it) },
                { UiError.SetActiveAccount(action, it) },
            )
            .onSuccess {
                pachliAccountIdFlow.value = it.accountEntity.id
                uiAction.emit(FallibleUiAction.RefreshAccount(it.accountEntity))
            }
    }

    private suspend fun onRefreshAccount(action: FallibleUiAction.RefreshAccount): Result<UiSuccess.RefreshAccount, UiError.RefreshAccount> {
        return accountManager.refresh(action.accountEntity)
            .mapEither(
                { UiSuccess.RefreshAccount(action) },
                { UiError.RefreshAccount(action, it) },
            )
    }

    private suspend fun onTabRemoveTimeline(timeline: Timeline) {
        val active = pachliAccountFlow.replayCache.last().entity
        val tabPreferences = active.tabPreferences.filterNot { it == timeline }
        accountManager.setTabPreferences(active.id, tabPreferences)
    }
}
