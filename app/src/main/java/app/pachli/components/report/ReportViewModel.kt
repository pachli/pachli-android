/* Copyright 2019 Joel Pyska
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

package app.pachli.components.report

import androidx.lifecycle.viewModelScope
import app.pachli.components.timeline.NetworkTimelineRepository
import app.pachli.components.timeline.viewmodel.NetworkTimelineViewModel
import app.pachli.core.common.PachliError
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.Loadable
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.data.repository.get
import app.pachli.core.eventhub.EventHub
import app.pachli.core.model.Relationship
import app.pachli.core.model.Status
import app.pachli.core.model.Timeline
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.usecase.TimelineCases
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AccountType {
    /** Account is on the same server as the user. */
    data object Local : AccountType

    /**
     * Account is on a different server to the user.
     *
     * @param server The name of the user's server (everything after the
     * '@' in their username).
     */
    data class Remote(val server: String) : AccountType
}

/**
 * [ReportViewModel] is a [NetworkTimelineViewModel] fixed to the
 * [Timeline.User.Replies] timeline.
 */
@HiltViewModel(assistedFactory = ReportViewModel.Factory::class)
class ReportViewModel @AssistedInject constructor(
    @Assisted("reportedAccountId") private val reportedAccountId: String,
    @Assisted("reportedAccountUsername") val reportedAccountUsername: String,
    @Assisted("reportedStatusId") private val reportedStatusId: String?,
    private val mastodonApi: MastodonApi,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
    eventHub: EventHub,
    repository: NetworkTimelineRepository,
    timelineCases: TimelineCases,
    accountManager: AccountManager,
    sharedPreferencesRepository: SharedPreferencesRepository,
) : NetworkTimelineViewModel(
    timeline = Timeline.User.Replies(reportedAccountId, excludeReblogs = true),
    repository = repository,
    timelineCases = timelineCases,
    eventHub = eventHub,
    accountManager = accountManager,
    statusDisplayOptionsRepository = statusDisplayOptionsRepository,
    sharedPreferencesRepository = sharedPreferencesRepository,
) {
    override val initialRefreshStatusId = flowOf(reportedStatusId)

    private val _navigation = MutableStateFlow(Screen.Statuses)

    /** The [Screen] to show to the user. */
    val navigation: StateFlow<Screen> = _navigation.asStateFlow()

    /** Emit in to this flow to reload the relationship data. */
    private val reloadRelationship = MutableSharedFlow<Unit>(replay = 1).apply {
        tryEmit(Unit)
    }

    /** Flow of the relationship between the user's account and the reported account. */
    private val relationship = reloadRelationship.flatMapLatest {
        flow {
            emit(Ok(Loadable.Loading))
            emit(
                mastodonApi.relationships(listOf(reportedAccountId)).map { it.body.first().asModel() }
                    .map { Loadable.Loaded(it) },
            )
        }
    }.shareIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        replay = 1,
    )

    private val _muting = MutableSharedFlow<Result<Loadable<Boolean>, PachliError>>()

    /**
     * Flow of the most recent mute/unmute relationship state.
     *
     * Initially determined by the data in [relationship], is updated when either
     * [relationship] or [_muting] emit a new value.
     */
    val muting = merge(
        relationship.map { relationship ->
            when (relationship) {
                is Err<PachliError> -> relationship
                is Ok<Loadable<Relationship>> -> relationship.map {
                    when (it) {
                        is Loadable.Loaded<Relationship> -> Loadable.Loaded(it.data.muting)
                        Loadable.Loading -> Loadable.Loading
                    }
                }
            }
        },
        _muting,
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        Ok(Loadable.Loading),
    )

    private val _blocking = MutableSharedFlow<Result<Loadable<Boolean>, PachliError>>()

    /**
     * Flow of the most recent blocked/unblocked relationship state.
     *
     * Initially determined by the data in [relationship], is updated when either
     * [relationship] or [_blocking] emit a new value.
     */
    val blocking = merge(
        relationship.map { relationship ->
            when (relationship) {
                is Err<PachliError> -> relationship
                is Ok<Loadable<Relationship>> -> relationship.map {
                    when (it) {
                        is Loadable.Loaded<Relationship> -> Loadable.Loaded(it.data.blocking)
                        Loadable.Loading -> Loadable.Loading
                    }
                }
            }
        },
        _blocking,
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        Ok(Loadable.Loading),
    )

    private val _reportingState = MutableSharedFlow<Result<Loadable<Unit>, PachliError>>()
    var reportingState: SharedFlow<Result<Loadable<Unit>, PachliError>> = _reportingState

    private val _checkUrl = MutableStateFlow<String?>(null)
    val checkUrl: StateFlow<String?> = _checkUrl.asStateFlow()

    /** IDs of statuses the user is reporting. */
    private val selectedIds = HashSet<String>().apply { reportedStatusId?.let { add(it) } }

    /** Text of the comment to include with the report. */
    var reportNote: String = ""

    /** True if the report should be forwarded to the remote server.*/
    var isRemoteNotify = false

    val reportedAccountType = if (reportedAccountUsername.contains('@')) {
        AccountType.Remote(reportedAccountUsername.substring(reportedAccountUsername.indexOf('@') + 1))
    } else {
        AccountType.Local
    }

    fun navigateTo(screen: Screen) {
        _navigation.value = screen
    }

    fun navigateBack() {
        _navigation.update { current ->
            return@update when (current) {
                Screen.Statuses -> Screen.Finish
                Screen.Note -> Screen.Statuses
                Screen.Done -> Screen.Done
                Screen.Finish -> Screen.Done
            }
        }
    }

    fun reloadRelationship() {
        viewModelScope.launch { reloadRelationship.emit(Unit) }
    }

    fun toggleMute() {
        val alreadyMuted = muting.value.get()?.get() == true

        val pachliAccountId = pachliAccountId.replayCache.lastOrNull() ?: return

        viewModelScope.launch {
            if (alreadyMuted) {
                timelineCases.unmuteAccount(pachliAccountId, this@ReportViewModel.reportedAccountId)
            } else {
                timelineCases.muteAccount(pachliAccountId, this@ReportViewModel.reportedAccountId)
            }
                .map { it.body.muting }
                .onSuccess { _muting.emit(Ok(Loadable.Loaded(it))) }
                .onFailure { _muting.emit(Err(it)) }
        }
    }

    fun toggleBlock() {
        val alreadyBlocked = blocking.value.get()?.get() == true

        val pachliAccountId = pachliAccountId.replayCache.lastOrNull() ?: return

        viewModelScope.launch {
            if (alreadyBlocked) {
                timelineCases.unblockAccount(pachliAccountId, this@ReportViewModel.reportedAccountId)
            } else {
                timelineCases.blockAccount(pachliAccountId, this@ReportViewModel.reportedAccountId)
            }
                .map { it.body.blocking }
                .onSuccess { _blocking.emit(Ok(Loadable.Loaded(it))) }
                .onFailure { _blocking.emit(Err(it)) }
        }
    }

    fun doReport() {
        viewModelScope.launch {
            _reportingState.emit(Ok(Loadable.Loading))
            mastodonApi.report(
                this@ReportViewModel.reportedAccountId,
                selectedIds.toList(),
                reportNote,
                if (reportedAccountType is AccountType.Remote) isRemoteNotify else null,
            )
                .onSuccess { _reportingState.emit(Ok(Loadable.Loaded(Unit))) }
                .onFailure { _reportingState.emit(Err(it)) }
        }
    }

    fun checkClickedUrl(url: String?) {
        _checkUrl.value = url
    }

    fun urlChecked() {
        _checkUrl.value = null
    }

    fun setStatusChecked(status: Status, checked: Boolean) {
        if (checked) {
            selectedIds.add(status.statusId)
        } else {
            selectedIds.remove(status.statusId)
        }
    }

    fun isStatusChecked(id: String): Boolean {
        return selectedIds.contains(id)
    }

    @AssistedFactory
    interface Factory {
        /**
         * Creates [ReportViewModel].
         *
         * @param reportedAccountId Server ID of the account to report.
         * @param reportedAccountUsername Username of the account to report.
         * @param reportedStatusId Server ID of the status to report.
         */
        fun create(
            @Assisted("reportedAccountId") reportedAccountId: String,
            @Assisted("reportedAccountUsername") reportedAccountUsername: String,
            @Assisted("reportedStatusId") reportedStatusId: String?,
        ): ReportViewModel
    }
}
