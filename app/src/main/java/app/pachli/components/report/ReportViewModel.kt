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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import app.pachli.components.report.adapter.StatusesPagingSource
import app.pachli.components.report.model.StatusViewState
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.Loadable
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.eventhub.BlockEvent
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.MuteEvent
import app.pachli.core.network.model.Relationship
import app.pachli.core.network.model.Status
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.util.Error
import app.pachli.util.Loading
import app.pachli.util.Resource
import app.pachli.util.Success
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val accountManager: AccountManager,
    private val mastodonApi: MastodonApi,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
    private val eventHub: EventHub,
) : ViewModel() {

    private val navigationMutable = MutableLiveData<Screen?>()
    val navigation: LiveData<Screen?> = navigationMutable

    private val muteStateMutable = MutableLiveData<Resource<Boolean>>()
    val muteState: LiveData<Resource<Boolean>> = muteStateMutable

    private val blockStateMutable = MutableLiveData<Resource<Boolean>>()
    val blockState: LiveData<Resource<Boolean>> = blockStateMutable

    private val reportingStateMutable = MutableLiveData<Resource<Boolean>>()
    var reportingState: LiveData<Resource<Boolean>> = reportingStateMutable

    private val checkUrlMutable = MutableLiveData<String?>()
    val checkUrl: LiveData<String?> = checkUrlMutable

    private val accountIdFlow = MutableSharedFlow<String>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val statusDisplayOptions = statusDisplayOptionsRepository.flow

    val activeAccountFlow = accountManager.accountsFlow
        .filterIsInstance<Loadable.Loaded<AccountEntity?>>()
        .mapNotNull { it.data }

    val statusesFlow = activeAccountFlow.combine(accountIdFlow) { activeAccount, reportedAccountId ->
        Pair(activeAccount, reportedAccountId)
    }.flatMapLatest { (activeAccount, reportedAccountId) ->
        Pager(
            initialKey = statusId,
            config = PagingConfig(pageSize = 20, initialLoadSize = 20),
            pagingSourceFactory = { StatusesPagingSource(reportedAccountId, mastodonApi) },
        ).flow
            .map { pagingData ->
                /* TODO: refactor reports to use the isShowingContent / isExpanded / isCollapsed attributes from StatusViewData
                 instead of StatusViewState */
                pagingData.map { status -> StatusViewData.from(activeAccount.id, status, false, false, false) }
            }
    }.cachedIn(viewModelScope)

    private val selectedIds = HashSet<String>()
    val statusViewState = StatusViewState()

    var reportNote: String = ""
    var isRemoteNotify = false

    private var statusId: String? = null
    lateinit var accountUserName: String
    lateinit var accountId: String
    var isRemoteAccount: Boolean = false
    var remoteServer: String? = null

    fun init(accountId: String, userName: String, statusId: String?) {
        this.accountId = accountId
        this.accountUserName = userName
        this.statusId = statusId
        statusId?.let {
            selectedIds.add(it)
        }

        isRemoteAccount = userName.contains('@')
        if (isRemoteAccount) {
            remoteServer = userName.substring(userName.indexOf('@') + 1)
        }

        obtainRelationship()

        viewModelScope.launch {
            accountIdFlow.emit(accountId)
        }
    }

    fun navigateTo(screen: Screen) {
        navigationMutable.value = screen
    }

    fun navigated() {
        navigationMutable.value = null
    }

    private fun obtainRelationship() {
        val ids = listOf(accountId)
        muteStateMutable.value = Loading()
        blockStateMutable.value = Loading()
        viewModelScope.launch {
            mastodonApi.relationships(ids)
                .onSuccess { updateRelationship(it.body.firstOrNull()) }
                .onFailure { updateRelationship(null) }
        }
    }

    private fun updateRelationship(relationship: Relationship?) {
        if (relationship != null) {
            muteStateMutable.value = Success(relationship.muting)
            blockStateMutable.value = Success(relationship.blocking)
        } else {
            muteStateMutable.value = Error(false)
            blockStateMutable.value = Error(false)
        }
    }

    fun toggleMute() {
        val alreadyMuted = muteStateMutable.value?.data == true
        viewModelScope.launch {
            if (alreadyMuted) {
                mastodonApi.unmuteAccount(accountId)
            } else {
                mastodonApi.muteAccount(accountId)
            }
                .onSuccess {
                    val relationship = it.body
                    val muting = relationship.muting
                    muteStateMutable.value = Success(muting)
                    if (muting) {
                        eventHub.dispatch(MuteEvent(accountId))
                    }
                }
                .onFailure { muteStateMutable.value = Error(false, it.throwable.message) }
        }

        muteStateMutable.value = Loading()
    }

    fun toggleBlock() {
        val alreadyBlocked = blockStateMutable.value?.data == true
        viewModelScope.launch {
            if (alreadyBlocked) {
                mastodonApi.unblockAccount(accountId)
            } else {
                mastodonApi.blockAccount(accountId)
            }
                .onSuccess {
                    val relationship = it.body
                    val blocking = relationship.blocking
                    blockStateMutable.value = Success(blocking)
                    if (blocking) {
                        eventHub.dispatch(BlockEvent(accountId))
                    }
                }
                .onFailure {
                    blockStateMutable.value = Error(false, it.throwable.message)
                }
        }
        blockStateMutable.value = Loading()
    }

    fun doReport() {
        reportingStateMutable.value = Loading()
        viewModelScope.launch {
            mastodonApi.report(accountId, selectedIds.toList(), reportNote, if (isRemoteAccount) isRemoteNotify else null)
                .onSuccess { reportingStateMutable.value = Success(true) }
                .onFailure { error -> reportingStateMutable.value = Error(cause = error.throwable) }
        }
    }

    fun checkClickedUrl(url: String?) {
        checkUrlMutable.value = url
    }

    fun urlChecked() {
        checkUrlMutable.value = null
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
}
