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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import app.pachli.components.report.adapter.StatusesPagingSource
import app.pachli.components.report.model.StatusViewState
import app.pachli.core.common.PachliError
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.data.repository.Loadable
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.data.repository.get
import app.pachli.core.eventhub.BlockEvent
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.MuteEvent
import app.pachli.core.model.Relationship
import app.pachli.core.model.Status
import app.pachli.core.network.retrofit.MastodonApi
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

@HiltViewModel(assistedFactory = ReportViewModel.Factory::class)
class ReportViewModel @AssistedInject constructor(
    @Assisted private val pachliAccountId: Long,
    @Assisted("reportedAccountId") private val reportedAccountId: String,
    @Assisted("reportedAccountUsername") val reportedAccountUsername: String,
    @Assisted("reportedStatusId") private val reportedStatusId: String?,
    private val mastodonApi: MastodonApi,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
    private val eventHub: EventHub,
) : ViewModel() {

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

    val statusDisplayOptions = statusDisplayOptionsRepository.flow

    val statusesFlow = Pager(
        initialKey = this.reportedStatusId,
        config = PagingConfig(pageSize = 20, initialLoadSize = 20),
        pagingSourceFactory = { StatusesPagingSource(reportedAccountId, mastodonApi) },
    ).flow
        .map { pagingData ->
                /* TODO: refactor reports to use the isShowingContent / isExpanded / isCollapsed attributes from StatusViewData
                 instead of StatusViewState */
            pagingData.map { status -> StatusViewData.from(pachliAccountId, status.asModel(), false, false, false) }
        }
        .cachedIn(viewModelScope)

    /** IDs of statuses the user is reporting. */
    private val selectedIds = HashSet<String>().apply { reportedStatusId?.let { add(it) } }
    val statusViewState = StatusViewState()

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

        viewModelScope.launch {
            if (alreadyMuted) {
                mastodonApi.unmuteAccount(this@ReportViewModel.reportedAccountId)
            } else {
                mastodonApi.muteAccount(this@ReportViewModel.reportedAccountId)
            }
                .map { it.body.muting }
                .onSuccess {
                    _muting.emit(Ok(Loadable.Loaded(it)))
                    if (it) {
                        eventHub.dispatch(MuteEvent(pachliAccountId, this@ReportViewModel.reportedAccountId))
                    }
                }
                .onFailure { _muting.emit(Err(it)) }
        }
    }

    fun toggleBlock() {
        val alreadyBlocked = blocking.value.get()?.get() == true

        viewModelScope.launch {
            if (alreadyBlocked) {
                mastodonApi.unblockAccount(this@ReportViewModel.reportedAccountId)
            } else {
                mastodonApi.blockAccount(this@ReportViewModel.reportedAccountId)
            }
                .map { it.body.blocking }
                .onSuccess {
                    _blocking.emit(Ok(Loadable.Loaded(it)))
                    if (it) {
                        eventHub.dispatch(BlockEvent(pachliAccountId, this@ReportViewModel.reportedAccountId))
                    }
                }
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
            selectedIds.add(status.id)
        } else {
            selectedIds.remove(status.id)
        }
    }

    fun isStatusChecked(id: String): Boolean {
        return selectedIds.contains(id)
    }

    @AssistedFactory
    interface Factory {
        /** Creates [ReportViewModel] with [pachliAccountId] as the active account. */
        fun create(
            pachliAccountId: Long,
            @Assisted("reportedAccountId") reportedAccountId: String,
            @Assisted("reportedAccountUsername") reportedAccountUsername: String,
            @Assisted("reportedStatusId") reportedStatusId: String?,
        ): ReportViewModel
    }
}
