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

package app.pachli.feature.intentrouter

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.support.getSupportWrapper
import app.pachli.core.common.PachliError
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.Loadable
import app.pachli.core.data.repository.RefreshAccountError
import app.pachli.core.data.repository.SetActiveAccountError
import app.pachli.core.database.AppDatabase
import app.pachli.core.database.dao.LogEntryDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.navigation.IntentRouterActivityIntent.Payload
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.mapEither
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

internal sealed interface UiState {
    data object Loading : UiState
}

/** Actions the user can take from the UI. */
internal sealed interface UiAction

internal sealed interface FallibleUiAction : UiAction {
    data class SetActiveAccount(
        val pachliAccountId: Long,
        val payload: Payload.MainActivity,
        val logoutAccount: AccountEntity? = null,
    ) : FallibleUiAction

    data class RefreshAccount(
        val accountEntity: AccountEntity,
        val payload: Payload.MainActivity,
    ) : FallibleUiAction
}

/** Actions that succeeded. */
internal sealed interface UiSuccess {
    val action: FallibleUiAction

    data class SetActiveAccount(
        override val action: FallibleUiAction.SetActiveAccount,
        val accountEntity: AccountEntity,
    ) : UiSuccess

    data class RefreshAccount(override val action: FallibleUiAction.RefreshAccount) : UiSuccess
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
    ) : UiError(R.string.error_set_active_account, action, cause)

    data class RefreshAccount(
        override val action: FallibleUiAction.RefreshAccount,
        override val cause: RefreshAccountError,
    ) : UiError(R.string.error_refresh_account, action, cause)
}

@HiltViewModel
internal class IntentRouterViewModel @Inject constructor(
    private val accountManager: AccountManager,
    private val db: AppDatabase,
    private val timelineDao: TimelineDao,
    private val logEntryDao: LogEntryDao,
) : ViewModel() {
    val accounts = accountManager.pachliAccountsFlow.map { Loadable.Loaded(it) }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        Loadable.Loading,
    )

    private val uiAction = MutableSharedFlow<UiAction>()
    val accept: (UiAction) -> Unit = { action -> viewModelScope.launch { uiAction.emit(action) } }

    private val _uiResult = Channel<Result<UiSuccess, UiError>>()
    val uiResult = _uiResult.receiveAsFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UiState.Loading,
    )

    init {
        viewModelScope.launch { uiAction.collect { launch { onUiAction(it) } } }
    }

    /** Processes actions received from the UI and updates [_uiState]. */
    private suspend fun onUiAction(uiAction: UiAction) {
        val result = when (uiAction) {
            is FallibleUiAction.RefreshAccount -> onRefreshAccount(uiAction)
            is FallibleUiAction.SetActiveAccount -> onSetActiveAccount(uiAction)
        }

        _uiResult.send(result)
    }

    private suspend fun onSetActiveAccount(action: FallibleUiAction.SetActiveAccount): Result<UiSuccess.SetActiveAccount, UiError.SetActiveAccount> {
        return accountManager.setActiveAccount(action.pachliAccountId)
            .mapEither(
                { UiSuccess.SetActiveAccount(action, it) },
                { UiError.SetActiveAccount(action, it) },
            )
    }

    private suspend fun onRefreshAccount(action: FallibleUiAction.RefreshAccount): Result<UiSuccess.RefreshAccount, UiError.RefreshAccount> {
        return accountManager.refresh(action.accountEntity.id)
            .mapEither(
                { UiSuccess.RefreshAccount(action) },
                { UiError.RefreshAccount(action, it) },
            )
    }

    /**
     * Prunes the local cache if there are more than 800 rows in TimelineStatusWithAccount.
     *
     * This works around buggy devices that can't reliably run WorkManager tasks. If the
     * cache gets too large the timeline queries take too long and the user experiences
     * crashes, out of memory errors, or other poor performance.
     */
    suspend fun pruneCacheIfNeeded() = withContext(Dispatchers.IO) {
        val countTimelineStatusWithAccount = getTableCount("TimelineStatusWithAccount").getOrElse {
            Timber.e("pruneCacheIfNeeded: getTableCount error: $it")
            return@withContext
        }
        Timber.d("pruneCacheIfNeeded: TimelineStatusWithAccount = $countTimelineStatusWithAccount")
        if (countTimelineStatusWithAccount < 800) {
            Timber.d("pruneCacheIfNeeded: skipping")
            return@withContext
        }

        Timber.d("pruneCacheIfNeeded: pruning")
        val marker = TimeSource.Monotonic.markNow()
        accountManager.accounts.forEach { timelineDao.cleanup(it.id) }
        logEntryDao.prune(Instant.now().minusMillis(48.hours.inWholeMilliseconds))

        Timber.d("pruneCacheIfNeeded: pruned, took ${marker.elapsedNow().inWholeMilliseconds} ms")
    }

    /**
     * @return The number of rows in [tableName], or any error that occurred.
     *
     * Identical to [DatabaseFragmentViewModel.getTableCount]
     */
    private suspend fun getTableCount(tableName: String) = withContext(Dispatchers.IO) {
        return@withContext runSuspendCatching {
            db.getSupportWrapper().query("SELECT COUNT(*) FROM $tableName").use {
                it.moveToFirst()
                it.getInt(0)
            }
        }
    }

    companion object {
        fun canHandleMimeType(mimeType: String?): Boolean {
            return mimeType != null && (mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType.startsWith("audio/") || mimeType == "text/plain")
        }
    }
}

inline fun <V, E> Result<V, E>.on(block: (Result<V, E>) -> Unit): Result<V, E> {
    block(this)
    return this
}
